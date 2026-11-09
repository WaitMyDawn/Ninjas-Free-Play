package yagen.waitmydawn.ninjas_free_play.mixin.client;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingEntityInputAccessor {
    @Accessor("jumping")
    void ninjas$setJumping(boolean jumping);
}
