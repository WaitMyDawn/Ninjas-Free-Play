package yagen.waitmydawn.ninjas_free_play.core;

import yagen.waitmydawn.ninjas_free_play.api.movement.*;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks semantic transitions independently of rendering, input retransmission and prediction replay.
 */
public final class MovementActionTracker {
    private record Entry(long token, long id, long start, long end, boolean active, ActionEndReason reason) {
    }

    private final Map<MovementAction, Entry> entries = new EnumMap<>(MovementAction.class);
    private long nextId;
    private long emittedTick = Long.MIN_VALUE;

    public ActionState state(MovementAction action, long tick) {
        Entry entry = entries.get(action);
        if (entry == null) return new ActionState(action, ActionPhase.IDLE, -1, -1, -1, 0, ActionEndReason.NONE);
        ActionPhase phase = entry.active() ? tick == entry.start() ? ActionPhase.START : ActionPhase.ACTIVE
                : tick == entry.end() ? ActionPhase.END : ActionPhase.IDLE;
        return new ActionState(action, phase, entry.id(), entry.start(), entry.end(),
                Math.max(0, (entry.active() ? tick : entry.end()) - entry.start()), entry.reason());
    }

    public List<ActionState> reconcile(MovementSnapshot movement, boolean eligible, long tick) {
        Map<MovementAction, Long> desired = new EnumMap<>(MovementAction.class);
        var posture = movement.posture();
        if (eligible) {
            if (posture.rollRemaining() > 0) desired.put(MovementAction.ROLL, posture.rollSequence());
            else if (movement.parkour().mode() != ParkourMode.NONE && movement.parkour().mode() != ParkourMode.AIM_GLIDE)
                desired.put(switch (movement.parkour().mode()) {
                    case SLIDE -> MovementAction.SLIDE;
                    case FLYING_KICK -> MovementAction.FLYING_KICK;
                    case AIM_GLIDE -> MovementAction.AIM_GLIDE;
                    case WALL_LATCH -> MovementAction.WALL_LATCH;
                    case NONE -> throw new IllegalStateException();
                }, movement.parkour().instance());
            else {
                if (posture.crouching() || posture.blockedLow()) desired.put(MovementAction.CROUCH, 0L);
                MovementAction jump = switch (movement.action()) {
                    case GROUND -> MovementAction.GROUND_JUMP;
                    case AIR -> MovementAction.AIR_JUMP;
                    case BULLET -> MovementAction.BULLET_JUMP;
                    case WALL -> MovementAction.WALL_DASH;
                    case NONE -> null;
                };
                // A roll supersedes the previous jump action even if its resource snapshot still retains that jump.
                Entry previous = jump == null ? null : entries.get(jump);
                if (jump != null && movement.actionSequence() > posture.rollSequence()
                        && !(previous != null && !previous.active() && previous.token() == movement.actionSequence()))
                    desired.put(jump, movement.actionSequence());
            }
            if (movement.parkour().glide().active())
                desired.put(MovementAction.AIM_GLIDE, movement.parkour().glide().instance());
        }
        List<ActionState> transitions = new ArrayList<>();
        for (MovementAction action : MovementAction.values()) {
            Entry entry = entries.get(action);
            Long token = desired.get(action);
            if (entry != null && entry.active() && (token == null || token != entry.token())) {
                ActionEndReason reason;
                if (!eligible) reason = ActionEndReason.INELIGIBLE;
                else if (action == MovementAction.ROLL) reason = posture.rollAge() >= posture.rollDuration()
                        ? ActionEndReason.COMPLETED : desired.isEmpty() ? ActionEndReason.INTERRUPTED : ActionEndReason.REPLACED;
                else if (action == MovementAction.CROUCH)
                    reason = desired.isEmpty() ? ActionEndReason.RELEASED : ActionEndReason.REPLACED;
                else if (action == MovementAction.AIM_GLIDE) {
                    reason = movement.grounded() ? ActionEndReason.LANDED : !movement.parkour().aim() ? ActionEndReason.RELEASED
                            : token != null || movement.parkour().mode() == ParkourMode.WALL_LATCH ? ActionEndReason.REPLACED : ActionEndReason.COMPLETED;
                } else if (action == MovementAction.WALL_LATCH || action == MovementAction.SLIDE || action == MovementAction.FLYING_KICK) {
                    reason = movement.grounded() && (action == MovementAction.AIM_GLIDE || action == MovementAction.WALL_LATCH || action == MovementAction.FLYING_KICK)
                            ? ActionEndReason.LANDED : !desired.isEmpty() ? ActionEndReason.REPLACED
                            : (action == MovementAction.AIM_GLIDE || action == MovementAction.WALL_LATCH ? movement.parkour().aim() : movement.parkour().crouch())
                            ? ActionEndReason.COMPLETED : ActionEndReason.RELEASED;
                } else reason = movement.grounded() ? ActionEndReason.LANDED
                        : token != null || !desired.isEmpty() ? ActionEndReason.REPLACED : ActionEndReason.INTERRUPTED;
                finish(action, tick, reason, transitions);
            }
        }
        for (MovementAction action : MovementAction.values()) {
            Long token = desired.get(action);
            Entry entry = entries.get(action);
            if (token != null && (entry == null || !entry.active())) {
                entries.put(action, new Entry(token, nextId++, tick, -1, true, ActionEndReason.NONE));
                transitions.add(state(action, tick));
            }
        }
        return List.copyOf(transitions);
    }

    /**
     * Produces at most one ACTIVE observation per action and simulated tick, after all pending transitions.
     */
    public List<ActionState> tick(long tick) {
        if (tick == emittedTick) return List.of();
        emittedTick = tick;
        List<ActionState> states = new ArrayList<>();
        for (MovementAction action : MovementAction.values()) {
            ActionState state = state(action, tick);
            if (state.phase() == ActionPhase.ACTIVE) states.add(state);
        }
        return List.copyOf(states);
    }

    public List<ActionState> stopAll(long tick, ActionEndReason reason) {
        if (reason == ActionEndReason.NONE) throw new IllegalArgumentException("An ending requires a reason");
        List<ActionState> transitions = new ArrayList<>();
        for (MovementAction action : MovementAction.values()) finish(action, tick, reason, transitions);
        return List.copyOf(transitions);
    }

    private void finish(MovementAction action, long tick, ActionEndReason reason, List<ActionState> transitions) {
        Entry entry = entries.get(action);
        if (entry == null || !entry.active()) return;
        entries.put(action, new Entry(entry.token(), entry.id(), entry.start(), tick, false, reason));
        transitions.add(state(action, tick));
    }
}
