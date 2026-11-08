package yagen.waitmydawn.ninjas_free_play.player;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import yagen.waitmydawn.ninjas_free_play.api.movement.*;

import java.util.List;

public final class MovementActionHooks {
    private MovementActionHooks() {
    }

    public static void reconcile(Player player) {
        var tracker = ((MovementAccess) player).ninjas$actions();
        publish(player, tracker.reconcile(MovementPhysics.controller(player).snapshot(player.tickCount),
                MovementPhysics.eligible(player), player.tickCount));
    }

    public static void tick(Player player) {
        reconcile(player);
        publish(player, ((MovementAccess) player).ninjas$actions().tick(player.tickCount));
    }

    public static void stopAll(Player player, ActionEndReason reason) {
        publish(player, ((MovementAccess) player).ninjas$actions().stopAll(player.tickCount, reason));
    }

    private static void publish(Player player, List<ActionState> states) {
        if (!(player instanceof ServerPlayer)) return;
        for (ActionState state : states) NeoForge.EVENT_BUS.post(new MovementActionEvent(player, state));
    }
}
