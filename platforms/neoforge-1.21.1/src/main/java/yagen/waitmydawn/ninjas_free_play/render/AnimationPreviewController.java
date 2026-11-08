package yagen.waitmydawn.ninjas_free_play.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import yagen.waitmydawn.ninjas_free_play.animation.core.AnimationClip;
import yagen.waitmydawn.ninjas_free_play.animation.core.AnimationPlayer;
import yagen.waitmydawn.ninjas_free_play.animation.core.FacingPlayer;
import yagen.waitmydawn.ninjas_free_play.animation.core.LaunchOrientation;
import yagen.waitmydawn.ninjas_free_play.animation.core.Rotation;
import yagen.waitmydawn.ninjas_free_play.animation.core.Vector3;
import yagen.waitmydawn.ninjas_free_play.config.ClientConfigs;

public final class AnimationPreviewController {
    private static AbstractClientPlayer target;
    private static AnimationPlayer player = new AnimationPlayer();
    private static double viewRadians;
    private static AnimationClip[] chain;
    private static double chainStartedAt;
    private static double chainInterval;
    private static long chainIndex;
    private static FacingPlayer facing;
    private static AnimationClip activeClip;
    private static double startedAt;
    private static boolean frozen;
    private static double frozenTime;
    private static boolean facingHeld;
    private static double facingUntil;
    private static Double previewPitch;

    private AnimationPreviewController() {
    }

    public static void play(AbstractClientPlayer entity, AnimationClip clip) {
        chain = null;
        playNext(entity, clip, frameClock(entity));
    }

    private static void playNext(AbstractClientPlayer entity, AnimationClip clip, double now) {
        if (target != entity) {
            player = new AnimationPlayer();
            facing = new FacingPlayer(time -> entityFacing(entity, time));
        }
        target = entity;
        activeClip = clip;
        frozen = false;
        startedAt = now;
        player.play(clip, now, ClientConfigs.ANIMATION_TRANSITION_SECONDS.get());
        setFacing(clip, now, ClientConfigs.ANIMATION_TRANSITION_SECONDS.get());
    }

    public static void freeze(AbstractClientPlayer entity, AnimationClip clip, double time) {
        HandGrounding.clear(entity);
        if (!Double.isFinite(time) || time < 0 || time > clip.duration())
            throw new IllegalArgumentException("Invalid preview time");
        target = entity;
        chain = null;
        player = new AnimationPlayer();
        player.play(AnimationClip.still(clip.sample(time)), frameClock(entity), 0);
        facing = new FacingPlayer(seconds -> entityFacing(entity, seconds));
        activeClip = clip;
        frozen = true;
        frozenTime = time;
        setFacing(clip, frameClock(entity), 0);
    }

    public static void stop() {
        chain = null;
        if (target == null) return;
        player.stop(frameClock(target), ClientConfigs.ANIMATION_TRANSITION_SECONDS.get());
        returnFacing(frameClock(target));
    }

    public static void setLaunchPitch(Double pitch) {
        if (pitch != null && (!Double.isFinite(pitch) || pitch < -90 || pitch > 90))
            throw new IllegalArgumentException("Invalid preview pitch");
        previewPitch = pitch;
        if (target != null && facingHeld)
            setFacing(activeClip, frameClock(target), ClientConfigs.ANIMATION_TRANSITION_SECONDS.get());
    }

    private static void setFacing(AnimationClip clip, double now, double transition) {
        facingHeld = clip.coordinateSpace() == AnimationClip.CoordinateSpace.LAUNCH;
        if (facingHeld) {
            var launch = LaunchOrientation.fromDegrees(target.getYRot(), previewPitch == null ? target.getXRot() : previewPitch);
            Rotation direction = launch.worldModelRotation();
            facing.play(time -> direction, now, transition);
            facingUntil = Double.POSITIVE_INFINITY;
        } else {
            facing.play(time -> entityFacing(target, time), now, transition);
            facingUntil = now + transition;
        }
    }

    private static void returnFacing(double now) {
        if (!facingHeld) return;
        facingHeld = false;
        double transition = ClientConfigs.ANIMATION_TRANSITION_SECONDS.get();
        facing.play(time -> entityFacing(target, time), now, transition);
        facingUntil = now + transition;
    }

    public static void chain(AbstractClientPlayer entity, AnimationClip first, AnimationClip second, double interval) {
        if (!Double.isFinite(interval) || interval < 0.05)
            throw new IllegalArgumentException("Preview interval must be at least one tick");
        play(entity, first);
        chain = new AnimationClip[]{first, second};
        chainStartedAt = frameClock(entity);
        chainInterval = interval;
        chainIndex = 0;
    }

    public static void setViewDegrees(double degrees) {
        if (!Double.isFinite(degrees)) throw new IllegalArgumentException("Preview angle must be finite");
        viewRadians = Math.toRadians(degrees);
        if (target != null) HandGrounding.clear(target);
    }

    public static double viewRadians() {
        return viewRadians;
    }

    public static boolean isClip(AbstractClientPlayer entity, net.minecraft.resources.ResourceLocation id) {
        return target == entity && activeClip == AnimationResources.INSTANCE.get(id);
    }

    public static double phase(AbstractClientPlayer entity, float partialTick) {
        if (activeClip == null || entity != target) return 0;
        return (frozen ? frozenTime : clock(entity, partialTick) - startedAt) / activeClip.duration();
    }

    public static AnimationPlayer.Sample sample(AbstractClientPlayer entity, float partialTick) {
        if (entity != target || !ClientConfigs.ENABLE_PLAYER_ANIMATIONS.get()) return AnimationPlayer.Sample.EMPTY;
        return player.sample(clock(entity, partialTick));
    }

    public static boolean isActive(AbstractClientPlayer entity, float partialTick) {
        return entity == target && ClientConfigs.ENABLE_PLAYER_ANIMATIONS.get()
                && (sample(entity, partialTick).weight() > 0 || facingHeld || clock(entity, partialTick) < facingUntil);
    }

    public static Rotation facing(AbstractClientPlayer entity, float partialTick) {
        double now = clock(entity, partialTick);
        return facing == null ? Rotation.IDENTITY : entityFacing(entity, now).inverse().multiply(facing.sample(now));
    }

    public static Rotation entityFacing(AbstractClientPlayer entity, double now) {
        float partial = (float) (now * 20 - entity.tickCount);
        return LaunchOrientation.entityModelRotation(Math.toRadians(Mth.rotLerp(partial, entity.yBodyRotO, entity.yBodyRot)));
    }

    public static Vector3 look(AbstractClientPlayer entity, float partialTick) {
        if (previewPitch != null)
            return LaunchOrientation.fromDegrees(entity.getViewYRot(partialTick), previewPitch).direction();
        var look = entity.getViewVector(partialTick);
        return new Vector3(look.x, look.y, look.z);
    }

    public static void cleanup() {
        if (target == null) return;
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null || target != minecraft.player || !target.isAlive() || target.isRemoved()
                || !ClientConfigs.ENABLE_PLAYER_ANIMATIONS.get()) {
            clear();
            return;
        }
        if (chain != null) {
            long index = Math.max(0, (long) Math.floor((clock(target, 0) - chainStartedAt + 1e-8) / chainInterval));
            if (index != chainIndex) {
                // Late ticks select the current phase instead of replaying an unbounded backlog.
                chainIndex = index;
                playNext(target, chain[(int) (index & 1)], clock(target, 0));
            }
        }
        if (!frozen && activeClip != null && !activeClip.loop() && clock(target, 0) >= startedAt + activeClip.duration()) {
            returnFacing(clock(target, 0));
        }
    }

    public static void clear() {
        target = null;
        chain = null;
        viewRadians = 0;
        player = new AnimationPlayer();
        facing = null;
        activeClip = null;
        facingHeld = false;
        facingUntil = 0;
        previewPitch = null;
    }

    private static double clock(AbstractClientPlayer entity, float partialTick) {
        return (entity.tickCount + (double) partialTick) / 20;
    }

    private static double frameClock(AbstractClientPlayer entity) {
        return clock(entity, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
    }
}
