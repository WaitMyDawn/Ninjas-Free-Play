package yagen.waitmydawn.ninjas_free_play.camera.client;

import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import yagen.waitmydawn.ninjas_free_play.NinjasFreePlay;
import yagen.waitmydawn.ninjas_free_play.animation.core.Vector3;
import yagen.waitmydawn.ninjas_free_play.camera.CameraCollision;
import yagen.waitmydawn.ninjas_free_play.camera.CameraTargeting;
import yagen.waitmydawn.ninjas_free_play.camera.core.*;
import yagen.waitmydawn.ninjas_free_play.config.ClientConfigs;

/**
 * Owns virtual views carried by vanilla's back-facing third-person camera.
 */
public final class ShoulderCamera {
    private static final CameraRig RIG = new CameraRig();
    private static final CameraOrbit ORBIT = new CameraOrbit();
    private static CameraView selected = CameraView.THIRD_PERSON_BACK;
    private static boolean rendered;
    private static Entity target;
    private static BlockGetter world;
    private static HitResult aimHit;
    private static CameraRig.Frame fixedFrame;
    private static Vec3 fixedEye;
    private static CameraProjection.Point reticle = CameraProjection.Point.HIDDEN;

    private ShoulderCamera() {
    }

    public static CameraType cycle(CameraType current, CameraType vanillaNext) {
        var view = currentView(current);
        var next = configuredCycle().next(view, enabled() && supported());
        if (next != view) selectView(next);
        return cameraType(next);
    }

    private static CameraView currentView(CameraType current) {
        return switch (current) {
            case FIRST_PERSON -> CameraView.FIRST_PERSON;
            case THIRD_PERSON_BACK -> virtual() ? selected : CameraView.THIRD_PERSON_BACK;
            case THIRD_PERSON_FRONT -> CameraView.THIRD_PERSON_FRONT;
        };
    }

    private static ViewCycle configuredCycle() {
        return new ViewCycle(ClientConfigs.CAMERA_VIEW_CYCLE.get());
    }

    private static void selectView(CameraView next) {
        var minecraft = Minecraft.getInstance();
        var camera = minecraft.gameRenderer.getMainCamera();
        boolean back = minecraft.options.getCameraType() == CameraType.THIRD_PERSON_BACK;
        double yaw = back || minecraft.player == null ? camera.getYRot() : minecraft.player.getYRot();
        double pitch = back || minecraft.player == null ? camera.getXRot() : minecraft.player.getXRot();
        clearFrame();
        selected = next;
        if (next == CameraView.SHOULDER_FIXED) ORBIT.initialize(yaw, pitch);
        if (virtual()) minecraft.gui.setOverlayMessage(Component.translatable(
                next == CameraView.SHOULDER_FIXED ? "camera.ninjas_free_play.fixed" : "camera.ninjas_free_play.shoulder"), false);
    }

    private static CameraType cameraType(CameraView next) {
        return switch (next) {
            case FIRST_PERSON -> CameraType.FIRST_PERSON;
            case THIRD_PERSON_FRONT -> CameraType.THIRD_PERSON_FRONT;
            case THIRD_PERSON_BACK, SHOULDER_AUTO, SHOULDER_FIXED -> CameraType.THIRD_PERSON_BACK;
        };
    }

    public static void refreshCycle() {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        var previous = minecraft.options.getCameraType();
        var current = currentView(previous);
        var cycle = configuredCycle();
        if (!supported() && virtual() && cycle.includes(current, enabled())) return;
        var next = cycle.reconcile(current, enabled() && supported());
        if (next == current) return;
        selectView(next);
        var type = cameraType(next);
        minecraft.options.setCameraType(type);
        if (previous.isFirstPerson() != type.isFirstPerson())
            minecraft.gameRenderer.checkEntityPostEffect(type.isFirstPerson() ? minecraft.getCameraEntity() : null);
        minecraft.levelRenderer.needsUpdate();
    }

    private static boolean virtual() {
        return selected == CameraView.SHOULDER_AUTO || selected == CameraView.SHOULDER_FIXED;
    }

    private static boolean available() {
        return virtual() && enabled() && supported()
                && Minecraft.getInstance().options.getCameraType() == CameraType.THIRD_PERSON_BACK;
    }

    public static boolean active() {
        return rendered && available();
    }

    private static boolean fixed() {
        return selected == CameraView.SHOULDER_FIXED && available();
    }

    private static void context(Entity entity, BlockGetter level) {
        if (entity != target || level != world || fixedEye != null && entity.getEyePosition().distanceToSqr(fixedEye) > 64) {
            boolean replacing = target != null;
            RIG.reset();
            fixedFrame = null;
            fixedEye = null;
            if (replacing) ORBIT.reset();
            target = entity;
            world = level;
        }
        if (!ORBIT.initialized()) ORBIT.initialize(entity.getYRot(), entity.getXRot());
    }

    private static CameraSettings settings(boolean fixed) {
        var minecraft = Minecraft.getInstance();
        double aspect = (double) minecraft.getWindow().getWidth() / Math.max(1, minecraft.getWindow().getHeight());
        double nearRadius = Math.min(.5, .05 * Math.tan(Math.toRadians(minecraft.options.fov().get()) / 2) * Math.hypot(aspect, 1) + .02);
        return new CameraSettings(ClientConfigs.CAMERA_DISTANCE.get(), fixed ? ClientConfigs.CAMERA_FIXED_HORIZONTAL_OFFSET.get() : ClientConfigs.CAMERA_HORIZONTAL_OFFSET.get(),
                ClientConfigs.CAMERA_VERTICAL_OFFSET.get(), ClientConfigs.CAMERA_FOLLOW_SECONDS.get(),
                ClientConfigs.CAMERA_TRANSITION_SECONDS.get(), ClientConfigs.CAMERA_RECOVERY_SECONDS.get(),
                Math.max(ClientConfigs.CAMERA_COLLISION_RADIUS.get(), nearRadius / minecraft.player.getScale()));
    }

    /**
     * Runs before vanilla picking so the rendered facing, interaction ray and transmitted rotation agree.
     */
    public static void prepareFixed(float partial) {
        if (!fixed()) return;
        var minecraft = Minecraft.getInstance();
        var entity = minecraft.player;
        context(entity, minecraft.level);
        Vec3 eye = entity.getEyePosition(partial);
        var angles = ORBIT.angles();
        fixedFrame = RIG.sample((entity.tickCount + (double) partial) / 20, vector(eye), angles.yaw(), angles.pitch(),
                vector(minecraft.gameRenderer.getMainCamera().getPosition().subtract(eye)), entity.getScale(), settings(true),
                (from, to, radius) -> CameraCollision.fraction(minecraft.level, entity, from, to, radius));
        fixedEye = eye;
    }

    public static void alignFixed(float partial) {
        if (!fixed()) return;
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        context(player, minecraft.level);
        Vec3 eye = player.getEyePosition(partial);
        var angles = ORBIT.angles();
        var settings = settings(true);
        var composition = fixedFrame != null ? fixedFrame.composition() : new Vector3(settings.distance(), settings.horizontal(), settings.vertical());
        var far = CameraOrbit.virtualTarget(vector(eye), angles, composition, player.getScale(), ClientConfigs.CAMERA_AIM_DISTANCE.get());
        var facing = CameraOrbit.aim(vector(eye), far, angles.yaw());
        float yaw = player.getYRot() + Mth.wrapDegrees((float) facing.yaw() - player.getYRot());
        player.setYRot(yaw);
        player.setXRot((float) facing.pitch());
        player.setYHeadRot(yaw);
    }

    public static boolean turn(LocalPlayer player, double horizontal, double vertical) {
        if (!fixed() || player != Minecraft.getInstance().player) return false;
        context(player, player.level());
        ORBIT.turn(horizontal, vertical);
        alignFixed(1);
        if (player.getVehicle() != null) player.getVehicle().onPassengerTurned(player);
        return true;
    }

    public static void setup(Camera camera, BlockGetter level, Entity entity, boolean detached, boolean mirrored, float partial) {
        var minecraft = Minecraft.getInstance();
        if (camera != minecraft.gameRenderer.getMainCamera()) return;
        rendered = false;
        if (!available() || !detached || mirrored) {
            clearFrame();
            return;
        }
        context(entity, level);
        Vec3 eye = entity.getEyePosition(partial);
        CameraRig.Frame frame;
        if (fixed()) {
            if (fixedFrame == null) prepareFixed(partial);
            frame = fixedFrame;
            reticle = new CameraProjection.Point(0, 0, true);
        } else {
            aimHit = aimTarget(minecraft, entity, partial);
            reticle = CameraProjection.Point.HIDDEN;
            frame = RIG.sample((entity.tickCount + (double) partial) / 20, vector(eye), camera.getYRot(), camera.getXRot(),
                    vector(camera.getPosition().subtract(eye)), minecraft.player.getScale(), settings(false),
                    (from, to, radius) -> CameraCollision.fraction(level, entity, from, to, radius));
        }
        var access = (CameraAccess) camera;
        access.ninjas$setCameraPosition(vec(frame.position()));
        access.ninjas$setCameraRotation((float) frame.angles().yaw(), (float) frame.angles().pitch(), 0);
        rendered = true;
    }

    private static HitResult aimTarget(Minecraft minecraft, Entity entity, float partial) {
        // The original reach-limited result wins; the longer query only locates the visual reticle.
        if (minecraft.hitResult != null && minecraft.hitResult.getType() != HitResult.Type.MISS)
            return minecraft.hitResult;
        return CameraTargeting.pick(entity, entity.getEyePosition(partial), entity.getViewVector(partial), ClientConfigs.CAMERA_AIM_DISTANCE.get());
    }

    public static CameraProjection.Point reticle() {
        return reticle;
    }

    public static void captureProjection(Camera camera, Matrix4f projection) {
        if (!active() || fixed() || aimHit == null) return;
        float[] packed = new Matrix4f(projection).rotate(camera.rotation().conjugate(new Quaternionf())).get(new float[16]);
        double[] matrix = new double[16];
        for (int i = 0; i < 16; i++) matrix[i] = packed[i];
        reticle = CameraProjection.project(vector(aimHit.getLocation().subtract(camera.getPosition())), matrix);
    }

    private static boolean enabled() {
        return ClientConfigs.ENABLE_SHOULDER_CAMERA.get();
    }

    private static boolean supported() {
        var minecraft = Minecraft.getInstance();
        return minecraft.level != null && minecraft.player != null && minecraft.getCameraEntity() == minecraft.player
                && minecraft.player.isAlive() && !minecraft.player.isSleeping() && !minecraft.player.isSpectator();
    }

    private static Vector3 vector(Vec3 value) {
        return new Vector3(value.x, value.y, value.z);
    }

    private static Vec3 vec(Vector3 value) {
        return new Vec3(value.x(), value.y(), value.z());
    }

    private static void clearFrame() {
        RIG.reset();
        ORBIT.reset();
        target = null;
        world = null;
        rendered = false;
        aimHit = null;
        fixedFrame = null;
        fixedEye = null;
        reticle = CameraProjection.Point.HIDDEN;
    }

    public static void tick() {
        refreshCycle();
        if (!available()) clearFrame();
        else alignFixed(1);
    }

    public static void logout() {
        selected = CameraView.THIRD_PERSON_BACK;
        clearFrame();
    }
}
