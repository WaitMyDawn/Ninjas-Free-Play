package yagen.waitmydawn.ninjas_free_play.render;

import net.minecraft.client.player.AbstractClientPlayer;
import yagen.waitmydawn.ninjas_free_play.animation.core.*;
import yagen.waitmydawn.ninjas_free_play.core.JumpKind;
import yagen.waitmydawn.ninjas_free_play.core.WallDashKind;
import yagen.waitmydawn.ninjas_free_play.player.MovementPhysics;
import yagen.waitmydawn.ninjas_free_play.player.ParkourPhysics;

import java.util.EnumMap;

/**
 * Hands and the pushing foot contact the actual launch wall while the torso retains view-facing yaw.
 */
public final class WallDashPoseAdapter {
    private WallDashPoseAdapter() {
    }

    public static AnimationPlayer.Sample apply(AbstractClientPlayer entity, AnimationPlayer.Sample sample,
                                               Rotation root, Rotation base, double height, float partial) {
        var state = MovementPhysics.controller(entity).snapshot(entity.tickCount);
        if (state.action() != JumpKind.WALL || state.actionAge() >= 7 || AnimationPreviewController.isActive(entity, partial)
                || !MovementAnimations.wallDashPose(entity, partial)) return sample;
        var plan = state.parkour().wallDash();
        var wall = ParkourPhysics.frame(entity, true).wall();
        if (!wall.present() || wall.x() != plan.normalX() || wall.z() != plan.normalZ()) return sample;
        double seconds = (state.actionAge() + partial) / 20;
        double weight = smooth(seconds / .05) * (1 - smooth((seconds - .12) / .1)) * sample.weight();
        if (weight == 0) return sample;
        Vector3 normal = new Vector3(wall.x(), 0, wall.z());
        double plane = -(entity.getBbWidth() / 2 + wall.distance());
        var frames = HumanoidRig.evaluateBody(sample.pose(), false);
        var clip = AnimationResources.INSTANCE.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                yagen.waitmydawn.ninjas_free_play.NinjasFreePlay.MODID, plan.animation()));
        var authored = clip == null ? sample.pose() : clip.sample(seconds);
        var authoredFrames = HumanoidRig.evaluateBody(authored, false);
        Rotation wallFacing = LaunchOrientation.entityModelRotation(Math.atan2(wall.x(), -wall.z()));
        Rotation worldRoot = base.multiply(root);
        EnumMap<Bone, BonePose> bones = new EnumMap<>(Bone.class);
        for (Bone bone : Bone.values()) bones.put(bone, sample.pose().bone(bone));
        Bone support = plan.kind() == WallDashKind.RIGHT ? Bone.RIGHT_LEG : Bone.LEFT_LEG;
        for (Bone bone : new Bone[]{Bone.LEFT_ARM, Bone.RIGHT_ARM, support}) {
            BonePose part = bones.get(bone);
            var frame = frames.get(bone);
            Vector3 end = bone == support ? new Vector3(0, 12, 0) : HumanoidRig.palmAnchor(bone, false);
            end = new LimbDeformer(part.bend(), HumanoidRig.bendCenter(bone), 10).position(end);
            Vector3 origin = PlayerModelSpace.offset(frame.position(), sample.weightedRoot().position(), root, base, height, entity.getScale());
            Vector3 tip = PlayerModelSpace.offset(frame.transform(end), sample.weightedRoot().position(), root, base, height, entity.getScale());
            Vector3 current = tip.add(origin.scale(-1));
            double distance = plane - origin.x() * normal.x() - origin.z() * normal.z();
            var referenceBone = authored.bone(bone);
            Vector3 referenceEnd = new LimbDeformer(referenceBone.bend(), HumanoidRig.bendCenter(bone), 10).position(
                    bone == support ? new Vector3(0, 12, 0) : HumanoidRig.palmAnchor(bone, false));
            Vector3 reference = PlayerModelSpace.offset(authoredFrames.get(bone).transform(referenceEnd), authored.bone(Bone.ROOT).position(),
                    Rotation.fromEuler(authored.bone(Bone.ROOT).rotation()), wallFacing, height, entity.getScale());
            Vector3 constrained = LimbPlaneConstraint.endpoint(current, normal, distance, reference.add(origin.scale(-1)));
            Rotation correction = Rotation.fromRotationVector(Rotation.between(current, constrained).rotationVector().scale(weight));
            Rotation parent = bone == support ? worldRoot : worldRoot.multiply(frames.get(Bone.BODY).rotation());
            Rotation target = parent.inverse().multiply(correction).multiply(worldRoot).multiply(frame.rotation());
            bones.put(bone, new BonePose(part.position(), target.toEuler(), part.bend()));
        }
        return new AnimationPlayer.Sample(new AnimationPose(bones), sample.weight());
    }

    private static double smooth(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }
}
