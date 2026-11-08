package yagen.waitmydawn.ninjas_free_play.animation;

import org.junit.jupiter.api.Test;
import yagen.waitmydawn.ninjas_free_play.animation.core.Bone;
import yagen.waitmydawn.ninjas_free_play.animation.core.BonePose;
import yagen.waitmydawn.ninjas_free_play.animation.resource.AnimationJsonCodec;

import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class AnimationJsonCodecTest {
    private static final String VALID = """
            {"format_version":1,"rig":"humanoid_v1","duration":1,"loop":false,"bones":{
              "root":{"rotation":{"interpolation":"linear","keyframes":[
                {"time":0,"value":[0,0,0]},{"time":1,"value":[0,0,6.283185307179586]}]}}}}
            """;

    @Test
    void readsFullTurnWithoutNormalizingKeyframes() {
        var clip = AnimationJsonCodec.read(new StringReader(VALID));
        assertEquals(Math.PI, clip.sample(0.5).bone(Bone.ROOT).rotation().z(), 1e-6);
    }

    @Test
    void rejectsUnknownVersionRigAndBone() {
        for (String invalid : new String[]{VALID.replace("format_version\":1", "format_version\":4"),
                VALID.replace("format_version\":1", "format_version\":1.5"), VALID.replace("humanoid_v1", "unknown"),
                VALID.replace("\"root\"", "\"missing_bone\""), VALID.replace("\"root\"", "\"ROOT\""),
                VALID.replace("\"duration\":1", "\"duration\":\"1\""), VALID.replace("\"loop\":false", "\"loop\":\"false\""),
                VALID.replace("6.283185307179586", "1e999"), VALID.replace("\"time\":1", "\"time\":0"),
                VALID.replace("\"time\":1", "\"time\":2"), VALID.replace("\"rotation\"", "\"rotaton\"")}) {
            assertThrows(RuntimeException.class, () -> AnimationJsonCodec.read(new StringReader(invalid)), invalid);
        }
    }

    @Test
    void bundledClipsParseAndStayWithinLimbLimits() throws Exception {
        for (String name : new String[]{"slide", "bullet_jump", "slide_to_bullet_jump"}) {
            String path = "/assets/ninjas_free_play/ninja_animations/" + name + ".json";
            try (var reader = new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream(path)), StandardCharsets.UTF_8)) {
                var clip = AnimationJsonCodec.read(reader);
                for (int tick = 0; tick <= Math.ceil(clip.duration() * 60); tick++) {
                    var pose = clip.sample(tick / 60.0);
                    for (Bone bone : Bone.values()) assertTrue(Math.abs(pose.bone(bone).bend()) <= BonePose.MAX_BEND);
                }
            }
        }
    }

    @Test
    void versionThreeRequiresExplicitValidatedCoordinateSpace() {
        String launch = VALID.replace("format_version\":1", "format_version\":3")
                .replace("\"rig\"", "\"coordinate_space\":\"launch\",\"rig\"");
        assertEquals(yagen.waitmydawn.ninjas_free_play.animation.core.AnimationClip.CoordinateSpace.LAUNCH,
                AnimationJsonCodec.read(new StringReader(launch)).coordinateSpace());
        assertThrows(RuntimeException.class, () -> AnimationJsonCodec.read(new StringReader(
                VALID.replace("format_version\":1", "format_version\":3"))));
        assertThrows(RuntimeException.class, () -> AnimationJsonCodec.read(new StringReader(launch.replace("launch", "unknown"))));
    }
}
