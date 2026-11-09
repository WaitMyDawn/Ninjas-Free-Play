package yagen.waitmydawn.ninjas_free_play.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Quaternionf;
import yagen.waitmydawn.ninjas_free_play.animation.core.*;

public final class PlayerPoseAdapter {
    private PlayerPoseAdapter() {
    }

    public static void apply(HumanoidModel<?> model, PlayerAnimationContext context) {
        AnimationPose pose = context.sample.pose();
        double weight = context.sample.weight();
        var frames = HumanoidRig.evaluateBody(pose, context.slim);
        apply(model.body, frames.get(Bone.BODY), pose.bone(Bone.BODY), weight, 0, Bone.BODY, model, context);
        apply(model.head, frames.get(Bone.HEAD), pose.bone(Bone.HEAD), weight, 0, Bone.HEAD, model, context);
        apply(model.leftArm, frames.get(Bone.LEFT_ARM), pose.bone(Bone.LEFT_ARM), weight, HumanoidRig.bendCenter(Bone.LEFT_ARM), Bone.LEFT_ARM, model, context);
        apply(model.rightArm, frames.get(Bone.RIGHT_ARM), pose.bone(Bone.RIGHT_ARM), weight, HumanoidRig.bendCenter(Bone.RIGHT_ARM), Bone.RIGHT_ARM, model, context);
        apply(model.leftLeg, frames.get(Bone.LEFT_LEG), pose.bone(Bone.LEFT_LEG), weight, HumanoidRig.bendCenter(Bone.LEFT_LEG), Bone.LEFT_LEG, model, context);
        apply(model.rightLeg, frames.get(Bone.RIGHT_LEG), pose.bone(Bone.RIGHT_LEG), weight, HumanoidRig.bendCenter(Bone.RIGHT_LEG), Bone.RIGHT_LEG, model, context);
    }

    private static void apply(ModelPart part, HumanoidRig.Frame frame, BonePose local, double weight, double center,
                              Bone bone, HumanoidModel<?> model, PlayerAnimationContext context) {
        Rotation target = frame.rotation();
        BonePose original = new BonePose(new Vector3(part.x, part.y, part.z), new Vector3(part.xRot, part.yRot, part.zRot), 0);
        BonePose result = original.blend(new BonePose(frame.position(), target.toEuler(), local.bend()), weight);
        if (bone == Bone.LEFT_ARM || bone == Bone.RIGHT_ARM) {
            if (context.armUseWeight > 0) {
                Rotation equipment = context.rootLocalRotation.inverse().multiply(Rotation.fromEuler(original.rotation()));
                double useWeight = context.armUseWeight;
                double bend = 0;
                if (context.consumptionWeight > 0) {
                    if (bone == context.consumingArm) {
                        Rotation head = Rotation.fromEuler(new Vector3(model.head.xRot, model.head.yRot, model.head.zRot));
                        var food = ConsumptionArmPose.solve(context.sample.pose(), bone, head,
                                context.slim, context.consumptionMotion);
                        equipment = slerp(equipment, Rotation.fromEuler(food.rotation()), context.consumptionWeight);
                        bend = food.bend() * context.consumptionWeight;
                    } else useWeight *= 1 - context.consumptionWeight;
                }
                Rotation rotation = slerp(Rotation.fromEuler(result.rotation()), equipment, useWeight);
                result = new BonePose(result.position(), rotation.toEuler(), result.bend() + (bend - result.bend()) * useWeight);
            }
        }
        if (bone == Bone.HEAD && context.retainHeadAim) {
            Rotation current = Rotation.fromEuler(result.rotation());
            Vector3 desired = context.rootWorldRotation.inverse().apply(context.worldLook);
            Rotation aim = Rotation.between(current.apply(new Vector3(0, 0, -1)), desired).multiply(current);
            result = new BonePose(result.position(), aim.toEuler(), result.bend());
        }
        part.setPos((float) result.position().x(), (float) result.position().y(), (float) result.position().z());
        part.setRotation((float) result.rotation().x(), (float) result.rotation().y(), (float) result.rotation().z());
        ((BendablePart) (Object) part).ninjas$setBend(result.bend(), center);
    }

    private static Rotation slerp(Rotation from, Rotation to, double weight) {
        var result = new Quaternionf((float) from.x(), (float) from.y(), (float) from.z(), (float) from.w());
        result.slerp(new Quaternionf((float) to.x(), (float) to.y(), (float) to.z(), (float) to.w()), (float) weight);
        return new Rotation(result.x, result.y, result.z, result.w);
    }

    public static void applyRoot(PlayerAnimationContext context, PoseStack poseStack) {
        double scale = PlayerModelSpace.RENDER_SCALE * context.scale;
        Vector3 grounding = context.baseRotation.inverse().apply(context.groundingOffset).scale(1 / scale);
        poseStack.translate(grounding.x(), grounding.y(), grounding.z());
        poseStack.scale(1, (float) context.heightScale, 1);
        BonePose root = context.sample.weightedRoot();
        Vector3 position = root.position();
        Rotation rotation = context.rootLocalRotation;
        // Conjugate around the model-origin translation that LivingEntityRenderer applies after scale().
        poseStack.translate(position.x() / 16, position.y() / 16 - PlayerModelSpace.ORIGIN + PlayerModelSpace.ROOT_PIVOT, position.z() / 16);
        poseStack.rotate(new Quaternionf((float) rotation.x(), (float) rotation.y(), (float) rotation.z(), (float) rotation.w()));
        poseStack.translate(0, PlayerModelSpace.ORIGIN - PlayerModelSpace.ROOT_PIVOT, 0);
    }
}
