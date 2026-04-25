package dev.denismasterherobrine.finale.arenaruntime.supervisor;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.denismasterherobrine.finale.arenaruntime.ArenaRuntimePlugin;
import dev.denismasterherobrine.finale.arenaruntime.event.SupervisorActionAppliedEvent;
import dev.denismasterherobrine.finale.arenaruntime.game.ArenaState;
import dev.denismasterherobrine.finale.arenaruntime.game.session.ArenaSession;
import dev.denismasterherobrine.finale.arenaruntime.game.session.SessionRegistry;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
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
        Map<String, String> parameters = parseParameters(body);

        if (actionType.isEmpty()) {
            writeJson(ex, 400, false, "missing action_type", null);
            return;
        }
        if (target.isEmpty()) {
            writeJson(ex, 400, false, "missing target (arena_id)", null);
            return;
        }

        plugin.getLogger().info(() -> "[SupervisorRelay] recv command_id=" + commandId + " action=" + actionType
                + " target=" + target + " params=" + parameters);

        AtomicReference<RelayResult> result = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
            try {
                RelayResult r = executeOnMainThread(actionType, target, parameters);
                result.set(r);
                if (r.implemented) {
                    try {
                        Bukkit.getPluginManager().callEvent(new SupervisorActionAppliedEvent(
                                commandId, actionType, target, r.success, r.message, parameters));
                    } catch (Exception eventEx) {
                        plugin.getLogger().log(Level.WARNING,
                                "[SupervisorRelay] failed to dispatch SupervisorActionAppliedEvent", eventEx);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "[SupervisorRelay] action failed command_id=" + commandId, e);
                result.set(RelayResult.err("exception: " + e.getMessage()));
            } finally {
                latch.countDown();
            }
        });

        try {
            if (!latch.await(45, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("[SupervisorRelay] main thread timeout command_id=" + commandId);
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

        if (r.success) {
            plugin.getLogger().info("[SupervisorRelay] ok command_id=" + commandId + " action=" + actionType
                    + " message=" + r.message);
        } else if (r.implemented) {
            plugin.getLogger().warning("[SupervisorRelay] failed command_id=" + commandId + " action=" + actionType
                    + " message=" + r.message);
        } else {
            plugin.getLogger().info("[SupervisorRelay] not implemented command_id=" + commandId + " action=" + actionType
                    + " message=" + r.message);
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

    private RelayResult executeOnMainThread(String actionType, String arenaId, Map<String, String> parameters) {
        return switch (actionType) {
            case "RESTART_ARENA" -> restartArena(arenaId);
            case "SOFT_FAIL_ARENA" -> softFailArena(arenaId);
            case "REDUCE_MOB_COUNT" -> reduceMobCount(arenaId, parameters);
            case "RESTORE_MOB_COUNT" -> restoreMobCount(arenaId);
            case "INCREASE_WAVE_INTERVAL" -> increaseWaveInterval(arenaId, parameters);
            case "DECREASE_WAVE_INTERVAL" -> decreaseWaveInterval(arenaId, parameters);
            case "CAP_DIFFICULTY" -> capDifficulty(arenaId, parameters);
            case "RESTORE_DIFFICULTY" -> restoreDifficulty(arenaId);
            case "SIMPLIFY_AI" -> simplifyAi(arenaId);
            case "RESTORE_AI" -> restoreAi(arenaId);
            case "RESTORE_ARENA" -> restoreArena(arenaId);
            case "PAUSE_NEW_SESSIONS", "RESUME_SESSIONS" -> RelayResult.notImplemented(
                    "Velocity/matchmaker integration deferred — not available on Paper relay alone");
            default -> RelayResult.notImplemented("action not implemented on relay: " + actionType);
        };
    }

    private RelayResult restartArena(String arenaId) {
        var opt = sessions.getSession(arenaId);
        if (opt.isEmpty()) {
            // No active session — restart degenerates to RESTORE_ARENA semantics (reset world).
            var api = ArenaRuntimePlugin.getWorldApi();
            if (api == null) {
                return RelayResult.err("RESTART_ARENA: no session and ArenaWorldAPI unavailable for arena_id=" + arenaId);
            }
            api.resetArena(arenaId).whenComplete((v, ex) -> {
                if (ex != null) {
                    plugin.getLogger().warning("[SupervisorRelay] RESTART_ARENA resetArena (no session) failed arena_id="
                            + arenaId + ": " + ex.getMessage());
                } else {
                    plugin.getLogger().info("[SupervisorRelay] RESTART_ARENA resetArena (no session) completed arena_id=" + arenaId);
                }
            });
            return RelayResult.ok("RESTART_ARENA: no active session, async world reset started for arena_id=" + arenaId);
        }
        opt.get().finishMatchSupervisorRestart();
        return RelayResult.ok("RESTART_ARENA: session finished (SUPERVISOR_RESTART), world reset chained for arena_id=" + arenaId);
    }

    private RelayResult softFailArena(String arenaId) {
        var opt = sessions.getSession(arenaId);
        if (opt.isEmpty()) {
            return RelayResult.err("no active session for arena_id=" + arenaId);
        }
        opt.get().finishMatchSupervisorSoftFail();
        return RelayResult.ok("SOFT_FAIL_ARENA: soft fail applied for arena_id=" + arenaId);
    }

    private RelayResult reduceMobCount(String arenaId, Map<String, String> parameters) {
        return withRunningSession(arenaId, session -> {
            int delta = parsePositiveInt(parameters.get("delta"), 1);
            session.getRuntimeOverrides().addMobsFlatSubtract(delta);
            return RelayResult.ok("REDUCE_MOB_COUNT: subtract_flat+=" + delta + " arena_id=" + arenaId);
        });
    }

    private RelayResult restoreMobCount(String arenaId) {
        return withRunningSession(arenaId, session -> {
            session.getRuntimeOverrides().resetMobTuning();
            return RelayResult.ok("RESTORE_MOB_COUNT: mob tuning reset arena_id=" + arenaId);
        });
    }

    private RelayResult increaseWaveInterval(String arenaId, Map<String, String> parameters) {
        return withRunningSession(arenaId, session -> {
            int seconds = parsePositiveInt(parameters.get("seconds"), 5);
            session.getRuntimeOverrides().addWaveDelayExtraSeconds(seconds);
            return RelayResult.ok("INCREASE_WAVE_INTERVAL: extra_delay+=" + seconds + "s arena_id=" + arenaId);
        });
    }

    private RelayResult decreaseWaveInterval(String arenaId, Map<String, String> parameters) {
        return withRunningSession(arenaId, session -> {
            int seconds = parsePositiveInt(parameters.get("seconds"), 5);
            session.getRuntimeOverrides().subtractWaveDelayExtraSeconds(seconds);
            return RelayResult.ok("DECREASE_WAVE_INTERVAL: extra_delay-=" + seconds + "s arena_id=" + arenaId);
        });
    }

    private RelayResult capDifficulty(String arenaId, Map<String, String> parameters) {
        return withRunningSession(arenaId, session -> {
            int cap = parsePositiveInt(parameters.get("max_mobs"), 8);
            session.getRuntimeOverrides().setMaxMobsPerWaveCap(cap);
            return RelayResult.ok("CAP_DIFFICULTY: max_mobs_per_wave=" + cap + " arena_id=" + arenaId);
        });
    }

    private RelayResult restoreDifficulty(String arenaId) {
        return withRunningSession(arenaId, session -> {
            session.getRuntimeOverrides().clearDifficultyCap();
            return RelayResult.ok("RESTORE_DIFFICULTY: cap cleared arena_id=" + arenaId);
        });
    }

    private RelayResult simplifyAi(String arenaId) {
        return withRunningSession(arenaId, session -> {
            session.getRuntimeOverrides().setSimplifyAi(true);
            return RelayResult.ok("SIMPLIFY_AI: enabled arena_id=" + arenaId);
        });
    }

    private RelayResult restoreAi(String arenaId) {
        return withRunningSession(arenaId, session -> {
            session.getRuntimeOverrides().setSimplifyAi(false);
            return RelayResult.ok("RESTORE_AI: disabled arena_id=" + arenaId);
        });
    }

    private RelayResult restoreArena(String arenaId) {
        var opt = sessions.getSession(arenaId);
        if (opt.isPresent()) {
            opt.get().finishMatch();
            return RelayResult.ok("RESTORE_ARENA: active session finished (soft restore) arena_id=" + arenaId);
        }
        var api = ArenaRuntimePlugin.getWorldApi();
        if (api == null) {
            return RelayResult.err("RESTORE_ARENA: ArenaWorldAPI unavailable");
        }
        api.resetArena(arenaId).whenComplete((v, ex) -> {
            if (ex != null) {
                plugin.getLogger().warning("[SupervisorRelay] RESTORE_ARENA resetArena failed arena_id=" + arenaId + ": " + ex.getMessage());
            } else {
                plugin.getLogger().info("[SupervisorRelay] RESTORE_ARENA resetArena completed arena_id=" + arenaId);
            }
        });
        return RelayResult.ok("RESTORE_ARENA: resetArena started (async) arena_id=" + arenaId);
    }

    private RelayResult withRunningSession(String arenaId, java.util.function.Function<ArenaSession, RelayResult> fn) {
        var opt = sessions.getSession(arenaId);
        if (opt.isEmpty()) {
            return RelayResult.err("no session for arena_id=" + arenaId);
        }
        ArenaSession session = opt.get();
        if (session.getState() != ArenaState.RUNNING) {
            return RelayResult.err("session not RUNNING for arena_id=" + arenaId + " (state=" + session.getState() + ")");
        }
        return fn.apply(session);
    }

    private static int parsePositiveInt(String raw, int defaultValue) {
        if (raw == null || raw.isEmpty()) {
            return defaultValue;
        }
        try {
            int v = Integer.parseInt(raw.trim());
            return v > 0 ? v : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static Map<String, String> parseParameters(JsonObject body) {
        Map<String, String> m = new HashMap<>();
        if (!body.has("parameters") || body.get("parameters").isJsonNull()) {
            return m;
        }
        JsonElement el = body.get("parameters");
        if (!el.isJsonObject()) {
            return m;
        }
        JsonObject p = el.getAsJsonObject();
        for (String key : p.keySet()) {
            JsonElement v = p.get(key);
            if (v != null && v.isJsonPrimitive()) {
                m.put(key, v.getAsString().trim());
            }
        }
        return m;
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
