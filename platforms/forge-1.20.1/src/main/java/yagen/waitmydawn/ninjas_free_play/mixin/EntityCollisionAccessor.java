package yagen.waitmydawn.ninjas_free_play.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import yagen.waitmydawn.ninjas_free_play.player.MovementCollisionAccess;

@Mixin(Entity.class)
public interface EntityCollisionAccessor extends MovementCollisionAccess {
    @Invoker("getBlockPosBelowThatAffectsMyMovement")
    BlockPos ninjas$frictionBlock();

    @Override
    @Invoker("collide")
    Vec3 ninjas$resolveMovement(Vec3 movement);
}
