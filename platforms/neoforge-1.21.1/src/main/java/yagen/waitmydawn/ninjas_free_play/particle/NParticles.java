package yagen.waitmydawn.ninjas_free_play.particle;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import yagen.waitmydawn.ninjas_free_play.NinjasFreePlay;

public final class NParticles {
    private static final DeferredRegister<ParticleType<?>> TYPES = DeferredRegister.create(Registries.PARTICLE_TYPE, NinjasFreePlay.MODID);
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> WALL_SPARK = TYPES.register("wall_spark", () -> new SimpleParticleType(false));

    private NParticles() {
    }

    public static void register(IEventBus bus) {
        TYPES.register(bus);
    }
}
