package yagen.waitmydawn.ninjas_free_play.mixin.client;

import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import yagen.waitmydawn.ninjas_free_play.render.AvatarAnimationState;
import yagen.waitmydawn.ninjas_free_play.render.PlayerAnimationContext;

@Mixin(AvatarRenderState.class)
public abstract class AvatarRenderStateMixin implements AvatarAnimationState {
    @Unique
    private PlayerAnimationContext ninjas$animation;
    @Unique
    private boolean ninjas$compact;

    @Override
    public PlayerAnimationContext ninjas$animation() {
        return ninjas$animation;
    }

    @Override
    public void ninjas$setAnimation(PlayerAnimationContext animation, boolean compact) {
        ninjas$animation = animation;
        ninjas$compact = compact;
    }

    @Override
    public boolean ninjas$compact() {
        return ninjas$compact;
    }
}
