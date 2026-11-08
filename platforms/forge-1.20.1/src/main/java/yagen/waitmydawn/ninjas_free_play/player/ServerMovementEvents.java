package yagen.waitmydawn.ninjas_free_play.player;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import yagen.waitmydawn.ninjas_free_play.api.movement.ActionEndReason;
import yagen.waitmydawn.ninjas_free_play.api.movement.ContactDamageContext;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraft.tags.DamageTypeTags;
import yagen.waitmydawn.ninjas_free_play.core.ParkourMode;
import yagen.waitmydawn.ninjas_free_play.network.NetworkHandler;
import yagen.waitmydawn.ninjas_free_play.NinjasFreePlay;
import yagen.waitmydawn.ninjas_free_play.api.attribute.NAttributes;
import yagen.waitmydawn.ninjas_free_play.core.MovementSnapshot;
import yagen.waitmydawn.ninjas_free_play.core.JumpKind;
import yagen.waitmydawn.ninjas_free_play.network.MovementInputPayload;
import yagen.waitmydawn.ninjas_free_play.network.MovementStatePayload;

import java.util.Map;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = NinjasFreePlay.MODID)
public final class ServerMovementEvents {
    private static final Map<ServerPlayer, MovementSnapshot> PUBLISHED = new WeakHashMap<>();

    private ServerMovementEvents() {
    }

    public static void receive(MovementInputPayload payload, ServerPlayer player) {
        if (player == null) return;
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

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
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

    @SubscribeEvent
    public static void tracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer observer && event.getTarget() instanceof ServerPlayer target) {
            NetworkHandler.sendToPlayer(observer, state(target, false));
        }
    }

    @SubscribeEvent
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ParkourPhysics.runtime(player).reset();
            MovementPhysics.controller(player).observe(false, false, true, player.tickCount);
            MovementActionHooks.stopAll(player, ActionEndReason.DIMENSION_CHANGED);
            NetworkHandler.sendToPlayersTrackingEntityAndSelf(player, state(player, false));
            PUBLISHED.put(player, MovementPhysics.controller(player).snapshot(player.tickCount));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void knockback(LivingKnockBackEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) return;
        MovementPhysics.controller(player).posture().stopRoll(player.tickCount);
        MovementActionHooks.reconcile(player);
        NetworkHandler.sendToPlayersTrackingEntityAndSelf(player, state(player, false));
        PUBLISHED.put(player, MovementPhysics.controller(player).snapshot(player.tickCount));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void kickKnockback(LivingKnockBackEvent event) {
        if (ParkourCombat.ownsDamageKnockback(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void death(LivingDeathEvent event) {
        if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer player) {
            MovementActionHooks.stopAll(player, ActionEndReason.DEATH);
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ParkourPhysics.runtime(player).reset();
            MovementPhysics.controller(player).observe(false, false, true, player.tickCount);
            MovementActionHooks.stopAll(player, ActionEndReason.DISCONNECTED);
            PUBLISHED.remove(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void incomingDamage(LivingAttackEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (ContactDamageContext.suppresses(event.getEntity(), event.getSource())) {
            event.setCanceled(true);
            return;
        }
    }

    @SubscribeEvent
    public static void fallDamage(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (event.getEntity() instanceof Player player && event.getSource().is(DamageTypeTags.IS_FALL)
                && (MovementPhysics.controller(player).parkour().gliding()
                || MovementPhysics.controller(player).posture().rolling(player.tickCount)))
            event.setAmount(event.getAmount() * 0.25f);
    }

    private static MovementStatePayload state(ServerPlayer player, boolean correct) {
        return new MovementStatePayload(player.getUUID(), MovementPhysics.controller(player).snapshot(player.tickCount),
                correct, player.getDeltaMovement());
    }
}
