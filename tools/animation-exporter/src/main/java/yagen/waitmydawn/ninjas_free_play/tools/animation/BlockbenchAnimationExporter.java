package yagen.waitmydawn.ninjas_free_play.tools.animation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import yagen.waitmydawn.ninjas_free_play.animation.core.Bone;
import yagen.waitmydawn.ninjas_free_play.animation.core.HumanoidRig;
import yagen.waitmydawn.ninjas_free_play.animation.resource.AnimationJsonCodec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Build-time adapter for numeric Blockbench 5.0 Euler animations and explicit humanoid bindings.
 */
public final class BlockbenchAnimationExporter {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Set<String> UNSUPPORTED_EXPRESSIONS = Set.of("anim_time_update", "blend_weight", "start_delay", "loop_delay");

    private BlockbenchAnimationExporter() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 3)
            throw new IllegalArgumentException("Expected source directory, generated resource directory, and report path");
        JsonObject report = export(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]));
        System.out.println("Exported " + report.getAsJsonArray("resources").size() + " Blockbench animations");
    }

    public static JsonObject export(Path sourceDirectory, Path outputDirectory, Path reportPath) throws IOException {
        Path root = sourceDirectory.toRealPath();
        Path output = physicalPath(outputDirectory);
        reportPath = physicalPath(reportPath);
        if (root.startsWith(output) || output.startsWith(root)) {
            throw new IllegalArgumentException("Generated resources must be separate from animation sources");
        }
        if (reportPath.startsWith(root))
            throw new IllegalArgumentException("Export report cannot overwrite source files");
        JsonObject manifest = read(root.resolve("project.json"));
        if (number(manifest.get("format_version")) != 1 || !"humanoid_v1".equals(text(manifest, "rig"))) {
            throw new IllegalArgumentException("Unsupported authoring manifest or rig version");
        }
        String namespace = text(manifest, "namespace");
        if (!namespace.matches("[a-z0-9_.-]+")) throw new IllegalArgumentException("Invalid animation namespace");
        double[] positionSigns = signs(manifest.getAsJsonArray("position_signs"));
        double[] rotationSigns = signs(manifest.getAsJsonArray("rotation_signs"));
        requireEqual(positionSigns, new double[]{1, -1, 1}, "Unexpected humanoid_v1 position convention");
        requireEqual(rotationSigns, new double[]{-1, 1, -1}, "Unexpected humanoid_v1 rotation convention");
        JsonObject bindings = manifest.getAsJsonObject("bones");
        if (bindings == null || bindings.isEmpty())
            throw new IllegalArgumentException("Explicit bone bindings are required");
        TreeMap<String, String> generated = new TreeMap<>();
        JsonObject coordinateSpaces = manifest.has("coordinate_spaces") ? manifest.getAsJsonObject("coordinate_spaces") : new JsonObject();
        for (var entry : coordinateSpaces.entrySet()) {
            if (!validResourcePath(entry.getKey()) || !entry.getValue().isJsonPrimitive()
                    || !entry.getValue().getAsJsonPrimitive().isString()
                    || !Set.of("entity", "launch").contains(entry.getValue().getAsString())) {
                throw new IllegalArgumentException("Invalid coordinate space binding: " + entry.getKey());
            }
        }
        Set<String> exportedClipNames = new HashSet<>();
        JsonArray sources = new JsonArray();
        Set<String> projects = new HashSet<>();
        for (JsonElement projectEntry : manifest.getAsJsonArray("projects")) {
            String relative = projectEntry.getAsString();
            Path source = root.resolve(relative).normalize().toRealPath();
            if (!source.startsWith(root) || !relative.endsWith(".bbmodel") || !projects.add(relative)) {
                throw new IllegalArgumentException("Invalid or duplicate source project: " + relative);
            }
            try {
                JsonObject project = read(source);
                Map<String, String> names = validateRig(project, bindings);
                Set<String> clipNames = new HashSet<>();
                for (JsonElement entry : project.getAsJsonArray("animations")) {
                    JsonObject animation = entry.getAsJsonObject();
                    String name = text(animation, "name");
                    if (!validResourcePath(name) || !clipNames.add(name))
                        throw new IllegalArgumentException("Invalid or duplicate animation name: " + name);
                    JsonObject clip = convert(animation, names, bindings, positionSigns, rotationSigns);
                    clip.addProperty("coordinate_space", coordinateSpaces.has(name) ? coordinateSpaces.get(name).getAsString() : "entity");
                    exportedClipNames.add(name);
                    AnimationJsonCodec.read(clip);
                    String path = "assets/" + namespace + "/ninja_animations/" + name + ".json";
                    if (generated.put(path, JSON.toJson(clip) + "\n") != null)
                        throw new IllegalArgumentException("Duplicate exported resource: " + path);
                }
                JsonObject provenance = new JsonObject();
                provenance.addProperty("path", relative);
                provenance.addProperty("sha256", sha256(Files.readAllBytes(source)));
                sources.add(provenance);
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException(relative + ": " + exception.getMessage(), exception);
            }
        }
        if (generated.isEmpty()) throw new IllegalArgumentException("No animations found in the source projects");
        if (!exportedClipNames.containsAll(coordinateSpaces.keySet()))
            throw new IllegalArgumentException("Coordinate space binding references a missing animation");

        // Validate all projects before replacing any output; a bad clip cannot publish a partial export.
        Set<Path> previous = previousOutputs(reportPath, output);
        JsonObject report = new JsonObject();
        report.addProperty("exporter_version", 1);
        report.addProperty("runtime_format_version", 3);
        report.addProperty("manifest_sha256", sha256(Files.readAllBytes(root.resolve("project.json"))));
        report.add("sources", sources);
        JsonArray resources = new JsonArray();
        for (var entry : generated.entrySet()) {
            Path destination = resourceDestination(output, entry.getKey());
            byte[] bytes = entry.getValue().getBytes(StandardCharsets.UTF_8);
            atomicWrite(destination, bytes);
            previous.remove(destination);
            JsonObject resource = new JsonObject();
            resource.addProperty("path", entry.getKey());
            resource.addProperty("sha256", sha256(bytes));
            resources.add(resource);
        }
        for (Path stale : previous) Files.deleteIfExists(stale);
        report.add("resources", resources);
        atomicWrite(reportPath, (JSON.toJson(report) + "\n").getBytes(StandardCharsets.UTF_8));
        return report;
    }

    private static Map<String, String> validateRig(JsonObject project, JsonObject bindings) {
        JsonObject meta = project.getAsJsonObject("meta");
        if (meta == null || !"5.0".equals(text(meta, "format_version")) || !"free".equals(text(meta, "model_format"))) {
            throw new IllegalArgumentException("Expected a Blockbench 5.0 Free Model project");
        }
        for (JsonElement entry : project.getAsJsonArray("elements")) {
            if (!"cube".equals(text(entry.getAsJsonObject(), "type"))) {
                throw new IllegalArgumentException("The authoring rig supports cube guides; IK/control nodes must be baked and removed");
            }
        }
        Map<String, String> names = new HashMap<>();
        Map<String, JsonObject> groups = new HashMap<>();
        Set<String> distinctNames = new HashSet<>();
        Set<String> destinations = new HashSet<>();
        for (JsonElement entry : project.getAsJsonArray("groups")) {
            JsonObject group = entry.getAsJsonObject();
            String id = text(group, "uuid"), name = text(group, "name");
            if (names.put(id, name) != null || !distinctNames.add(name))
                throw new IllegalArgumentException("Duplicate bone identity: " + name);
            groups.put(name, group);
        }
        if (!groups.keySet().equals(bindings.keySet()))
            throw new IllegalArgumentException("Project bones do not match the explicit rig bindings");
        Map<String, String> parents = new HashMap<>();
        collectParents(project.getAsJsonArray("outliner"), null, names, parents);
        if (parents.size() != groups.size()) throw new IllegalArgumentException("Orphaned bones in the outliner");
        for (var entry : bindings.entrySet()) {
            String name = entry.getKey();
            JsonObject binding = entry.getValue().getAsJsonObject();
            JsonObject group = groups.get(name);
            String parent = binding.get("parent").isJsonNull() ? null : text(binding, "parent");
            if (!java.util.Objects.equals(parent, parents.get(name)))
                throw new IllegalArgumentException("Unexpected parent for bone " + name);
            requireEqual(vector(group.get("origin")), vector(binding.get("origin")), "Bind origin changed: " + name);
            requireEqual(vector(group.get("rotation")), new double[3], "Nonzero bind rotation: " + name);
            String target = text(binding, "target"), kind = text(binding, "kind");
            Bone targetBone = Bone.valueOf(target.toUpperCase(Locale.ROOT));
            if (!target.equals(targetBone.name().toLowerCase(Locale.ROOT)) || !Set.of("transform", "bend").contains(kind)
                    || !destinations.add(target + ":" + kind))
                throw new IllegalArgumentException("Invalid or duplicate bone binding: " + name);
            if (kind.equals("bend") && !Set.of(Bone.LEFT_ARM, Bone.RIGHT_ARM, Bone.LEFT_LEG, Bone.RIGHT_LEG).contains(targetBone)) {
                throw new IllegalArgumentException("Bend target must be a limb: " + name);
            }
            var anchor = HumanoidRig.bindAnchor(targetBone, kind.equals("bend"), false);
            requireEqual(vector(binding.get("origin")), new double[]{anchor.x(), 24 - anchor.y(), anchor.z()},
                    "Binding does not match the runtime humanoid_v1 rig: " + name);
            Bone expectedParent = kind.equals("bend") ? targetBone : HumanoidRig.parent(targetBone);
            if (expectedParent == null) {
                if (parent != null) throw new IllegalArgumentException("Runtime root cannot have a parent");
            } else {
                JsonObject parentBinding = parent == null ? null : bindings.getAsJsonObject(parent);
                if (parentBinding == null || !"transform".equals(text(parentBinding, "kind"))
                        || !expectedParent.name().toLowerCase(Locale.ROOT).equals(text(parentBinding, "target"))) {
                    throw new IllegalArgumentException("Binding hierarchy differs from the runtime rig: " + name);
                }
            }
        }
        return names;
    }

    private static void collectParents(JsonArray entries, String parent, Map<String, String> names, Map<String, String> parents) {
        if (entries == null) throw new IllegalArgumentException("Missing outliner children");
        for (JsonElement entry : entries) {
            if (!entry.isJsonObject()) {
                if (names.containsKey(entry.getAsString()))
                    throw new IllegalArgumentException("Bone requires an outliner group node");
                continue;
            }
            JsonObject node = entry.getAsJsonObject();
            String name = names.get(text(node, "uuid"));
            if (name == null || parents.containsKey(name))
                throw new IllegalArgumentException("Unknown, duplicated, or cyclic outliner bone");
            parents.put(name, parent);
            collectParents(node.getAsJsonArray("children"), name, names, parents);
        }
    }

    private static JsonObject convert(JsonObject animation, Map<String, String> names, JsonObject bindings,
                                      double[] positionSigns, double[] rotationSigns) {
        String animationName = text(animation, "name");
        double duration = number(animation.get("length"));
        JsonElement loop = animation.get("loop");
        boolean looping = loop != null && loop.isJsonPrimitive() && "loop".equals(loop.getAsString());
        if (loop != null && !loop.isJsonNull() && !Set.of("loop", "once", "false").contains(loop.getAsString())) {
            throw new IllegalArgumentException(animationName + ": unsupported loop mode");
        }
        for (String expression : UNSUPPORTED_EXPRESSIONS) {
            if (animation.has(expression) && !animation.get(expression).getAsString().isEmpty()) {
                throw new IllegalArgumentException(animationName + ": expressions and delays require baking: " + expression);
            }
        }
        if (bool(animation, "override"))
            throw new IllegalArgumentException("Animation override is controlled by the runtime layer");
        JsonObject result = new JsonObject();
        result.addProperty("format_version", 3);
        result.addProperty("rig", "humanoid_v1");
        result.addProperty("duration", duration);
        result.addProperty("loop", looping);
        JsonObject bones = new JsonObject();
        TreeMap<String, Map<String, List<JsonObject>>> grouped = new TreeMap<>();
        for (var entry : animation.getAsJsonObject("animators").entrySet()) {
            JsonObject animator = entry.getValue().getAsJsonObject();
            String sourceName = names.get(entry.getKey());
            if (sourceName == null || !"bone".equals(text(animator, "type")))
                throw new IllegalArgumentException("Unsupported animator: " + entry.getKey());
            if (bool(animator, "rotation_global") || bool(animator, "quaternion_interpolation")) {
                throw new IllegalArgumentException(sourceName + ": use local unwrapped Euler interpolation");
            }
            JsonObject binding = bindings.getAsJsonObject(sourceName);
            boolean bend = "bend".equals(text(binding, "kind"));
            for (JsonElement frameEntry : animator.getAsJsonArray("keyframes")) {
                JsonObject frame = frameEntry.getAsJsonObject();
                String channel = text(frame, "channel");
                if (!Set.of("rotation", "position").contains(channel) || bend && !channel.equals("rotation")) {
                    throw new IllegalArgumentException(sourceName + ": unsupported channel " + channel);
                }
                String destinationChannel = bend ? "bend" : channel;
                List<JsonObject> frames = grouped.computeIfAbsent(text(binding, "target"), ignored -> new TreeMap<>())
                        .computeIfAbsent(destinationChannel, ignored -> new ArrayList<>());
                frames.add(convertKeyframe(frame, bend, channel.equals("rotation"), positionSigns, rotationSigns));
            }
        }
        grouped.forEach((bone, channels) -> {
            JsonObject target = new JsonObject();
            channels.forEach((channel, frames) -> {
                frames.sort(Comparator.comparingDouble(frame -> number(frame.get("time"))));
                if (looping && frames.size() > 1 && number(frames.get(frames.size() - 1).get("time")) != duration) {
                    throw new IllegalArgumentException(bone + "." + channel + ": animated loops require an explicit end keyframe");
                }
                JsonObject track = new JsonObject();
                track.addProperty("interpolation", "linear");
                JsonArray keys = new JsonArray();
                frames.forEach(keys::add);
                track.add("keyframes", keys);
                target.add(channel, track);
            });
            bones.add(bone, target);
        });
        result.add("bones", bones);
        return result;
    }

    private static JsonObject convertKeyframe(JsonObject frame, boolean bend, boolean rotation,
                                              double[] positionSigns, double[] rotationSigns) {
        JsonArray points = frame.getAsJsonArray("data_points");
        if (points == null || points.size() != 1)
            throw new IllegalArgumentException("Split pre/post values require baking");
        JsonObject point = points.get(0).getAsJsonObject();
        double[] values = {number(point.get("x")), number(point.get("y")), number(point.get("z"))};
        if (bend && (values[1] != 0 || values[2] != 0))
            throw new IllegalArgumentException("Forearm and shin controls support only X-axis hinge rotation");
        String interpolation = text(frame, "interpolation");
        if (!Set.of("linear", "step", "bezier").contains(interpolation)) {
            throw new IllegalArgumentException("Unsupported interpolation " + interpolation + "; use linear, step, or bezier");
        }
        double[] factors = rotation ? rotationSigns : positionSigns;
        double unit = rotation ? Math.PI / 180 : 1;
        JsonObject result = new JsonObject();
        result.addProperty("time", number(frame.get("time")));
        result.addProperty("interpolation", interpolation);
        double[] converted = converted(values, factors, unit);
        if (bend) result.addProperty("value", converted[0]);
        else result.add("value", array(converted));
        for (String side : List.of("left", "right")) {
            double defaultTime = side.equals("left") ? -0.1 : 0.1;
            double[] time = frame.has("bezier_" + side + "_time") ? vector(frame.get("bezier_" + side + "_time"))
                    : new double[]{defaultTime, defaultTime, defaultTime};
            double[] handle = frame.has("bezier_" + side + "_value") ? vector(frame.get("bezier_" + side + "_value")) : new double[3];
            if (bend && (handle[1] != 0 || handle[2] != 0))
                throw new IllegalArgumentException("Hinge Bezier handles must use the X axis");
            handle = converted(handle, factors, unit);
            if (bend) {
                time = new double[]{time[0], 0, 0};
                handle = new double[]{handle[0], 0, 0};
            }
            result.add(side + "_time", array(time));
            result.add(side + "_value", array(handle));
        }
        return result;
    }

    private static Set<Path> previousOutputs(Path reportPath, Path output) throws IOException {
        Set<Path> result = new HashSet<>();
        if (!Files.exists(reportPath)) return result;
        for (JsonElement entry : read(reportPath).getAsJsonArray("resources")) {
            result.add(resourceDestination(output, text(entry.getAsJsonObject(), "path")));
        }
        return result;
    }

    private static Path resourceDestination(Path root, String relative) throws IOException {
        if (!relative.matches("assets/[a-z0-9_.-]+/ninja_animations/[a-z0-9_./-]+\\.json") || relative.contains("..")) {
            throw new IllegalArgumentException("Invalid generated resource path: " + relative);
        }
        Path destination = physicalPath(root.resolve(relative));
        if (!destination.startsWith(root))
            throw new IllegalArgumentException("Generated resource escapes output directory");
        return destination;
    }

    private static Path physicalPath(Path path) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        Path existing = absolute;
        while (!Files.exists(existing)) existing = existing.getParent();
        return existing.toRealPath().resolve(existing.relativize(absolute)).normalize();
    }

    private static boolean validResourcePath(String name) {
        return name.matches("[a-z0-9_/-]+") && !name.startsWith("/") && !name.endsWith("/") && !name.contains("//");
    }

    private static void atomicWrite(Path destination, byte[] content) throws IOException {
        Path path = destination.toAbsolutePath().normalize();
        Files.createDirectories(path.getParent());
        if (Files.exists(path) && java.util.Arrays.equals(Files.readAllBytes(path), content)) return;
        Path temporary = Files.createTempFile(path.getParent(), path.getFileName().toString(), ".tmp");
        try {
            Files.write(temporary, content);
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static JsonObject read(Path path) throws IOException {
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static String text(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Expected string: " + key);
        return value.getAsString();
    }

    private static boolean bool(JsonObject object, String key) {
        if (!object.has(key)) return false;
        JsonElement value = object.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
            throw new IllegalArgumentException("Expected boolean: " + key);
        return value.getAsBoolean();
    }

    private static double number(JsonElement element) {
        try {
            if (element == null || !element.isJsonPrimitive() || element.getAsJsonPrimitive().isBoolean())
                throw new NumberFormatException();
            double value = Double.parseDouble(element.getAsString());
            if (!Double.isFinite(value)) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Expected finite numeric animation value; expressions require baking", exception);
        }
    }

    private static double[] vector(JsonElement element) {
        if (element == null || !element.isJsonArray() || element.getAsJsonArray().size() != 3)
            throw new IllegalArgumentException("Expected three-component vector");
        var values = element.getAsJsonArray();
        return new double[]{number(values.get(0)), number(values.get(1)), number(values.get(2))};
    }

    private static double[] signs(JsonArray array) {
        double[] values = vector(array);
        for (double value : values)
            if (Math.abs(value) != 1) throw new IllegalArgumentException("Axis conversion requires signs of +/-1");
        return values;
    }

    private static double[] converted(double[] values, double[] signs, double unit) {
        return new double[]{values[0] * signs[0] * unit, values[1] * signs[1] * unit, values[2] * signs[2] * unit};
    }

    private static JsonArray array(double[] values) {
        JsonArray result = new JsonArray();
        for (double value : values) result.add(value == 0 ? 0.0 : value);
        return result;
    }

    private static void requireEqual(double[] actual, double[] expected, String message) {
        for (int axis = 0; axis < 3; axis++)
            if (Math.abs(actual[axis] - expected[axis]) > 1e-6) throw new IllegalArgumentException(message);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
