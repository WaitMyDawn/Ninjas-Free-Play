package yagen.waitmydawn.ninjas_free_play.animation;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import yagen.waitmydawn.ninjas_free_play.animation.core.Bone;
import yagen.waitmydawn.ninjas_free_play.animation.resource.AnimationJsonCodec;
import yagen.waitmydawn.ninjas_free_play.tools.animation.BlockbenchAnimationExporter;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class BlockbenchAnimationExporterTest {
    @TempDir Path temporary;
    private Path source;
    private Path output;
    private Path report;

    @BeforeEach
    void copySources() throws Exception {
        source = Files.createDirectory(temporary.resolve("source"));
        output = temporary.resolve("generated");
        report = temporary.resolve("report.json");
        Files.copy(Path.of("animations/project.json"), source.resolve("project.json"));
        Files.copy(Path.of("animations/player_movement.bbmodel"), source.resolve("player_movement.bbmodel"));
    }

    private JsonObject project() throws Exception {
        return JsonParser.parseString(Files.readString(source.resolve("player_movement.bbmodel"))).getAsJsonObject();
    }

    private void saveProject(JsonObject project) throws Exception {
        Files.writeString(source.resolve("player_movement.bbmodel"), new GsonBuilder().setPrettyPrinting().create().toJson(project));
    }

    private Path exported(String name) {
        return output.resolve("assets/ninjas_free_play/ninja_animations/" + name + ".json");
    }

    @Test
    void sourceMigrationPreservesInitialP0SamplesAndAuthoredTurns() throws Exception {
        Files.copy(Path.of("src/test/resources/animation/initial_p0/player_movement.bbmodel"),
                source.resolve("player_movement.bbmodel"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        var result = BlockbenchAnimationExporter.export(source, output, report);
        assertEquals(3, result.getAsJsonArray("resources").size());
        for (String name : new String[]{"slide", "bullet_jump", "slide_to_bullet_jump"}) {
            try (var reference = new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream(
                    "/animation/initial_p0/" + name + ".json")), StandardCharsets.UTF_8);
                 var generated = Files.newBufferedReader(exported(name))) {
                var expected = AnimationJsonCodec.read(reference);
                var actual = AnimationJsonCodec.read(generated);
                for (int index = 0; index <= 137; index++) {
                    double time = expected.duration() * index / 137;
                    for (Bone bone : Bone.values()) {
                        var oldPose = expected.sample(time).bone(bone);
                        var newPose = actual.sample(time).bone(bone);
                        assertEquals(oldPose.bend(), newPose.bend(), 1e-6, name + ":" + bone);
                        for (int axis = 0; axis < 3; axis++) {
                            assertEquals(oldPose.position().component(axis), newPose.position().component(axis), 1e-6, name + ":" + bone);
                            assertEquals(oldPose.rotation().component(axis), newPose.rotation().component(axis), 1e-6, name + ":" + bone);
                        }
                    }
                }
            }
        }
    }

    @Test
    void exportIsDeterministicAndDoesNotRewriteSources() throws Exception {
        byte[] original = Files.readAllBytes(source.resolve("player_movement.bbmodel"));
        BlockbenchAnimationExporter.export(source, output, report);
        byte[] first = Files.readAllBytes(report);
        byte[] animation = Files.readAllBytes(exported("bullet_jump"));
        BlockbenchAnimationExporter.export(source, output, report);
        assertArrayEquals(first, Files.readAllBytes(report));
        assertArrayEquals(animation, Files.readAllBytes(exported("bullet_jump")));
        assertArrayEquals(original, Files.readAllBytes(source.resolve("player_movement.bbmodel")));
    }

    @Test
    void removingAClipPrunesOnlyPreviouslyOwnedOutputs() throws Exception {
        BlockbenchAnimationExporter.export(source, output, report);
        Path sentinel = output.resolve("unowned.txt");
        Files.writeString(sentinel, "preserve");
        var project = project();
        project.getAsJsonArray("animations").remove(0);
        saveProject(project);
        var manifest = JsonParser.parseString(Files.readString(source.resolve("project.json"))).getAsJsonObject();
        manifest.getAsJsonObject("coordinate_spaces").remove("bullet_jump");
        Files.writeString(source.resolve("project.json"), manifest.toString());
        BlockbenchAnimationExporter.export(source, output, report);
        assertFalse(Files.exists(exported("bullet_jump")));
        assertTrue(Files.exists(exported("slide")));
        assertEquals("preserve", Files.readString(sentinel));
    }

    @Test
    void expressionFailureLeavesPreviouslyExportedResourcesUnchanged() throws Exception {
        BlockbenchAnimationExporter.export(source, output, report);
        byte[] before = Files.readAllBytes(exported("bullet_jump"));
        var project = project();
        var animator = project.getAsJsonArray("animations").get(0).getAsJsonObject().getAsJsonObject("animators").entrySet().iterator().next().getValue().getAsJsonObject();
        animator.getAsJsonArray("keyframes").get(0).getAsJsonObject().getAsJsonArray("data_points")
                .get(0).getAsJsonObject().addProperty("x", "query.anim_time * 30");
        saveProject(project);
        assertThrows(IllegalArgumentException.class, () -> BlockbenchAnimationExporter.export(source, output, report));
        assertArrayEquals(before, Files.readAllBytes(exported("bullet_jump")));
    }

    @Test
    void rejectsUnsupportedChannelsAndInterpolation() throws Exception {
        var original = project();
        for (String field : new String[]{"channel", "interpolation"}) {
            var modified = original.deepCopy();
            var animator = modified.getAsJsonArray("animations").get(0).getAsJsonObject().getAsJsonObject("animators").entrySet().iterator().next().getValue().getAsJsonObject();
            animator.getAsJsonArray("keyframes").get(0).getAsJsonObject().addProperty(field, field.equals("channel") ? "scale" : "catmullrom");
            saveProject(modified);
            assertThrows(IllegalArgumentException.class, () -> BlockbenchAnimationExporter.export(source, output, report));
        }
    }

    @Test
    void explicitIdentityBindingsSupportIrregularSourceNamesAndClipPaths() throws Exception {
        var project = project();
        var manifest = JsonParser.parseString(Files.readString(source.resolve("project.json"))).getAsJsonObject();
        var bindings = manifest.getAsJsonObject("bones");
        bindings.add("pelvis_anchor_x9", bindings.remove("root"));
        for (var entry : bindings.entrySet()) {
            var parent = entry.getValue().getAsJsonObject().get("parent");
            if (!parent.isJsonNull() && parent.getAsString().equals("root")) entry.getValue().getAsJsonObject().addProperty("parent", "pelvis_anchor_x9");
        }
        for (var entry : project.getAsJsonArray("groups")) {
            if (entry.getAsJsonObject().get("name").getAsString().equals("root")) entry.getAsJsonObject().addProperty("name", "pelvis_anchor_x9");
        }
        project.getAsJsonArray("animations").get(0).getAsJsonObject().addProperty("name", "motion/irregular_17");
        manifest.getAsJsonObject("coordinate_spaces").add("motion/irregular_17",
                manifest.getAsJsonObject("coordinate_spaces").remove("bullet_jump"));
        saveProject(project);
        Files.writeString(source.resolve("project.json"), manifest.toString());
        BlockbenchAnimationExporter.export(source, output, report);
        assertTrue(Files.exists(exported("motion/irregular_17")));
        try (var reader = Files.newBufferedReader(exported("motion/irregular_17"))) {
            assertEquals(yagen.waitmydawn.ninjas_free_play.animation.core.AnimationClip.CoordinateSpace.LAUNCH,
                    AnimationJsonCodec.read(reader).coordinateSpace());
        }
    }

    @Test
    void coordinateBindingsRejectUnknownSpacesAndMissingClips() throws Exception {
        var manifest = JsonParser.parseString(Files.readString(source.resolve("project.json"))).getAsJsonObject();
        manifest.getAsJsonObject("coordinate_spaces").addProperty("bullet_jump", "unknown");
        Files.writeString(source.resolve("project.json"), manifest.toString());
        assertThrows(IllegalArgumentException.class, () -> BlockbenchAnimationExporter.export(source, output, report));
        manifest.getAsJsonObject("coordinate_spaces").addProperty("bullet_jump", "launch");
        manifest.getAsJsonObject("coordinate_spaces").addProperty("missing", "launch");
        Files.writeString(source.resolve("project.json"), manifest.toString());
        assertThrows(IllegalArgumentException.class, () -> BlockbenchAnimationExporter.export(source, output, report));
    }

    @Test
    void rejectsMissingBindingsChangedBindPoseAndDuplicateClipNames() throws Exception {
        var original = project();
        var changed = original.deepCopy();
        changed.getAsJsonArray("groups").get(0).getAsJsonObject().addProperty("name", "unbound");
        saveProject(changed);
        assertThrows(IllegalArgumentException.class, () -> BlockbenchAnimationExporter.export(source, output, report));
        changed = original.deepCopy();
        changed.getAsJsonArray("groups").get(0).getAsJsonObject().getAsJsonArray("origin").set(0, new com.google.gson.JsonPrimitive(1));
        saveProject(changed);
        assertThrows(IllegalArgumentException.class, () -> BlockbenchAnimationExporter.export(source, output, report));
        changed = original.deepCopy();
        changed.getAsJsonArray("animations").add(changed.getAsJsonArray("animations").get(0).deepCopy());
        saveProject(changed);
        assertThrows(IllegalArgumentException.class, () -> BlockbenchAnimationExporter.export(source, output, report));
    }

    @Test
    void matchingEditorAndManifestChangesCannotSilentlyRedefineTheRuntimeRig() throws Exception {
        var project = project();
        project.getAsJsonArray("groups").get(0).getAsJsonObject().getAsJsonArray("origin").set(0, new com.google.gson.JsonPrimitive(1));
        var manifest = JsonParser.parseString(Files.readString(source.resolve("project.json"))).getAsJsonObject();
        manifest.getAsJsonObject("bones").getAsJsonObject("root").getAsJsonArray("origin").set(0, new com.google.gson.JsonPrimitive(1));
        saveProject(project);
        Files.writeString(source.resolve("project.json"), manifest.toString());
        assertThrows(IllegalArgumentException.class, () -> BlockbenchAnimationExporter.export(source, output, report));
    }

    @Test
    void nativeEditorEditsUseExplicitAxesAndDegreeConversion() throws Exception {
        var project = project();
        var animation = project.getAsJsonArray("animations").get(0).getAsJsonObject();
        var root = project.getAsJsonArray("groups").get(0).getAsJsonObject().get("uuid").getAsString();
        for (var entry : animation.getAsJsonObject("animators").getAsJsonObject(root).getAsJsonArray("keyframes")) {
            var frame = entry.getAsJsonObject();
            if (frame.get("time").getAsDouble() != 0) continue;
            var value = frame.getAsJsonArray("data_points").get(0).getAsJsonObject();
            value.addProperty("x", 10);
            value.addProperty("y", 20);
            value.addProperty("z", 30);
        }
        saveProject(project);
        BlockbenchAnimationExporter.export(source, output, report);
        try (var reader = Files.newBufferedReader(exported("bullet_jump"))) {
            var pose = AnimationJsonCodec.read(reader).sample(0).bone(Bone.ROOT);
            assertEquals(-Math.toRadians(10), pose.rotation().x(), 1e-6);
            assertEquals(Math.toRadians(20), pose.rotation().y(), 1e-6);
            assertEquals(-Math.toRadians(30), pose.rotation().z(), 1e-6);
            assertEquals(10, pose.position().x(), 1e-6);
            assertEquals(-20, pose.position().y(), 1e-6);
            assertEquals(30, pose.position().z(), 1e-6);
        }
    }

    @Test
    void mixedNativeInterpolationUsesBlockbenchDefaultTimeHandles() throws Exception {
        var project = project();
        var animation = project.getAsJsonArray("animations").get(0).getAsJsonObject();
        animation.addProperty("length", 1);
        var root = project.getAsJsonArray("groups").get(0).getAsJsonObject().get("uuid").getAsString();
        var keys = JsonParser.parseString("""
                [{"channel":"rotation","time":0,"interpolation":"linear","data_points":[{"x":0,"y":0,"z":0}]},
                 {"channel":"rotation","time":1,"interpolation":"bezier","data_points":[{"x":90,"y":0,"z":0}]}]
                """).getAsJsonArray();
        animation.getAsJsonObject("animators").getAsJsonObject(root).add("keyframes", keys);
        saveProject(project);
        BlockbenchAnimationExporter.export(source, output, report);
        try (var reader = Files.newBufferedReader(exported("bullet_jump"))) {
            assertEquals(-Math.toRadians(14.0625), AnimationJsonCodec.read(reader).sample(0.184375)
                    .bone(Bone.ROOT).rotation().x(), 1e-6);
        }
    }

    @Test
    void forgedPreviousReportCannotDeleteFilesOutsideGeneratedResources() throws Exception {
        Files.writeString(temporary.resolve("keep.txt"), "preserve");
        Files.writeString(report, "{\"resources\":[{\"path\":\"../keep.txt\"}]}");
        assertThrows(IllegalArgumentException.class, () -> BlockbenchAnimationExporter.export(source, output, report));
        assertEquals("preserve", Files.readString(temporary.resolve("keep.txt")));
    }

    @Test
    void rejectsUnbakedEditorControlNodes() throws Exception {
        var project = project();
        project.getAsJsonArray("elements").add(JsonParser.parseString(
                "{\"type\":\"null_object\",\"uuid\":\"unbaked-control\",\"name\":\"joint_target\"}"));
        saveProject(project);
        assertThrows(IllegalArgumentException.class, () -> BlockbenchAnimationExporter.export(source, output, report));
    }
}
