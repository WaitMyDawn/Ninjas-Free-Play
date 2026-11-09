package yagen.waitmydawn.ninjas_free_play.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import yagen.waitmydawn.ninjas_free_play.api.movement.ContactDamageContext;

@Mixin(LivingEntity.class)
public abstract class ContactCollisionMixin {
    @WrapOperation(method = "doPush", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;push(Lnet/minecraft/world/entity/Entity;)V"))
    private void ninjas$collisionDamage(Entity other, Entity self, Operation<Void> original) {
        try (var scope = ContactDamageContext.collision(self, other)) {
            original.call(other, self);
        }
    }
}
