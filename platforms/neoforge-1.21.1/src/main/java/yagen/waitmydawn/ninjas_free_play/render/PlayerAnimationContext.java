package yagen.waitmydawn.ninjas_free_play.render;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.player.AbstractClientPlayer;
import yagen.waitmydawn.ninjas_free_play.animation.core.AnimationPlayer;
import yagen.waitmydawn.ninjas_free_play.animation.core.Rotation;
import yagen.waitmydawn.ninjas_free_play.animation.core.Vector3;

import java.util.ArrayList;
import java.util.List;
import java.util.EnumMap;

import org.joml.Quaternionf;
import yagen.waitmydawn.ninjas_free_play.animation.core.*;

public final class PlayerAnimationContext implements AutoCloseable {
    private record Snapshot(ModelPart part, PartPose pose, float xScale, float yScale, float zScale, double bend,
                            double center) {
        Snapshot(ModelPart part) {
            this(part, part.storePose(), part.xScale, part.yScale, part.zScale, ((BendablePart) (Object) part).ninjas$getBend(),
                    ((BendablePart) (Object) part).ninjas$getBendCenter());
        }

        void restore() {
            part.loadPose(pose);
            part.xScale = xScale;
            part.yScale = yScale;
            part.zScale = zScale;
            ((BendablePart) (Object) part).ninjas$setBend(bend, center);
        }
    }

    private static final ThreadLocal<PlayerAnimationContext> CURRENT = new ThreadLocal<>();
    private final PlayerAnimationContext previous;
    private final List<Snapshot> snapshots = new ArrayList<>();
    public final AbstractClientPlayer entity;
    public final PlayerModel<AbstractClientPlayer> model;
    public final AnimationPlayer.Sample sample;
    public final Rotation rootLocalRotation;
    public final Rotation rootWorldRotation;
    public final Vector3 worldLook;
    public final double heightScale;
    public final boolean retainHeadAim;
    public final Vector3 groundingOffset;
    public final Rotation baseRotation;
    public final double armUseWeight;
    public final double consumptionWeight;
    public final Bone consumingArm;
    public final double consumptionMotion;

    public PlayerAnimationContext(AbstractClientPlayer entity, PlayerModel<AbstractClientPlayer> model, float partialTick) {
        this.entity = entity;
        this.model = model;
        var rawSample = PlayerAnimations.sample(entity, partialTick);
        armUseWeight = AnimationPreviewController.isActive(entity, partialTick) ? 0 : MovementAnimations.armUseWeight(entity, partialTick);
        consumptionWeight = AnimationPreviewController.isActive(entity, partialTick) ? 0 : MovementAnimations.consumptionWeight(entity, partialTick);
        consumingArm = MovementAnimations.consumingArm(entity, partialTick);
        consumptionMotion = .3 * Math.sin((entity.tickCount + partialTick) * 1.5);
        heightScale = PlayerAnimations.heightScale(entity, partialTick);
        retainHeadAim = PlayerAnimations.retainHeadAim(entity, partialTick);
        Rotation inspection = Rotation.fromEuler(new Vector3(0, PlayerAnimations.viewRadians(entity, partialTick), 0));
        rootLocalRotation = inspection
                .multiply(PlayerAnimations.facing(entity, partialTick))
                .multiply(Rotation.fromEuler(rawSample.weightedRoot().rotation()));
        Rotation base = AnimationPreviewController.entityFacing(entity, (entity.tickCount + (double) partialTick) / 20);
        baseRotation = base;
        rootWorldRotation = base.multiply(rootLocalRotation);
        var planted = WallPoseAdapter.apply(entity, rawSample, rootLocalRotation, base, heightScale, partialTick);
        sample = equipmentBody(planted.sample(), rootLocalRotation, AnimationPreviewController.isActive(entity, partialTick) ? 0 : MovementAnimations.bodyUseWeight(entity, partialTick));
        groundingOffset = HandGrounding.offset(this, partialTick, base).add(planted.offset());
        worldLook = base.apply(inspection.apply(base.inverse().apply(PlayerAnimations.look(entity, partialTick))));
        this.previous = CURRENT.get();
        for (ModelPart part : List.of(model.head, model.hat, model.body, model.leftArm, model.rightArm, model.leftLeg,
                model.rightLeg, model.leftSleeve, model.rightSleeve, model.leftPants, model.rightPants, model.jacket)) {
            snapshots.add(new Snapshot(part));
        }
        CURRENT.set(this);
    }

    public static PlayerAnimationContext current() {
        return CURRENT.get();
    }

    private static AnimationPlayer.Sample equipmentBody(AnimationPlayer.Sample sample, Rotation root, double weight) {
        if (weight <= 0) return sample;
        BonePose body = sample.pose().bone(Bone.BODY);
        Rotation source = Rotation.fromEuler(body.rotation());
        Rotation target = root.inverse().multiply(BodyAim.align(root.multiply(source), new Vector3(0, 0, -1)));
        Quaternionf rotation = new Quaternionf((float) source.x(), (float) source.y(), (float) source.z(), (float) source.w());
        rotation.slerp(new Quaternionf((float) target.x(), (float) target.y(), (float) target.z(), (float) target.w()), (float) weight);
        EnumMap<Bone, BonePose> bones = new EnumMap<>(Bone.class);
        for (Bone bone : Bone.values()) bones.put(bone, sample.pose().bone(bone));
        bones.put(Bone.BODY, new BonePose(body.position(), new Rotation(rotation.x, rotation.y, rotation.z, rotation.w).toEuler(), body.bend()));
        return new AnimationPlayer.Sample(new AnimationPose(bones), sample.weight());
    }

    @Override
    public void close() {
        try {
            snapshots.forEach(Snapshot::restore);
        } finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }
}
