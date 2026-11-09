package yagen.waitmydawn.ninjas_free_play.render;

public interface AvatarAnimationState {
    PlayerAnimationContext ninjas$animation();

    void ninjas$setAnimation(PlayerAnimationContext animation, boolean compact);

    boolean ninjas$compact();
}
