package yagen.waitmydawn.ninjas_free_play.render;

import com.mojang.blaze3d.vertex.PoseStack;

public interface BendablePart {
    double ninjas$getBend();

    double ninjas$getBendCenter();

    void ninjas$setBend(double angle, double center);

    void ninjas$copyBend(BendablePart source);

    void ninjas$translateToHand(PoseStack poseStack);
}
