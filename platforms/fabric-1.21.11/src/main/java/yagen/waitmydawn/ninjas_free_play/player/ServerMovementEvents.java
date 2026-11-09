package yagen.waitmydawn.ninjas_free_play.player;

import yagen.waitmydawn.ninjas_free_play.network.NetworkHandler;
import net.minecraft.server.level.ServerPlayer;
import yagen.waitmydawn.ninjas_free_play.api.movement.ActionEndReason;
import yagen.waitmydawn.ninjas_free_play.core.ParkourMode;
import yagen.waitmydawn.ninjas_free_play.api.attribute.NAttributes;
import yagen.waitmydawn.ninjas_free_play.core.MovementSnapshot;
import yagen.waitmydawn.ninjas_free_play.core.JumpKind;
import yagen.waitmydawn.ninjas_free_play.network.MovementInputPayload;
import yagen.waitmydawn.ninjas_free_play.network.MovementStatePayload;

import java.util.Map;
import java.util.WeakHashMap;

public final class ServerMovementEvents {
    private static final Map<ServerPlayer, MovementSnapshot> PUBLISHED = new WeakHashMap<>();

    private ServerMovementEvents() {
    }

    public static void receive(MovementInputPayload payload, ServerPlayer player) {
        var controller = MovementPhysics.controller(player);
        var values = NAttributes.read(player);
        var frame = ParkourPhysics.frame(player, payload.input().aim() || payload.input().jump());
        var decision = controller.accept(payload.input(), MovementPhysics.eligible(player), MovementPhysics.grounded(player), MovementPhysics.canStand(player), MovementPhysics.canRoll(player, payload.input()),
                player.tickCount, values, frame);
        if (!decision.processed()) return;
        MovementPhysics.applyCrouch(player);
        MovementPhysics.applyJump(player, decision.jump(), payload.input(), values);
        if (decision.rollStarted()) MovementPhysics.applyRoll(player);
        if (decision.slideStarted()) ParkourPhysics.applySlide(player, frame, values);
        MovementActionHooks.reconcile(player);
        boolean hardCorrection = decision.slideStarted() != payload.predictedSlide()
                || Math.abs(payload.predictedSlideMultiplier() - (decision.slideStarted() ? values.get(
                yagen.waitmydawn.ninjas_free_play.core.MovementAttribute.SLIDE_SPEED_MULTIPLIER) : 0)) > 1e-9
                || decision.jump() != payload.predictedJump()
                || Math.abs(payload.predictedPower() - MovementPhysics.customJumpPower(decision.jump(), values)) > 1e-9
                || Math.abs(payload.predictedLift() - (decision.jump() == JumpKind.BULLET ? values.bulletLiftPerTick() : 0)) > 1e-9
                || decision.rollStarted() != payload.predictedRoll()
                || decision.jump() == JumpKind.WALL && (payload.predictedWallX() != controller.snapshot(player.tickCount).parkour().wall().x()
                || payload.predictedWallZ() != controller.snapshot(player.tickCount).parkour().wall().z())
                || Math.abs(payload.predictedRollPower() - (decision.rollStarted() ? values.rollPerTick() : 0)) > 1e-9;
        boolean correct = hardCorrection || decision.slideStarted()
                && yagen.waitmydawn.ninjas_free_play.core.SlidePrediction.needsVelocityCorrection(
                payload.predictedSlideSpeed(), player.getDeltaMovement().horizontalDistance());
        if (hardCorrection) {
            // A configuration/attribute change may invalidate power even when the predicted action kind matches.
            player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        }
        NetworkHandler.sendToPlayer(player, state(player, correct));
        NetworkHandler.sendToPlayersTrackingEntity(player, state(player, false));
        PUBLISHED.put(player, controller.snapshot(player.tickCount));
    }

    public static void tick(ServerPlayer player) {
        var controller = MovementPhysics.controller(player);
        MovementSnapshot before = PUBLISHED.get(player);
        ParkourCombat.tick(player);
        MovementPhysics.observe(player);
        ParkourPhysics.update(player);
        controller.expire(player.tickCount);
        MovementPhysics.applyCrouch(player);
        MovementSnapshot after = controller.snapshot(player.tickCount);
        MovementActionHooks.tick(player);
        if (before == null || before.jumpsUsed() != after.jumpsUsed() || before.bulletUsed() != after.bulletUsed()
                || before.crouchHeld() != after.crouchHeld() || before.jumpHeld() != after.jumpHeld()
                || before.action() != after.action() || before.grounded() != after.grounded()
                || before.posture().crouching() != after.posture().crouching()
                || before.posture().blockedLow() != after.posture().blockedLow()
                || before.parkour().mode() != after.parkour().mode() || before.parkour().instance() != after.parkour().instance()
                || before.parkour().wall().x() != after.parkour().wall().x() || before.parkour().wall().z() != after.parkour().wall().z()
                || before.parkour().landingSlide() != after.parkour().landingSlide()
                || before.parkour().glide().active() != after.parkour().glide().active()
                || before.parkour().glide().instance() != after.parkour().glide().instance()
                || !before.parkour().slideBoost().equals(after.parkour().slideBoost())
                || after.parkour().assisted() && player.tickCount % 10 == 0
                || (before.posture().rollRemaining() > 0) != (after.posture().rollRemaining() > 0)) {
            NetworkHandler.sendToPlayersTrackingEntityAndSelf(player, state(player, false));
            PUBLISHED.put(player, after);
        }
    }

    public static void tracking(ServerPlayer observer, ServerPlayer target) {
        NetworkHandler.sendToPlayer(observer, state(target, false));
    }

    public static void changedDimension(ServerPlayer player) {
        ParkourPhysics.runtime(player).reset();
        MovementPhysics.controller(player).observe(false, false, true, player.tickCount);
        MovementActionHooks.stopAll(player, ActionEndReason.DIMENSION_CHANGED);
        NetworkHandler.sendToPlayersTrackingEntityAndSelf(player, state(player, false));
        PUBLISHED.put(player, MovementPhysics.controller(player).snapshot(player.tickCount));
    }

    public static void knockback(ServerPlayer player) {
        MovementPhysics.controller(player).posture().stopRoll(player.tickCount);
        MovementActionHooks.reconcile(player);
        NetworkHandler.sendToPlayersTrackingEntityAndSelf(player, state(player, false));
        PUBLISHED.put(player, MovementPhysics.controller(player).snapshot(player.tickCount));
    }

    public static void logout(ServerPlayer player) {
        ParkourPhysics.runtime(player).reset();
        MovementPhysics.controller(player).observe(false, false, true, player.tickCount);
        MovementActionHooks.stopAll(player, ActionEndReason.DISCONNECTED);
        PUBLISHED.remove(player);
    }

    private static MovementStatePayload state(ServerPlayer player, boolean correct) {
        return new MovementStatePayload(player.getUUID(), MovementPhysics.controller(player).snapshot(player.tickCount),
                correct, player.getDeltaMovement());
    }
}
