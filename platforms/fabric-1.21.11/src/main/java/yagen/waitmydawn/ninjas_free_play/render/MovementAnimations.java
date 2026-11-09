package yagen.waitmydawn.ninjas_free_play.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import yagen.waitmydawn.ninjas_free_play.NinjasFreePlay;
import yagen.waitmydawn.ninjas_free_play.animation.core.AnimationPlayer;
import yagen.waitmydawn.ninjas_free_play.animation.core.FacingPlayer;
import yagen.waitmydawn.ninjas_free_play.animation.core.LaunchOrientation;
import yagen.waitmydawn.ninjas_free_play.animation.core.Rotation;
import yagen.waitmydawn.ninjas_free_play.config.ClientConfigs;
import yagen.waitmydawn.ninjas_free_play.core.JumpKind;
import yagen.waitmydawn.ninjas_free_play.core.MovementAttribute;
import yagen.waitmydawn.ninjas_free_play.core.RollKind;
import yagen.waitmydawn.ninjas_free_play.core.ParkourMode;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import yagen.waitmydawn.ninjas_free_play.animation.core.*;
import yagen.waitmydawn.ninjas_free_play.animation.core.HandContactProfile;
import yagen.waitmydawn.ninjas_free_play.player.MovementPhysics;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

public final class MovementAnimations {
    private static final Identifier BULLET_JUMP = Identifier.fromNamespaceAndPath(NinjasFreePlay.MODID, "bullet_jump");
    public static final Identifier ROLL = Identifier.fromNamespaceAndPath(NinjasFreePlay.MODID, "roll");
    private static final Identifier CROUCH_IDLE = Identifier.fromNamespaceAndPath(NinjasFreePlay.MODID, "crouch_idle");
    private static final Identifier CROUCH_WALK = Identifier.fromNamespaceAndPath(NinjasFreePlay.MODID, "crouch_walk");

    private static Identifier id(String name) {
        return Identifier.fromNamespaceAndPath(NinjasFreePlay.MODID, name);
    }

    private static final Map<AbstractClientPlayer, Playback> PLAYERS = new HashMap<>();

    private MovementAnimations() {
    }

    private static final class Playback {
        final AnimationPlayer pose = new AnimationPlayer();
        final FacingPlayer facing;
        long sequence = Long.MIN_VALUE;
        long retiredAction = -1;
        Identifier current;
        boolean launchHeld;
        double end;
        double facingUntil;
        double clipStarted;
        double clipDuration = 1;
        double heightFrom = 1, heightTo = 1, heightVelocity, heightStarted;
        double armsFrom, armsTo, armsStarted;
        double consumeFrom, consumeTo, consumeStarted;
        double bodyUseFrom, bodyUseTo, bodyUseStarted;
        Bone consumingArm = Bone.RIGHT_ARM;
        int wallX, wallZ;
        int adaptiveFacingTick = Integer.MIN_VALUE;
        double glideYaw = Double.NaN;

        Playback(AbstractClientPlayer entity) {
            facing = new FacingPlayer(time -> AnimationPreviewController.entityFacing(entity, time));
        }

        void update(AbstractClientPlayer entity, double now) {
            var state = MovementPhysics.controller(entity).snapshot(entity.tickCount);
            var posture = state.posture();
            var parkour = state.parkour();
            double transition = ClientConfigs.ANIMATION_TRANSITION_SECONDS.get();
            boolean jumpPose = state.actionSequence() > posture.rollSequence() && state.actionSequence() > retiredAction
                    && (state.action() == JumpKind.AIR && state.actionAge() < 11 || state.action() == JumpKind.WALL && state.actionAge() < 7);
            boolean jumpOverlay = jumpPose && (parkour.mode() == ParkourMode.NONE || parkour.mode() == ParkourMode.AIM_GLIDE);
            if (parkour.mode() != ParkourMode.NONE && !jumpOverlay)
                retiredAction = Math.max(retiredAction, state.actionSequence());
            var use = entity.getUseItem().getUseAnimation();
            boolean consuming = entity.isUsingItem() && (use == net.minecraft.world.item.ItemUseAnimation.EAT || use == net.minecraft.world.item.ItemUseAnimation.DRINK);
            double consumeTarget = parkour.mode().assisted() && !jumpOverlay && consuming ? 1 : 0;
            if (consumeTarget != consumeTo) {
                consumeFrom = consumption(now);
                consumeTo = consumeTarget;
                consumeStarted = now;
            }
            if (consuming) {
                var arm = entity.getUsedItemHand() == net.minecraft.world.InteractionHand.MAIN_HAND ? entity.getMainArm() : entity.getMainArm().getOpposite();
                consumingArm = arm == net.minecraft.world.entity.HumanoidArm.LEFT ? Bone.LEFT_ARM : Bone.RIGHT_ARM;
            }
            double armTarget = parkour.mode().assisted() && !jumpOverlay && (entity.isUsingItem() || entity.swinging) ? 1 : 0;
            double bodyTarget = armTarget > 0 && !consuming ? 1 : 0;
            if (bodyTarget != bodyUseTo) {
                bodyUseFrom = bodyUse(now);
                bodyUseTo = bodyTarget;
                bodyUseStarted = now;
            }
            if (armTarget != armsTo) {
                armsFrom = arms(now);
                armsTo = armTarget;
                armsStarted = now;
            }
            Identifier desired = null;
            long token = 0;
            int age = 0;
            Rotation direction = null;
            var bulletClip = AnimationResources.INSTANCE.get(BULLET_JUMP);
            if (posture.rollRemaining() > 0) {
                desired = RollAnimations.clip(posture.rollKind());
                token = posture.rollSequence();
                age = posture.rollAge();
                direction = LaunchOrientation.entityModelRotation(Math.toRadians(posture.bodyYaw()));
            } else if (parkour.mode() != ParkourMode.NONE && !jumpOverlay) {
                desired = id(switch (parkour.mode()) {
                    case SLIDE -> "slide";
                    case FLYING_KICK -> "flying_kick";
                    case AIM_GLIDE -> "aim_glide";
                    case WALL_LATCH -> "wall_latch";
                    case NONE -> throw new IllegalStateException();
                });
                token = parkour.instance();
                age = parkour.age();
                double yaw = entity.getYRot();
                if (parkour.mode() == ParkourMode.WALL_LATCH)
                    yaw = Math.toDegrees(Math.atan2(parkour.wall().x(), -parkour.wall().z()));
                else if (parkour.mode().compact() && entity.getDeltaMovement().horizontalDistanceSqr() > 1e-6)
                    yaw = Math.toDegrees(Math.atan2(-entity.getDeltaMovement().x, entity.getDeltaMovement().z));
                direction = LaunchOrientation.entityModelRotation(Math.toRadians(yaw));
                if (parkour.mode() == ParkourMode.WALL_LATCH && id("wall_latch").equals(current) && launchHeld && (wallX != parkour.wall().x() || wallZ != parkour.wall().z())) {
                    Rotation target = direction;
                    facing.play(time -> target, now, transition);
                }
                wallX = parkour.wall().x();
                wallZ = parkour.wall().z();
            } else if (jumpPose) {
                desired = id(state.action() == JumpKind.AIR ? "air_flip" : parkour.wallDash().animation());
                token = state.actionSequence();
                age = state.actionAge();
                direction = LaunchOrientation.entityModelRotation(Math.toRadians(state.yaw()));
            } else if (state.action() == JumpKind.BULLET && state.actionSequence() > posture.rollSequence() && state.actionSequence() > retiredAction
                    && bulletClip != null && state.actionAge() / 20.0 < bulletClip.duration()) {
                desired = BULLET_JUMP;
                token = state.actionSequence();
                age = state.actionAge();
                direction = LaunchOrientation.fromDegrees(state.yaw(), state.pitch()).worldModelRotation();
            } else if (MovementPhysics.low(entity)) {
                double movement = Math.hypot(entity.getX() - entity.xo, entity.getZ() - entity.zo);
                desired = movement > (CROUCH_WALK.equals(current) ? 0.002 : 0.006) ? CROUCH_WALK : CROUCH_IDLE;
            }
            boolean compact = desired != null && (desired.equals(CROUCH_IDLE) || desired.equals(CROUCH_WALK) || parkour.mode().compact()
                    || RollAnimations.contains(desired) && posture.rollKind() == RollKind.FORWARD);
            double targetHeight = compact
                    ? ((yagen.waitmydawn.ninjas_free_play.player.MovementAccess) entity).ninjas$lowHeightRatio()
                    / MovementAttribute.CROUCH_HEIGHT_RATIO.defaultValue() : 1;
            if (targetHeight <= 0) targetHeight = 1;
            if (heightTo != targetHeight) {
                double previous = height(now);
                double velocity = heightDerivative(now);
                heightFrom = previous;
                heightVelocity = velocity;
                heightTo = targetHeight;
                heightStarted = now;
            }
            if (sequence != token || !java.util.Objects.equals(current, desired)) {
                sequence = token;
                current = desired;
                var clip = desired == null ? null : AnimationResources.INSTANCE.get(desired);
                if (clip != null) {
                    if (RollAnimations.contains(desired)) clip = clip.retimed(posture.rollDuration() / 20.0);
                    double start = direction == null ? now : (entity.tickCount - age) / 20.0;
                    clipStarted = start;
                    clipDuration = clip.duration();
                    pose.play(clip, start, transition);
                    if (direction != null) {
                        Rotation target = direction;
                        facing.play(parkour.mode() == ParkourMode.AIM_GLIDE && !jumpOverlay ? time -> glideFacing(entity)
                                : parkour.mode() == ParkourMode.FLYING_KICK ? time -> kickFacing(entity) : time -> target, start, transition);
                        end = clip.loop() ? Double.POSITIVE_INFINITY : start + clip.duration();
                        launchHeld = true;
                    } else returnFacing(entity, now, transition);
                } else {
                    pose.stop(now, transition);
                    returnFacing(entity, now, transition);
                }
            }
            boolean glideFacing = id("aim_glide").equals(current);
            boolean wallFacing = jumpPose && state.action() == JumpKind.WALL && id(parkour.wallDash().animation()).equals(current);
            if (launchHeld && (glideFacing || wallFacing) && adaptiveFacingTick != entity.tickCount) {
                Rotation target = glideFacing ? glideFacing(entity) : LaunchOrientation.entityModelRotation(Math.toRadians(entity.getYRot()));
                facing.play(time -> target, now, transition);
                adaptiveFacingTick = entity.tickCount;
            }
            if (launchHeld && now >= end) returnFacing(entity, now, transition);
        }

        Rotation glideFacing(AbstractClientPlayer entity) {
            Vec3 motion = motion(entity);
            if (motion.horizontalDistanceSqr() > 1e-6) glideYaw = Math.atan2(-motion.x, motion.z);
            if (!Double.isFinite(glideYaw)) glideYaw = Math.toRadians(entity.getYRot());
            var clip = AnimationResources.INSTANCE.get(id("aim_glide"));
            Rotation correction = clip == null ? Rotation.IDENTITY : GlideOrientation.correction(clip.sample(0), motion.length() * 20);
            return LaunchOrientation.entityModelRotation(glideYaw).multiply(correction);
        }

        double arms(double now) {
            double duration = ClientConfigs.ANIMATION_TRANSITION_SECONDS.get();
            double s = duration == 0 ? 1 : Math.max(0, Math.min(1, (now - armsStarted) / duration));
            return armsFrom + (armsTo - armsFrom) * s * s * (3 - 2 * s);
        }

        double bodyUse(double now) {
            double duration = ClientConfigs.ANIMATION_TRANSITION_SECONDS.get();
            double t = duration == 0 ? 1 : Math.max(0, Math.min(1, (now - bodyUseStarted) / duration));
            return bodyUseFrom + (bodyUseTo - bodyUseFrom) * t * t * (3 - 2 * t);
        }

        double consumption(double now) {
            double duration = ClientConfigs.ANIMATION_TRANSITION_SECONDS.get();
            double t = duration == 0 ? 1 : Math.max(0, Math.min(1, (now - consumeStarted) / duration));
            return consumeFrom + (consumeTo - consumeFrom) * t * t * (3 - 2 * t);
        }

        double height(double now) {
            double duration = ClientConfigs.ANIMATION_TRANSITION_SECONDS.get();
            if (duration == 0 || now >= heightStarted + duration) return heightTo;
            double s = Math.max(0, (now - heightStarted) / duration);
            return Math.max(0.1, heightFrom + (heightTo - heightFrom) * s * s * (3 - 2 * s)
                    + heightVelocity * duration * s * (1 - s) * (1 - s));
        }

        double heightDerivative(double now) {
            double duration = ClientConfigs.ANIMATION_TRANSITION_SECONDS.get();
            if (duration == 0 || now >= heightStarted + duration) return 0;
            double s = Math.max(0, (now - heightStarted) / duration);
            return (heightTo - heightFrom) * 6 * s * (1 - s) / duration + heightVelocity * (1 - 4 * s + 3 * s * s);
        }

        void returnFacing(AbstractClientPlayer entity, double now, double transition) {
            if (!launchHeld) return;
            launchHeld = false;
            facing.play(time -> AnimationPreviewController.entityFacing(entity, time), now, transition);
            facingUntil = now + transition;
        }
    }

    private static Rotation kickFacing(AbstractClientPlayer entity) {
        Vec3 motion = motion(entity);
        if (motion.lengthSqr() < 1e-8) motion = entity.getLookAngle();
        var clip = AnimationResources.INSTANCE.get(id("flying_kick"));
        if (clip == null) return LaunchOrientation.entityModelRotation(Math.toRadians(entity.getYRot()));
        var terminal = clip.sample(Math.nextDown(clip.duration()));
        var aim = new LaunchOrientation(Math.atan2(-motion.x, motion.z), -Math.atan2(motion.y, motion.horizontalDistance()));
        return KickOrientation.facing(terminal, aim);
    }

    private static Vec3 motion(AbstractClientPlayer entity) {
        return entity == Minecraft.getInstance().player ? entity.getDeltaMovement()
                : new Vec3(entity.getX() - entity.xo, entity.getY() - entity.yo, entity.getZ() - entity.zo);
    }

    private static Playback playback(AbstractClientPlayer entity, float partialTick) {
        Playback playback = PLAYERS.computeIfAbsent(entity, Playback::new);
        playback.update(entity, (entity.tickCount + (double) partialTick) / 20);
        return playback;
    }

    public static boolean isActive(AbstractClientPlayer entity, float partialTick) {
        if (!ClientConfigs.ENABLE_PLAYER_ANIMATIONS.get()) return false;
        Playback playback = playback(entity, partialTick);
        double now = (entity.tickCount + (double) partialTick) / 20;
        return playback.pose.sample(now).weight() > 0 || playback.launchHeld || now < playback.facingUntil;
    }

    public static AnimationPlayer.Sample sample(AbstractClientPlayer entity, float partialTick) {
        return playback(entity, partialTick).pose.sample((entity.tickCount + (double) partialTick) / 20);
    }

    public static Rotation facing(AbstractClientPlayer entity, float partialTick) {
        double now = (entity.tickCount + (double) partialTick) / 20;
        return AnimationPreviewController.entityFacing(entity, now).inverse().multiply(playback(entity, partialTick).facing.sample(now));
    }

    public static double heightScale(AbstractClientPlayer entity, float partialTick) {
        return playback(entity, partialTick).height((entity.tickCount + (double) partialTick) / 20);
    }

    public static boolean rollPose(AbstractClientPlayer entity, float partialTick) {
        var current = playback(entity, partialTick).current;
        return RollAnimations.contains(current) || id("air_flip").equals(current) || id("flying_kick").equals(current);
    }

    public static double armUseWeight(AbstractClientPlayer entity, float partialTick) {
        return playback(entity, partialTick).arms((entity.tickCount + (double) partialTick) / 20);
    }

    public static double bodyUseWeight(AbstractClientPlayer entity, float partial) {
        return playback(entity, partial).bodyUse((entity.tickCount + (double) partial) / 20);
    }

    public static double consumptionWeight(AbstractClientPlayer entity, float partial) {
        return playback(entity, partial).consumption((entity.tickCount + (double) partial) / 20);
    }

    public static Bone consumingArm(AbstractClientPlayer entity, float partial) {
        return playback(entity, partial).consumingArm;
    }

    static boolean wallDashPose(AbstractClientPlayer entity, float partialTick) {
        var state = MovementPhysics.controller(entity).snapshot(entity.tickCount);
        return state.action() == JumpKind.WALL && id(state.parkour().wallDash().animation()).equals(playback(entity, partialTick).current);
    }

    public static HandContactProfile.Weights contacts(AbstractClientPlayer entity, float partialTick) {
        var player = playback(entity, partialTick);
        double phase = ((entity.tickCount + (double) partialTick) / 20 - player.clipStarted) / player.clipDuration;
        return HandContactResources.INSTANCE.get(player.current).sample(phase);
    }

    public static void cleanup() {
        var level = Minecraft.getInstance().level;
        PLAYERS.keySet().removeIf(entity -> entity.isRemoved() || !entity.isAlive() || entity.level() != level);
    }

    public static void reload() {
        PLAYERS.clear();
        WallPoseAdapter.clear();
    }
}
