package yagen.waitmydawn.ninjas_free_play.particle;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import yagen.waitmydawn.ninjas_free_play.NinjasFreePlay;

public final class NParticles {
    public static final SimpleParticleType WALL_SPARK = FabricParticleTypes.simple();

    private NParticles() {}

    public static void register() {
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, NinjasFreePlay.id("wall_spark"), WALL_SPARK);
    }
}
