package yagen.waitmydawn.ninjas_free_play.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yagen.waitmydawn.ninjas_free_play.core.MovementSession;
import yagen.waitmydawn.ninjas_free_play.core.MovementActionTracker;
import yagen.waitmydawn.ninjas_free_play.player.MovementAccess;
import yagen.waitmydawn.ninjas_free_play.player.MovementPhysics;
import yagen.waitmydawn.ninjas_free_play.player.ParkourPhysics;
import yagen.waitmydawn.ninjas_free_play.player.ParkourRuntime;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import yagen.waitmydawn.ninjas_free_play.api.movement.ContactDamageContext;

@Mixin(Player.class)
public abstract class PlayerMovementMixin implements MovementAccess {
    @Unique
    private final MovementSession ninjas$movement = new MovementSession();
    @Unique
    private final MovementActionTracker ninjas$actions = new MovementActionTracker();
    @Unique
    private final ParkourRuntime ninjas$parkourRuntime = new ParkourRuntime();
    @Unique
    private float ninjas$lowHeightRatio;

    @Override
    public MovementSession ninjas$movement() {
        return ninjas$movement;
    }

    @Override
    public MovementActionTracker ninjas$actions() {
        return ninjas$actions;
    }

    @Override
    public ParkourRuntime ninjas$parkourRuntime() {
        return ninjas$parkourRuntime;
    }

    @Override
    public float ninjas$lowHeightRatio() {
        return ninjas$lowHeightRatio;
    }

    @Override
    public void ninjas$lowHeightRatio(float ratio) {
        ninjas$lowHeightRatio = ratio;
    }

    @Inject(method = "travel", at = @At("HEAD"))
    private void ninjas$rollDrive(Vec3 input, CallbackInfo ci) {
        Player player = (Player) (Object) this;
        if (player.isControlledByLocalInstance()) {
            ParkourPhysics.beforeTravel(player);
            MovementPhysics.driveRoll(player);
            ParkourPhysics.captureTravelSpeed(player);
        }
    }

    @Inject(method = "travel", at = @At("RETURN"))
    private void ninjas$measureTravel(Vec3 input, CallbackInfo ci) {
        Player player = (Player) (Object) this;
        if (player.isControlledByLocalInstance()) ParkourPhysics.afterTravel(player);
    }

    @WrapOperation(method = "touch", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;playerTouch(Lnet/minecraft/world/entity/player/Player;)V"))
    private void ninjas$touchDamage(Entity other, Player player, Operation<Void> original) {
        try (var scope = ContactDamageContext.collision(player, other)) {
            original.call(other, player);
        }
    }

    @Inject(method = "getDimensions", at = @At("HEAD"), cancellable = true)
    private void ninjas$compactDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        if (pose == Pose.CROUCHING && ninjas$lowHeightRatio > 0) {
            Player player = (Player) (Object) this;
            cir.setReturnValue(player.getDimensions(Pose.STANDING).scale(1, ninjas$lowHeightRatio));
        }
    }

    @Inject(method = "getStandingEyeHeight", at = @At("HEAD"), cancellable = true)
    private void ninjas$compactEyeHeight(Pose pose, EntityDimensions dimensions, CallbackInfoReturnable<Float> cir) {
        if (pose == Pose.CROUCHING && ninjas$lowHeightRatio > 0) cir.setReturnValue(dimensions.height * 0.9f);
    }

    @Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true)
    private void ninjas$crouchPose(CallbackInfo ci) {
        if (ninjas$movement == null) return;
        Player player = (Player) (Object) this;
        MovementPhysics.applyCrouch(player);
        boolean preserveLow = MovementPhysics.eligible(player) && (ninjas$movement.posture().low(player.tickCount)
                || ninjas$movement.compactAction(player.tickCount)
                || ninjas$lowHeightRatio > 0 && !MovementPhysics.canStand(player));
        if (preserveLow) ci.cancel();
    }
}
