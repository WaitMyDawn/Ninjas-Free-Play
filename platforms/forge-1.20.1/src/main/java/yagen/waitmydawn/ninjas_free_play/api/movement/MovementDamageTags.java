package yagen.waitmydawn.ninjas_free_play.api.movement;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import yagen.waitmydawn.ninjas_free_play.NinjasFreePlay;

public final class MovementDamageTags {
    public static final TagKey<DamageType> CONTACT_MELEE = TagKey.create(Registries.DAMAGE_TYPE,
            new ResourceLocation(NinjasFreePlay.MODID, "contact_melee"));

    private MovementDamageTags() {
    }
}
