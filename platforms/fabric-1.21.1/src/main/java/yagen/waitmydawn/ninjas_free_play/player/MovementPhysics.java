package yagen.waitmydawn.ninjas_free_play.player;

import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import yagen.waitmydawn.ninjas_free_play.animation.core.LaunchOrientation;
import yagen.waitmydawn.ninjas_free_play.core.MovementSession;
import yagen.waitmydawn.ninjas_free_play.core.MovementAttribute;
import yagen.waitmydawn.ninjas_free_play.api.attribute.NAttributes;
import yagen.waitmydawn.ninjas_free_play.core.JumpKind;
import yagen.waitmydawn.ninjas_free_play.core.MovementInput;
import yagen.waitmydawn.ninjas_free_play.core.MovementValues;
import yagen.waitmydawn.ninjas_free_play.core.RollDrive;
import yagen.waitmydawn.ninjas_free_play.core.RollKind;

public final class MovementPhysics {
    private MovementPhysics() {
    }

    public static MovementSession controller(Player player) {
        return ((MovementAccess) player).ninjas$movement();
    }

    public static boolean low(Player player) {
        return ((MovementAccess) player).ninjas$lowHeightRatio() > 0;
    }

    public static boolean canStand(Player player) {
        return player.level().noCollision(player, player.getDimensions(Pose.STANDING).makeBoundingBox(player.position()).deflate(1e-7));
    }

    public static boolean canRoll(Player player, MovementInput input) {
        var kind = RollKind.fromInput(input.strafe(), input.forward());
        double ratio = kind == RollKind.FORWARD
                ? MovementAttribute.ROLL_HEIGHT_RATIO.effective(player.getAttributeValue(NAttributes.ROLL_HEIGHT_RATIO)) : 1.25;
        return player.level().noCollision(player, player.getDimensions(Pose.STANDING).scale(1, (float) ratio)
                .makeBoundingBox(player.position()).deflate(1e-7));
    }

    public static void observe(Player player) {
        controller(player).observe(eligible(player), grounded(player), canStand(player), player.tickCount);
    }

    public static boolean eligible(Player player) {
        return player.isAlive() && !player.isSpectator() && !player.isPassenger() && !player.isSleeping()
                && !player.getAbilities().flying && !player.isFallFlying() && !player.isSwimming()
                && !player.isInWater() && !player.isInLava()
                && !player.onClimbable() && !player.isAutoSpinAttack();
    }

    public static boolean grounded(Player player) {
        if (!player.onGround()) return false;
        AABB box = player.getBoundingBox();
        AABB feet = new AABB(box.minX + 1e-5, box.minY - 0.04, box.minZ + 1e-5,
                box.maxX - 1e-5, box.minY + 1e-7, box.maxZ - 1e-5);
        return !player.level().noCollision(player, feet);
    }

    public static void applyCrouch(Player player) {
        MovementAccess access = (MovementAccess) player;
        float previous = access.ninjas$lowHeightRatio();
        var posture = controller(player).posture();
        boolean actionLow = controller(player).compactAction(player.tickCount);
        if (actionLow) posture.keepLow();
        posture.observe(eligible(player), grounded(player), canStand(player), player.tickCount);
        boolean low = eligible(player) && (actionLow || posture.low(player.tickCount) || previous > 0 && !canStand(player));
        if (low) {
            var attribute = posture.rolling(player.tickCount) ? MovementAttribute.ROLL_HEIGHT_RATIO : MovementAttribute.CROUCH_HEIGHT_RATIO;
            float ratio = (float) player.getAttributeValue(NAttributes.holder(attribute));
            ratio = (float) attribute.effective(ratio);
            var target = player.getDimensions(Pose.STANDING).scale(1, ratio);
            if (player.level().noCollision(player, target.makeBoundingBox(player.position()).deflate(1e-7))) {
                access.ninjas$lowHeightRatio(ratio);
            } else if (previous <= 0) return;
            player.setPose(Pose.CROUCHING);
            var dimensions = player.getDimensions(Pose.CROUCHING);
            if (Math.abs(player.getBbHeight() - dimensions.height()) > 1e-5) player.refreshDimensions();
            if (posture.crouching() && !posture.rolling(player.tickCount) && !actionLow) player.setSprinting(false);
        } else if (previous > 0) {
            access.ninjas$lowHeightRatio(0);
            if (eligible(player)) player.setPose(player.isShiftKeyDown() ? Pose.CROUCHING : Pose.STANDING);
            player.refreshDimensions();
        }
    }

    public static void applyJump(Player player, JumpKind kind, MovementInput input, MovementValues values) {
        if (kind == JumpKind.NONE) return;
        if (kind == JumpKind.GROUND) {
            player.jumpFromGround();
        } else if (kind == JumpKind.AIR) {
            Vec3 motion = player.getDeltaMovement();
            player.setDeltaMovement(motion.x, Math.max(motion.y, values.airJumpPerTick()), motion.z);
        } else if (kind == JumpKind.WALL) {
            ParkourPhysics.wallImpulse(player, input, values);
        } else {
            var impulse = LaunchOrientation.fromDegrees(input.yaw(), input.pitch()).impulsePerTick(values.bulletJumpPerTick());
            player.setDeltaMovement(player.getDeltaMovement().add(impulse.x(), impulse.y() + values.bulletLiftPerTick(), impulse.z()));
        }
        // Suppress the vanilla server's ground-jump inference when the matching move packet arrives.
        player.setOnGround(false);
        player.hasImpulse = true;
    }

    public static void applyRoll(Player player) {
        driveRoll(player);
        player.hasImpulse = true;
    }

    public static void driveRoll(Player player) {
        var posture = controller(player).posture();
        if (!eligible(player) || !posture.rolling(player.tickCount)) return;
        var state = posture.snapshot(player.tickCount);
        Vec3 motion = player.getDeltaMovement();
        double phase = state.rollDuration() == 0 ? 1 : state.rollAge() / (double) state.rollDuration();
        var driven = RollDrive.sustain(motion.x, motion.z, state.rollYaw(), state.rollSpeed(), phase);
        Vec3 resolved = null;
        if (state.rollKind() != RollKind.FORWARD) {
            resolved = ((MovementCollisionAccess) player).ninjas$resolveMovement(new Vec3(driven.x(), motion.y, driven.z()));
            if (!RollClearance.canFollow(player, resolved)) {
                posture.stopRoll(player.tickCount);
                return;
            }
        }
        // Collisions remain authoritative; aborting prevents a blocked roll from restarting its motor every tick.
        if (player.horizontalCollision && state.rollAge() > 0 && motion.horizontalDistanceSqr() < 1e-4) {
            if (resolved == null)
                resolved = ((MovementCollisionAccess) player).ninjas$resolveMovement(new Vec3(driven.x(), motion.y, driven.z()));
            if (resolved.horizontalDistanceSqr() < 1e-4) {
                posture.stopRoll(player.tickCount);
                return;
            }
        }
        player.setDeltaMovement(driven.x(), motion.y, driven.z());
    }

    public static double customJumpPower(JumpKind kind, MovementValues values) {
        return switch (kind) {
            case BULLET -> values.bulletJumpPerTick();
            case AIR -> values.airJumpPerTick();
            case WALL -> values.get(MovementAttribute.WALL_DASH_STRENGTH) / 20;
            default -> 0;
        };
    }
}
