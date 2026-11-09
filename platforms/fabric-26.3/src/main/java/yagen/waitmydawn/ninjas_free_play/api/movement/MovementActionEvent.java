package yagen.waitmydawn.ninjas_free_play.api.movement;

import net.minecraft.world.entity.player.Player;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/**
 * Non-cancellable notification emitted on the logical server after acceptance.
 */
public final class MovementActionEvent {
    @FunctionalInterface
    public interface Listener {
        void onAction(MovementActionEvent event);
    }

    public static final Event<Listener> EVENT = EventFactory.createArrayBacked(Listener.class,
            listeners -> event -> {
                for (Listener listener : listeners) listener.onAction(event);
            });

    private final Player player;
    private final ActionState state;

    public MovementActionEvent(Player player, ActionState state) {
        this.player = player;
        this.state = state;
    }

    public Player getEntity() { return player; }

    public ActionState getState() {
        return state;
    }

    public MovementAction getAction() {
        return state.action();
    }

    public ActionPhase getPhase() {
        return state.phase();
    }

    public ActionPhase getActionPhase() {
        return state.phase();
    }
}
