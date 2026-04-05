package dev.denismasterherobrine.finale.arenaruntime.supervisor;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.denismasterherobrine.finale.arenaruntime.game.session.ArenaSession;
import dev.denismasterherobrine.finale.arenaruntime.game.session.SessionRegistry;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

/**
 * Loopback HTTP relay: arena-bridge (Go) POSTs actions; execution runs on the server main thread.
 */
public final class SupervisorRelayServer {

    private static final Gson GSON = new Gson();

    private final JavaPlugin plugin;
    private final SessionRegistry sessions;
    private final String bindHost;
    private final int port;
    private final String bearerToken;

    private HttpServer httpServer;

    public SupervisorRelayServer(JavaPlugin plugin, SessionRegistry sessions, String bindHost, int port, String bearerToken) {
        this.plugin = plugin;
        this.sessions = sessions;
        this.bindHost = bindHost;
        this.port = port;
        this.bearerToken = bearerToken == null ? "" : bearerToken.trim();
    }

    public void start() throws IOException {
        InetSocketAddress addr = new InetSocketAddress(bindHost, port);
        httpServer = HttpServer.create(addr, 0);
        httpServer.createContext("/v1/execute", this::handleExecute);
        httpServer.setExecutor(r -> {
            try {
                r.run();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "supervisor relay task failed", e);
            }
        });
        httpServer.start();
        plugin.getLogger().info("Supervisor relay listening on http://" + bindHost + ":" + port + "/v1/execute");
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop(1);
            httpServer = null;
        }
    }

    private void handleExecute(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(405, -1);
            return;
        }

        if (!authorize(ex)) {
            byte[] denied = "{\"success\":false,\"message\":\"unauthorized\"}".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            ex.sendResponseHeaders(401, denied.length);
            ex.getResponseBody().write(denied);
            ex.close();
            return;
        }

        JsonObject body;
        try (var reader = new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8)) {
            body = GSON.fromJson(reader, JsonObject.class);
        } catch (Exception e) {
            writeJson(ex, 400, false, "invalid JSON: " + e.getMessage(), null);
            return;
        }
        if (body == null) {
            writeJson(ex, 400, false, "empty body", null);
            return;
        }

        String commandId = jsonString(body, "command_id");
        String actionType = jsonString(body, "action_type");
        String target = jsonString(body, "target");

        if (actionType.isEmpty()) {
            writeJson(ex, 400, false, "missing action_type", null);
            return;
        }
        if (target.isEmpty()) {
            writeJson(ex, 400, false, "missing target (arena_id)", null);
            return;
        }

        AtomicReference<RelayResult> result = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                result.set(executeOnMainThread(actionType, target));
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "supervisor relay action failed", e);
                result.set(RelayResult.err("exception: " + e.getMessage()));
            } finally {
                latch.countDown();
            }
        });

        try {
            if (!latch.await(45, TimeUnit.SECONDS)) {
                writeJson(ex, 504, false, "main thread timeout", null);
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            writeJson(ex, 500, false, "interrupted", null);
            return;
        }

        RelayResult r = result.get();
        if (r == null) {
            writeJson(ex, 500, false, "no result", null);
            return;
        }

        int status = r.implemented ? 200 : 501;
        JsonObject out = new JsonObject();
        out.addProperty("success", r.success);
        out.addProperty("message", r.message == null ? "" : r.message);
        if (commandId != null && !commandId.isEmpty()) {
            out.addProperty("command_id", commandId);
        }
        if (r.output != null && !r.output.isEmpty()) {
            JsonObject m = new JsonObject();
            for (Map.Entry<String, String> e : r.output.entrySet()) {
                m.addProperty(e.getKey(), e.getValue());
            }
            out.add("output", m);
        }

        byte[] bytes = GSON.toJson(out).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private RelayResult executeOnMainThread(String actionType, String arenaId) {
        return switch (actionType) {
            case "RESTART_ARENA" -> restartOrStopSession(arenaId);
            default -> RelayResult.notImplemented("action not implemented on relay: " + actionType);
        };
    }

    private RelayResult restartOrStopSession(String arenaId) {
        var opt = sessions.getSession(arenaId);
        if (opt.isEmpty()) {
            return RelayResult.err("no active session for arena_id=" + arenaId);
        }
        ArenaSession session = opt.get();
        session.finishMatch();
        return RelayResult.ok("session finished for arena_id=" + arenaId);
    }

    private boolean authorize(HttpExchange ex) {
        if (bearerToken.isEmpty()) {
            plugin.getLogger().warning("Supervisor relay bearer-token is empty; refusing requests");
            return false;
        }
        String h = ex.getRequestHeaders().getFirst("Authorization");
        if (h == null) {
            return false;
        }
        String expected = "Bearer " + bearerToken;
        return expected.equals(h.trim());
    }

    private static String jsonString(JsonObject o, String key) {
        if (!o.has(key) || o.get(key).isJsonNull()) {
            return "";
        }
        return o.get(key).getAsString().trim();
    }

    private static void writeJson(HttpExchange ex, int status, boolean success, String message, Map<String, String> output)
            throws IOException {
        JsonObject o = new JsonObject();
        o.addProperty("success", success);
        o.addProperty("message", message == null ? "" : message);
        if (output != null && !output.isEmpty()) {
            JsonObject m = new JsonObject();
            for (Map.Entry<String, String> e : output.entrySet()) {
                m.addProperty(e.getKey(), e.getValue());
            }
            o.add("output", m);
        }
        byte[] bytes = GSON.toJson(o).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private record RelayResult(boolean success, String message, boolean implemented, Map<String, String> output) {
        static RelayResult ok(String msg) {
            return new RelayResult(true, msg, true, null);
        }

        static RelayResult err(String msg) {
            return new RelayResult(false, msg, true, null);
        }

        static RelayResult notImplemented(String msg) {
            return new RelayResult(false, msg, false, null);
        }
    }
}
