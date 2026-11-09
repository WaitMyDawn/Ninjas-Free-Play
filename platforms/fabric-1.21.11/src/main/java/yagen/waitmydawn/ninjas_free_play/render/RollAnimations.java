package yagen.waitmydawn.ninjas_free_play.render;

import net.minecraft.resources.Identifier;
import yagen.waitmydawn.ninjas_free_play.NinjasFreePlay;
import yagen.waitmydawn.ninjas_free_play.core.RollKind;

import java.util.EnumMap;
import java.util.Map;

public final class RollAnimations {
    private static final Map<RollKind, Identifier> CLIPS = new EnumMap<>(RollKind.class);

    static {
        CLIPS.put(RollKind.FORWARD, id("roll"));
        CLIPS.put(RollKind.LEFT, id("roll_left"));
        CLIPS.put(RollKind.RIGHT, id("roll_right"));
        CLIPS.put(RollKind.BACKWARD, id("roll_backward"));
    }

    private RollAnimations() {
    }

    private static Identifier id(String name) {
        return Identifier.fromNamespaceAndPath(NinjasFreePlay.MODID, name);
    }

    public static Identifier clip(RollKind kind) {
        return CLIPS.get(kind);
    }

    public static boolean contains(Identifier id) {
        return CLIPS.containsValue(id);
    }
}
