package yagen.waitmydawn.ninjas_free_play.animation.resource;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import yagen.waitmydawn.ninjas_free_play.animation.core.*;

import java.io.Reader;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Set;

public final class AnimationJsonCodec {
    private AnimationJsonCodec() {
    }

    public static AnimationClip read(Reader reader) {
        return read(JsonParser.parseReader(reader));
    }

    public static AnimationClip read(JsonElement element) {
        JsonObject root = object(element, "animation");
        double version = number(root.get("format_version"), "format_version");
        if (version != 1 && version != 2 && version != 3) {
            throw new IllegalArgumentException("Unsupported animation format_version");
        }
        keys(root, version == 3 ? Set.of("format_version", "rig", "duration", "loop", "bones", "coordinate_space")
                : Set.of("format_version", "rig", "duration", "loop", "bones"), "animation");
        AnimationClip.CoordinateSpace space = AnimationClip.CoordinateSpace.ENTITY;
        if (version == 3) {
            String value = string(root.get("coordinate_space"), "coordinate_space");
            if (!Set.of("entity", "launch").contains(value))
                throw new IllegalArgumentException("Unknown coordinate_space: " + value);
            space = AnimationClip.CoordinateSpace.valueOf(value.toUpperCase(Locale.ROOT));
        }
        if (!"humanoid_v1".equals(string(root.get("rig"), "rig"))) {
            throw new IllegalArgumentException("Unsupported animation rig");
        }
        double duration = number(root.get("duration"), "duration");
        if (duration <= 0 || duration > 600)
            throw new IllegalArgumentException("Duration must be within (0, 600] seconds");
        JsonElement loop = root.get("loop");
        if (loop == null || !loop.isJsonPrimitive() || !loop.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException("loop must be a boolean");
        }
        JsonObject bones = object(root.get("bones"), "bones");
        if (bones.isEmpty()) throw new IllegalArgumentException("Animation must contain at least one bone");
        EnumMap<Bone, AnimationClip.Track> tracks = new EnumMap<>(Bone.class);
        for (var entry : bones.entrySet()) {
            Bone bone;
            try {
                bone = Bone.valueOf(entry.getKey().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Unknown bone: " + entry.getKey(), exception);
            }
            if (!entry.getKey().equals(bone.name().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Bone identifiers must be lower_snake_case");
            }
            JsonObject channels = object(entry.getValue(), entry.getKey());
            keys(channels, Set.of("position", "rotation", "bend"), entry.getKey());
            if (channels.isEmpty()) throw new IllegalArgumentException("Bone has no channels: " + bone);
            boolean limb = bone == Bone.LEFT_ARM || bone == Bone.RIGHT_ARM || bone == Bone.LEFT_LEG || bone == Bone.RIGHT_LEG;
            if (channels.has("bend") && !limb) throw new IllegalArgumentException("Only limbs support bend channels");
            tracks.put(bone, new AnimationClip.Track(track(channels.get("position"), false, version),
                    track(channels.get("rotation"), false, version), track(channels.get("bend"), true, version)));
        }
        return new AnimationClip(duration, loop.getAsBoolean(), tracks, space);
    }

    private static KeyframeTrack track(JsonElement element, boolean bend, double version) {
        if (element == null) return null;
        JsonObject channel = object(element, "channel");
        keys(channel, Set.of("interpolation", "keyframes"), "channel");
        KeyframeTrack.Interpolation interpolation;
        try {
            interpolation = KeyframeTrack.Interpolation.valueOf(string(channel.get("interpolation"), "interpolation").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown interpolation", exception);
        }
        JsonElement keyframes = channel.get("keyframes");
        if (keyframes == null || !keyframes.isJsonArray() || keyframes.getAsJsonArray().size() > 4096) {
            throw new IllegalArgumentException("keyframes must be an array with at most 4096 entries");
        }
        ArrayList<KeyframeTrack.Keyframe> result = new ArrayList<>();
        for (JsonElement frame : keyframes.getAsJsonArray()) {
            JsonObject keyframe = object(frame, "keyframe");
            keys(keyframe, version == 1 ? Set.of("time", "value") : Set.of("time", "value", "interpolation",
                    "left_time", "left_value", "right_time", "right_value"), "keyframe");
            JsonElement value = keyframe.get("value");
            Vector3 vector;
            if (bend) {
                double angle = number(value, "bend");
                if (Math.abs(angle) > BonePose.MAX_BEND)
                    throw new IllegalArgumentException("Bend exceeds +/-120 degrees");
                vector = new Vector3(angle, 0, 0);
            } else {
                if (value == null || !value.isJsonArray() || value.getAsJsonArray().size() != 3) {
                    throw new IllegalArgumentException("position and rotation values require three numbers");
                }
                var components = value.getAsJsonArray();
                vector = new Vector3(number(components.get(0), "x"), number(components.get(1), "y"), number(components.get(2), "z"));
            }
            var mode = keyframe.has("interpolation") ? KeyframeTrack.Interpolation.valueOf(
                    string(keyframe.get("interpolation"), "interpolation").toUpperCase(Locale.ROOT)) : null;
            Vector3 leftTime = optionalVector(keyframe.get("left_time"));
            Vector3 rightTime = optionalVector(keyframe.get("right_time"));
            Vector3 leftValue = optionalVector(keyframe.get("left_value"));
            Vector3 rightValue = optionalVector(keyframe.get("right_value"));
            if (bend && (Math.abs(vector.x() + leftValue.x()) > BonePose.MAX_BEND
                    || Math.abs(vector.x() + rightValue.x()) > BonePose.MAX_BEND
                    || leftValue.y() != 0 || leftValue.z() != 0 || rightValue.y() != 0 || rightValue.z() != 0)) {
                throw new IllegalArgumentException("Bend handles must remain within the X-axis joint limits");
            }
            result.add(new KeyframeTrack.Keyframe(number(keyframe.get("time"), "time"), vector, mode,
                    leftTime, leftValue, rightTime, rightValue));
        }
        return new KeyframeTrack(result, interpolation);
    }

    private static Vector3 optionalVector(JsonElement element) {
        if (element == null) return Vector3.ZERO;
        if (!element.isJsonArray() || element.getAsJsonArray().size() != 3) {
            throw new IllegalArgumentException("Bezier handles require three numbers");
        }
        var vector = element.getAsJsonArray();
        return new Vector3(number(vector.get(0), "x"), number(vector.get(1), "y"), number(vector.get(2), "z"));
    }

    private static JsonObject object(JsonElement element, String name) {
        if (element == null || !element.isJsonObject()) throw new IllegalArgumentException(name + " must be an object");
        return element.getAsJsonObject();
    }

    private static String string(JsonElement element, String name) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(name + " must be a string");
        }
        return element.getAsString();
    }

    private static double number(JsonElement element, String name) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(name + " must be a number");
        }
        double value = element.getAsDouble();
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
        return value;
    }

    private static void keys(JsonObject object, Set<String> allowed, String name) {
        for (String key : object.keySet()) {
            if (!allowed.contains(key)) throw new IllegalArgumentException("Unknown " + name + " field: " + key);
        }
    }
}
