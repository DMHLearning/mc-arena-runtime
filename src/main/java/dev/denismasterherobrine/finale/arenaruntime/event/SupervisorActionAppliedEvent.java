package dev.denismasterherobrine.finale.arenaruntime.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Fired after the SupervisorRelayServer successfully applies an action coming from the LLM
 * pipeline (Cortex → arena-bridge → relay). The chaos engine listens for this to short-circuit
 * its safety TTL when the LLM remediation arrives in time.
 *
 * <p>{@code success=true} means the action handler executed without an error and made (or attempted)
 * a real state change. {@code success=false} carries the failure message for diagnostics — the
 * event is still fired so observers can record outcomes.
 */
public class SupervisorActionAppliedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String commandId;
    private final String actionType;
    private final String target;
    private final boolean success;
    private final String message;
    private final Map<String, String> parameters;

    public SupervisorActionAppliedEvent(String commandId,
                                        String actionType,
                                        String target,
                                        boolean success,
                                        String message,
                                        Map<String, String> parameters) {
        this.commandId = commandId == null ? "" : commandId;
        this.actionType = actionType == null ? "" : actionType;
        this.target = target == null ? "" : target;
        this.success = success;
        this.message = message == null ? "" : message;
        this.parameters = parameters == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new HashMap<>(parameters));
    }

    public String getCommandId() {
        return commandId;
    }

    public String getActionType() {
        return actionType;
    }

    public String getTarget() {
        return target;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
