package yagen.waitmydawn.ninjas_free_play.render;

import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import yagen.waitmydawn.ninjas_free_play.NinjasFreePlay;
import yagen.waitmydawn.ninjas_free_play.animation.core.AnimationClip;
import yagen.waitmydawn.ninjas_free_play.animation.resource.AnimationJsonCodec;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class AnimationResources extends SimpleJsonResourceReloadListener implements IdentifiableResourceReloadListener {
    public static final AnimationResources INSTANCE = new AnimationResources();
    private Map<ResourceLocation, AnimationClip> clips = Map.of();

    private AnimationResources() {
        super(new Gson(), "ninja_animations");
    }

    @Override
    public ResourceLocation getFabricId() { return NinjasFreePlay.id("animations"); }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager manager, ProfilerFiller profiler) {
        HashMap<ResourceLocation, AnimationClip> loaded = new HashMap<>();
        resources.forEach((id, json) -> {
            try {
                loaded.put(id, AnimationJsonCodec.read(json));
            } catch (RuntimeException exception) {
                NinjasFreePlay.LOGGER.error("Invalid player animation {}: {}", id, exception.getMessage());
            }
        });
        AnimationPreviewController.clear();
        MovementAnimations.reload();
        HandGrounding.clear();
        clips = Map.copyOf(loaded);
        NinjasFreePlay.LOGGER.info("Loaded {} player animation clips", clips.size());
    }

    public AnimationClip get(ResourceLocation id) {
        return clips.get(id);
    }

    public Set<ResourceLocation> ids() {
        return clips.keySet();
    }
}
