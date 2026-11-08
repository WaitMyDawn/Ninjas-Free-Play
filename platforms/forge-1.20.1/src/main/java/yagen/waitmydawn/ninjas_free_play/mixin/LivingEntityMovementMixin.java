package yagen.waitmydawn.ninjas_free_play.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import yagen.waitmydawn.ninjas_free_play.player.MovementPhysics;
import yagen.waitmydawn.ninjas_free_play.player.ParkourPhysics;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMovementMixin {
    @Unique
    private double ninjas$rollFriction = 1;
    @Unique
    private double ninjas$retention = -1;

    @ModifyConstant(method = "aiStep", constant = @Constant(doubleValue = 0.003))
    private double ninjas$glideVelocityThreshold(double threshold) {
        return (Object) this instanceof Player player && MovementPhysics.controller(player).parkour().gliding() ? 0 : threshold;
    }

    @ModifyExpressionValue(method = "travel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/attributes/AttributeInstance;getValue()D", ordinal = 0))
    private double ninjas$parkourGravity(double gravity) {
        return (Object) this instanceof Player player && MovementPhysics.eligible(player) ? ParkourPhysics.gravity(player, gravity) : gravity;
    }

    @Inject(method = "travel", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;handleRelativeFrictionAndCalculateMovement(Lnet/minecraft/world/phys/Vec3;F)Lnet/minecraft/world/phys/Vec3;"))
    private void ninjas$captureRollFriction(Vec3 input, CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;
        ninjas$rollFriction = 1;
        ninjas$retention = -1;
        if (entity instanceof Player player && MovementPhysics.eligible(player)
                && (ninjas$retention = ParkourPhysics.retention(player)) >= 0) {
            var below = ((EntityCollisionAccessor) player).ninjas$frictionBlock();
            float friction = player.level().getBlockState(below).getFriction(player.level(), below, player);
            ninjas$rollFriction = player.onGround() ? friction * 0.91F : 0.91F;
        }
    }

    @WrapOperation(method = "travel", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;setDeltaMovement(DDD)V", ordinal = 1),
            slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;handleRelativeFrictionAndCalculateMovement(Lnet/minecraft/world/phys/Vec3;F)Lnet/minecraft/world/phys/Vec3;")))
    private void ninjas$retainRollMomentum(LivingEntity entity, double x, double y, double z, Operation<Void> original) {
        // Undo only horizontal drag after collision resolution; zeroed axes and vertical gravity remain authoritative.
        if (ninjas$retention >= 0 && ninjas$rollFriction > 0) {
            x = x / ninjas$rollFriction * ninjas$retention;
            z = z / ninjas$rollFriction * ninjas$retention;
        }
        original.call(entity, x, y, z);
    }
}
