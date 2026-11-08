package yagen.waitmydawn.ninjas_free_play.api.movement;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;

/**
 * Non-cancellable notification on MinecraftForge.EVENT_BUS, emitted only on the logical server after acceptance.
 */
public final class MovementActionEvent extends PlayerEvent {
    private final ActionState state;

    public MovementActionEvent(Player player, ActionState state) {
        super(player);
        this.state = state;
    }

    public ActionState getState() {
        return state;
    }

    public MovementAction getAction() {
        return state.action();
    }

    public ActionPhase getActionPhase() {
        return state.phase();
    }
}
