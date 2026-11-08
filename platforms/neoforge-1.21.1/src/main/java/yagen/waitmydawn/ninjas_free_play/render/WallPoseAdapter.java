package yagen.waitmydawn.ninjas_free_play.render;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.resources.ResourceLocation;
import yagen.waitmydawn.ninjas_free_play.NinjasFreePlay;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.PlayerModelPart;
import yagen.waitmydawn.ninjas_free_play.animation.core.*;
import yagen.waitmydawn.ninjas_free_play.core.ParkourMode;
import yagen.waitmydawn.ninjas_free_play.player.MovementPhysics;

import java.util.EnumMap;
import java.util.Map;
import java.util.WeakHashMap;

import yagen.waitmydawn.ninjas_free_play.config.ClientConfigs;

public final class WallPoseAdapter {
    private static final Map<AbstractClientPlayer, VectorSmoother> OFFSETS = new WeakHashMap<>();
    private static final Map<AbstractClientPlayer, PoseCorrectionSmoother> CONTACTS = new WeakHashMap<>();

    public record Result(AnimationPlayer.Sample sample, Vector3 offset) {
    }

    private WallPoseAdapter() {
    }

    public static void clear() {
        OFFSETS.clear();
        CONTACTS.clear();
    }

    public static Result apply(AbstractClientPlayer entity, AnimationPlayer.Sample sample, Rotation rootRotation, Rotation base, double height, float partial) {
        Result target = target(entity, sample, rootRotation, base, height, partial);
        var smoother = CONTACTS.computeIfAbsent(entity, key -> new PoseCorrectionSmoother());
        double duration = ClientConfigs.ANIMATION_TRANSITION_SECONDS.get();
        if (MovementPhysics.controller(entity).parkour().mode() != ParkourMode.WALL_LATCH)
            duration = Math.min(.06, duration);
        var pose = smoother.sample((entity.tickCount + (double) partial) / 20, sample.pose(), target.sample.pose(), duration);
        return new Result(new AnimationPlayer.Sample(pose, sample.weight()), target.offset());
    }

    private static Result target(AbstractClientPlayer entity, AnimationPlayer.Sample sample, Rotation rootRotation, Rotation base, double height, float partial) {
        var state = MovementPhysics.controller(entity).snapshot(entity.tickCount).parkour();
        double now = (entity.tickCount + (double) partial) / 20;
        var smoother = OFFSETS.computeIfAbsent(entity, key -> new VectorSmoother());
        double duration = ClientConfigs.ANIMATION_TRANSITION_SECONDS.get();
        if (state.mode() != ParkourMode.WALL_LATCH || AnimationPreviewController.isActive(entity, partial))
            return new Result(WallDashPoseAdapter.apply(entity, sample, rootRotation, base, height, partial), smoother.sample(now, Vector3.ZERO, duration));
        var wall = state.wall();
        double yaw = Math.toRadians(Mth.rotLerp(partial, entity.yRotO, entity.getYRot()));
        double wallYaw = Math.atan2(wall.x(), -wall.z());
        double pitch = Math.tanh(Math.toRadians(Mth.lerp(partial, entity.xRotO, entity.getXRot()))) * .12;
        double phase = (state.age() + partial) / 20;
        AnimationPose front = pose("wall_latch", phase, sample.pose());
        AnimationPose pose = WallLatchBlend.adapt(sample.pose(), front,
                pose("wall_latch_left", phase, front), pose("wall_latch_back", phase, front),
                pose("wall_latch_right", phase, front), yaw - wallYaw);
        var bones = new EnumMap<Bone, BonePose>(Bone.class);
        for (Bone bone : Bone.values()) bones.put(bone, pose.bone(bone));
        BonePose body = bones.get(Bone.BODY);
        bones.put(Bone.BODY, new BonePose(body.position(), body.rotation().add(new Vector3(pitch, 0, 0)), body.bend()));
        pose = new AnimationPose(bones);
        var root = sample.weightedRoot();
        boolean boots = !entity.getItemBySlot(EquipmentSlot.FEET).isEmpty();
        double leftInflation = boots ? 1 : entity.isModelPartShown(PlayerModelPart.LEFT_PANTS_LEG) ? .25 : 0;
        double rightInflation = boots ? 1 : entity.isModelPartShown(PlayerModelPart.RIGHT_PANTS_LEG) ? .25 : 0;
        Vector3 normal = new Vector3(wall.x(), 0, wall.z());
        Vector3 localNormal = rootRotation.inverse().apply(base.inverse().apply(normal));
        Vector3 left = foot(pose, Bone.LEFT_LEG, root, rootRotation, base, height, entity.getScale(), localNormal, leftInflation);
        double plane = -(entity.getBbWidth() / 2 + wall.distance());
        double correction = Math.max(-0.5 * entity.getScale(), Math.min(0.5 * entity.getScale(), plane - left.x() * wall.x() - left.z() * wall.z()));
        Vector3 offset = smoother.sample(now, new Vector3(wall.x() * correction, 0, wall.z() * correction).scale(sample.weight()), duration);
        Vector3 origin = PlayerModelSpace.offset(Vector3.ZERO, root.position(), rootRotation, base, height, entity.getScale()).add(offset);
        double localPlane = (plane - origin.x() * wall.x() - origin.z() * wall.z()) * 16 / (PlayerModelSpace.RENDER_SCALE * entity.getScale());
        pose = FootSupport.fit(pose, Bone.RIGHT_LEG, localNormal, localPlane, rightInflation);
        return new Result(new AnimationPlayer.Sample(pose, sample.weight()), offset);
    }

    private static AnimationPose pose(String name, double time, AnimationPose fallback) {
        var clip = AnimationResources.INSTANCE.get(ResourceLocation.fromNamespaceAndPath(NinjasFreePlay.MODID, name));
        return clip == null ? fallback : clip.sample(time);
    }

    private static Vector3 foot(AnimationPose pose, Bone bone, BonePose root, Rotation rotation, Rotation base, double height, double scale, Vector3 normal, double inflation) {
        return PlayerModelSpace.offset(FootSupport.point(pose, bone, normal, inflation), root.position(), rotation, base, height, scale);
    }
}
