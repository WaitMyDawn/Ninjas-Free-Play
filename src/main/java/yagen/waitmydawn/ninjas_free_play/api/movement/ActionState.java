package yagen.waitmydawn.ninjas_free_play.api.movement;

/**
 * Immutable observation. Tick values use the observed player's logical-side tick counter.
 */
public record ActionState(MovementAction action, ActionPhase phase, long instanceId,
                          long startedTick, long endedTick, long elapsedTicks, ActionEndReason endReason) {
    public boolean isActive() {
        return phase == ActionPhase.START || phase == ActionPhase.ACTIVE;
    }

    public boolean startedThisTick() {
        return phase == ActionPhase.START;
    }

    public boolean endedThisTick() {
        return phase == ActionPhase.END;
    }
}
