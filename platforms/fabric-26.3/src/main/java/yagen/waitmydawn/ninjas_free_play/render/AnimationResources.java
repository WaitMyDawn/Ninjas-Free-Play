package yagen.waitmydawn.ninjas_free_play.render;

import com.mojang.serialization.Codec;
import net.minecraft.resources.FileToIdConverter;
import com.google.gson.JsonElement;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import yagen.waitmydawn.ninjas_free_play.NinjasFreePlay;
import yagen.waitmydawn.ninjas_free_play.animation.core.AnimationClip;
import yagen.waitmydawn.ninjas_free_play.animation.resource.AnimationJsonCodec;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class AnimationResources extends SimpleJsonResourceReloadListener<JsonElement> {
    public static final AnimationResources INSTANCE = new AnimationResources();
    private Map<Identifier, AnimationClip> clips = Map.of();

    private AnimationResources() {
        super(Codec.PASSTHROUGH.xmap(value -> value.convert(com.mojang.serialization.JsonOps.INSTANCE).getValue(),
                json -> new com.mojang.serialization.Dynamic<>(com.mojang.serialization.JsonOps.INSTANCE, json)),
                FileToIdConverter.json("ninja_animations"));
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> resources, ResourceManager manager, ProfilerFiller profiler) {
        HashMap<Identifier, AnimationClip> loaded = new HashMap<>();
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

    public AnimationClip get(Identifier id) {
        return clips.get(id);
    }

    public Set<Identifier> ids() {
        return clips.keySet();
    }
}
