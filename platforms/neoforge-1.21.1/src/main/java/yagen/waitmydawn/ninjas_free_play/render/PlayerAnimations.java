package yagen.waitmydawn.ninjas_free_play.render;

import net.minecraft.client.player.AbstractClientPlayer;
import yagen.waitmydawn.ninjas_free_play.animation.core.AnimationPlayer;
import yagen.waitmydawn.ninjas_free_play.animation.core.Rotation;
import yagen.waitmydawn.ninjas_free_play.animation.core.Vector3;
import yagen.waitmydawn.ninjas_free_play.animation.core.HandContactProfile;
import yagen.waitmydawn.ninjas_free_play.core.RollKind;

/**
 * Debug preview overrides the current player's rendering without changing movement qualification.
 */
public final class PlayerAnimations {
    private PlayerAnimations() {
    }

    public static boolean isActive(AbstractClientPlayer entity, float partialTick) {
        return AnimationPreviewController.isActive(entity, partialTick) || MovementAnimations.isActive(entity, partialTick);
    }

    public static AnimationPlayer.Sample sample(AbstractClientPlayer entity, float partialTick) {
        return AnimationPreviewController.isActive(entity, partialTick)
                ? AnimationPreviewController.sample(entity, partialTick) : MovementAnimations.sample(entity, partialTick);
    }

    public static Rotation facing(AbstractClientPlayer entity, float partialTick) {
        return AnimationPreviewController.isActive(entity, partialTick)
                ? AnimationPreviewController.facing(entity, partialTick) : MovementAnimations.facing(entity, partialTick);
    }

    public static double viewRadians(AbstractClientPlayer entity, float partialTick) {
        return AnimationPreviewController.isActive(entity, partialTick) ? AnimationPreviewController.viewRadians() : 0;
    }

    public static Vector3 look(AbstractClientPlayer entity, float partialTick) {
        if (AnimationPreviewController.isActive(entity, partialTick))
            return AnimationPreviewController.look(entity, partialTick);
        var direction = entity.getViewVector(partialTick);
        return new Vector3(direction.x, direction.y, direction.z);
    }

    public static double heightScale(AbstractClientPlayer entity, float partialTick) {
        return AnimationPreviewController.isActive(entity, partialTick) ? 1 : MovementAnimations.heightScale(entity, partialTick);
    }

    public static boolean retainHeadAim(AbstractClientPlayer entity, float partialTick) {
        if (!AnimationPreviewController.isActive(entity, partialTick))
            return !MovementAnimations.rollPose(entity, partialTick);
        for (RollKind kind : RollKind.values())
            if (AnimationPreviewController.isClip(entity, RollAnimations.clip(kind))) return false;
        return true;
    }

    public static HandContactProfile.Weights contacts(AbstractClientPlayer entity, float partialTick) {
        if (!AnimationPreviewController.isActive(entity, partialTick))
            return MovementAnimations.contacts(entity, partialTick);
        for (RollKind kind : RollKind.values()) {
            var id = RollAnimations.clip(kind);
            if (AnimationPreviewController.isClip(entity, id)) return HandContactResources.INSTANCE.get(id)
                    .sample(AnimationPreviewController.phase(entity, partialTick));
        }
        return new HandContactProfile.Weights(0, 0);
    }
}
