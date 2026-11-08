package yagen.waitmydawn.ninjas_free_play.api.movement;

import net.minecraft.world.entity.player.Player;
import yagen.waitmydawn.ninjas_free_play.player.MovementAccess;

import java.util.EnumMap;
import java.util.Map;

import yagen.waitmydawn.ninjas_free_play.core.ParkourSnapshot;
import yagen.waitmydawn.ninjas_free_play.api.attribute.NAttributes;

/**
 * Read-only runtime observations; clients observe prediction, servers observe accepted gameplay actions.
 */
public final class MovementApi {
    private MovementApi() {
    }

    public static ActionState state(Player player, MovementAction action) {
        return ((MovementAccess) player).ninjas$actions().state(action, player.tickCount);
    }

    public static boolean isActive(Player player, MovementAction action) {
        return state(player, action).isActive();
    }

    public static ParkourSnapshot parkour(Player player) {
        return ((MovementAccess) player).ninjas$movement().snapshot(player.tickCount).parkour();
    }

    public static double airTimeRemaining(Player player) {
        return ((MovementAccess) player).ninjas$movement().resources().remainingAssistedSeconds(NAttributes.read(player));
    }

    public static Map<MovementAction, ActionState> states(Player player) {
        Map<MovementAction, ActionState> states = new EnumMap<>(MovementAction.class);
        for (MovementAction action : MovementAction.values()) states.put(action, state(player, action));
        return Map.copyOf(states);
    }
}
