package yagen.waitmydawn.ninjas_free_play.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yagen.waitmydawn.ninjas_free_play.render.BendablePart;
import yagen.waitmydawn.ninjas_free_play.render.LimbMeshRenderer;

import java.util.List;

@Mixin(ModelPart.class)
public abstract class ModelPartMixin implements BendablePart {
    @Shadow
    @Final
    private List<ModelPart.Cube> cubes;
    @Unique
    private double ninjas$bend;
    @Unique
    private double ninjas$center;
    @Unique
    private LimbMeshRenderer ninjas$mesh;

    @Override
    public double ninjas$getBend() {
        return ninjas$bend;
    }

    @Override
    public double ninjas$getBendCenter() {
        return ninjas$center;
    }

    @Override
    public void ninjas$setBend(double angle, double center) {
        ninjas$bend = angle;
        ninjas$center = center;
    }

    @Override
    public void ninjas$copyBend(BendablePart source) {
        ninjas$setBend(source.ninjas$getBend(), source.ninjas$getBendCenter());
    }

    @Unique
    private LimbMeshRenderer ninjas$mesh() {
        if (ninjas$mesh == null) ninjas$mesh = new LimbMeshRenderer(cubes);
        return ninjas$mesh;
    }

    @Override
    public void ninjas$translateToHand(PoseStack poseStack) {
        if (Math.abs(ninjas$bend) > 1e-7) ninjas$mesh().translateToHand(poseStack, ninjas$bend, ninjas$center);
    }

    @Inject(method = "resetPose", at = @At("TAIL"))
    private void ninjas$resetJoint(CallbackInfo ci) {
        ninjas$setBend(0, 0);
    }

    @Inject(method = "compile", at = @At("HEAD"), cancellable = true)
    private void ninjas$renderBentMesh(PoseStack.Pose pose, VertexConsumer buffer, int light, int overlay, int color, CallbackInfo ci) {
        if (Math.abs(ninjas$bend) > 1e-7) {
            ninjas$mesh().render(pose, buffer, light, overlay, color, ninjas$bend, ninjas$center);
            ci.cancel();
        }
    }
}
