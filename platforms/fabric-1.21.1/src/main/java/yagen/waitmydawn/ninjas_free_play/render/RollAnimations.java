package yagen.waitmydawn.ninjas_free_play.render;

import net.minecraft.resources.ResourceLocation;
import yagen.waitmydawn.ninjas_free_play.NinjasFreePlay;
import yagen.waitmydawn.ninjas_free_play.core.RollKind;

import java.util.EnumMap;
import java.util.Map;

public final class RollAnimations {
    private static final Map<RollKind, ResourceLocation> CLIPS = new EnumMap<>(RollKind.class);

    static {
        CLIPS.put(RollKind.FORWARD, id("roll"));
        CLIPS.put(RollKind.LEFT, id("roll_left"));
        CLIPS.put(RollKind.RIGHT, id("roll_right"));
        CLIPS.put(RollKind.BACKWARD, id("roll_backward"));
    }

    private RollAnimations() {
    }

    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(NinjasFreePlay.MODID, name);
    }

    public static ResourceLocation clip(RollKind kind) {
        return CLIPS.get(kind);
    }

    public static boolean contains(ResourceLocation id) {
        return CLIPS.containsValue(id);
    }
}
