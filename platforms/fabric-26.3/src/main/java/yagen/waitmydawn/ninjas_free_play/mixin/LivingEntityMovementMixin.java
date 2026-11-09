package yagen.waitmydawn.ninjas_free_play.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import yagen.waitmydawn.ninjas_free_play.player.ParkourCombat;
import yagen.waitmydawn.ninjas_free_play.player.ServerMovementEvents;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import yagen.waitmydawn.ninjas_free_play.player.MovementPhysics;
import yagen.waitmydawn.ninjas_free_play.player.ParkourPhysics;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMovementMixin {
    @Inject(method = "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V", at = @At("HEAD"), cancellable = true)
    private void ninjas$replaceKickKnockback(double strength, double x, double z, DamageSource source,
                                             float damage, boolean projectile, CallbackInfo ci) {
        if (ParkourCombat.ownsDamageKnockback((LivingEntity) (Object) this)) ci.cancel();
    }

    @Inject(method = "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V", at = @At("TAIL"))
    private void ninjas$onKnockback(double strength, double x, double z, DamageSource source,
                                             float damage, boolean projectile, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player) ServerMovementEvents.knockback(player);
    }

    @ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
    private float ninjas$reduceFallDamage(float amount, ServerLevel level, DamageSource source) {
        if ((Object) this instanceof Player player && source.is(DamageTypeTags.IS_FALL)) {
            var movement = MovementPhysics.controller(player);
            if (movement.parkour().gliding() || movement.posture().rolling(player.tickCount)) return amount * .25f;
        }
        return amount;
    }

    @ModifyConstant(method = "aiStep", constant = @Constant(doubleValue = 0.003))
    private double ninjas$glideVelocityThreshold(double threshold) {
        return (Object) this instanceof Player player && MovementPhysics.controller(player).parkour().gliding() ? 0 : threshold;
    }

    @ModifyExpressionValue(method = "travelInAir", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getEffectiveGravity()D"))
    private double ninjas$parkourGravity(double gravity) {
        return (Object) this instanceof Player player && MovementPhysics.eligible(player) ? ParkourPhysics.gravity(player, gravity) : gravity;
    }

    @WrapOperation(method = "travelInAir", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;setDeltaMovement(DDD)V", ordinal = 1))
    private void ninjas$retainRollMomentum(LivingEntity entity, double x, double y, double z, Operation<Void> original,
                                            @Local(ordinal = 1) Vec3 collisionVelocity) {
        if ((Object) this instanceof Player player && MovementPhysics.eligible(player)) {
            double retention = ParkourPhysics.retention(player);
            if (retention >= 0) {
                // Preserve collision-resolved axes and leave vertical gravity to vanilla.
                x = collisionVelocity.x * retention;
                z = collisionVelocity.z * retention;
            }
        }
        original.call(entity, x, y, z);
    }
}
