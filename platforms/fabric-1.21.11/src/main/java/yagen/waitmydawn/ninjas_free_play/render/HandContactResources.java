package yagen.waitmydawn.ninjas_free_play.render;

import com.mojang.serialization.Codec;
import net.minecraft.resources.FileToIdConverter;
import com.google.gson.JsonElement;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import yagen.waitmydawn.ninjas_free_play.NinjasFreePlay;
import yagen.waitmydawn.ninjas_free_play.animation.core.HandContactProfile;
import yagen.waitmydawn.ninjas_free_play.animation.resource.HandContactCodec;

import java.util.HashMap;
import java.util.Map;

public final class HandContactResources extends SimpleJsonResourceReloadListener<JsonElement> {
    public static final HandContactResources INSTANCE = new HandContactResources();
    private Map<Identifier, HandContactProfile> profiles = Map.of();

    private HandContactResources() {
        super(Codec.PASSTHROUGH.xmap(value -> value.convert(com.mojang.serialization.JsonOps.INSTANCE).getValue(),
                json -> new com.mojang.serialization.Dynamic<>(com.mojang.serialization.JsonOps.INSTANCE, json)),
                FileToIdConverter.json("ninja_contacts"));
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> resources, ResourceManager manager, ProfilerFiller profiler) {
        var loaded = new HashMap<Identifier, HandContactProfile>();
        resources.forEach((id, json) -> {
            try {
                loaded.put(id, HandContactCodec.read(json));
            } catch (RuntimeException exception) {
                NinjasFreePlay.LOGGER.error("Invalid hand contacts {}: {}", id, exception.getMessage());
            }
        });
        profiles = Map.copyOf(loaded);
        MovementAnimations.reload();
        HandGrounding.clear();
        NinjasFreePlay.LOGGER.info("Loaded {} hand contact profiles", profiles.size());
    }

    public HandContactProfile get(Identifier id) {
        return id == null ? HandContactProfile.NONE : profiles.getOrDefault(id, HandContactProfile.NONE);
    }
}
