package yagen.waitmydawn.ninjas_free_play.render;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import yagen.waitmydawn.ninjas_free_play.NinjasFreePlay;
import yagen.waitmydawn.ninjas_free_play.animation.core.HandContactProfile;
import yagen.waitmydawn.ninjas_free_play.animation.resource.HandContactCodec;

import java.util.HashMap;
import java.util.Map;

public final class HandContactResources extends SimpleJsonResourceReloadListener {
    public static final HandContactResources INSTANCE = new HandContactResources();
    private Map<ResourceLocation, HandContactProfile> profiles = Map.of();

    private HandContactResources() {
        super(new Gson(), "ninja_contacts");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager manager, ProfilerFiller profiler) {
        var loaded = new HashMap<ResourceLocation, HandContactProfile>();
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

    public HandContactProfile get(ResourceLocation id) {
        return id == null ? HandContactProfile.NONE : profiles.getOrDefault(id, HandContactProfile.NONE);
    }
}
