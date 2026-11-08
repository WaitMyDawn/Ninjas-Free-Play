package yagen.waitmydawn.ninjas_free_play.animation.resource;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import yagen.waitmydawn.ninjas_free_play.animation.core.HandContactProfile;

import java.util.ArrayList;
import java.util.Set;

public final class HandContactCodec {
    private HandContactCodec() {
    }

    public static HandContactProfile read(JsonElement json) {
        JsonObject root = json.getAsJsonObject();
        if (!root.keySet().equals(Set.of("format_version", "keys")) || number(root.get("format_version")) != 1) {
            throw new IllegalArgumentException("Unsupported hand contact contract");
        }
        var keys = new ArrayList<HandContactProfile.Key>();
        for (JsonElement element : root.getAsJsonArray("keys")) {
            JsonObject key = element.getAsJsonObject();
            if (!key.keySet().equals(Set.of("phase", "left", "right")))
                throw new IllegalArgumentException("Unknown hand contact fields");
            keys.add(new HandContactProfile.Key(number(key.get("phase")), number(key.get("left")), number(key.get("right"))));
        }
        return new HandContactProfile(keys);
    }

    private static double number(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Hand contact values must be JSON numbers");
        }
        double number = value.getAsDouble();
        if (!Double.isFinite(number)) throw new IllegalArgumentException("Non-finite hand contact value");
        return number;
    }
}
