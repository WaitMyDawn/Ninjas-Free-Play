package yagen.waitmydawn.ninjas_free_play.config;

import java.nio.file.Path;
import yagen.waitmydawn.ninjas_free_play.camera.core.CameraSettings;
import yagen.waitmydawn.ninjas_free_play.camera.core.ViewCycle;

public class ClientConfigs {
    private static final FabricConfig BUILDER = new FabricConfig();
    private static final FabricConfig SPEC;
    public static final FabricConfig.Value<Boolean> ENABLE_PLAYER_ANIMATIONS;
    public static final FabricConfig.Value<Double> ANIMATION_TRANSITION_SECONDS;
    public static final FabricConfig.Value<Boolean> ENABLE_SHOULDER_CAMERA;
    public static final FabricConfig.Value<String> CAMERA_VIEW_CYCLE;
    public static final FabricConfig.Value<Double> CAMERA_DISTANCE;
    public static final FabricConfig.Value<Double> CAMERA_HORIZONTAL_OFFSET;
    public static final FabricConfig.Value<Double> CAMERA_FIXED_HORIZONTAL_OFFSET;
    public static final FabricConfig.Value<Double> CAMERA_VERTICAL_OFFSET;
    public static final FabricConfig.Value<Double> CAMERA_FOLLOW_SECONDS;
    public static final FabricConfig.Value<Double> CAMERA_TRANSITION_SECONDS;
    public static final FabricConfig.Value<Double> CAMERA_RECOVERY_SECONDS;
    public static final FabricConfig.Value<Double> CAMERA_COLLISION_RADIUS;
    public static final FabricConfig.Value<Double> CAMERA_AIM_DISTANCE;
    private static final FabricConfig.Value<Integer> CAMERA_DEFAULT_SCHEMA;

    static {
        BUILDER.push("Animation");
        ENABLE_PLAYER_ANIMATIONS = BUILDER.comment("Enable custom third-person player animations.")
                .define("Enable_Player_Animations", true);
        ANIMATION_TRANSITION_SECONDS = BUILDER.comment("Maximum transition duration in seconds; zero cuts immediately. Weight-bound constraints may shorten a fade.")
                .defineInRange("Transition_Seconds", 0.15, 0.0, 1.0);
        BUILDER.pop();
        BUILDER.push("ShoulderCamera");
        CAMERA_DEFAULT_SCHEMA = BUILDER.comment("Camera default-value migration schema; preserve custom values after migration.")
                .defineInRange("Default_Schema", 0, 0, 2);
        ENABLE_SHOULDER_CAMERA = BUILDER.comment("Add NFP automatic and fixed views after the vanilla back view in the perspective-key cycle.")
                .define("Enabled", true);
        CAMERA_VIEW_CYCLE = BUILDER.comment("F5 cycle mask: first person, vanilla back, NFP automatic, NFP fixed, vanilla front; at least one view must be enabled.")
                .define("View_Cycle", ViewCycle.ALL.mask(), value -> value instanceof String mask && ViewCycle.valid(mask) ? mask : null);
        CAMERA_DISTANCE = BUILDER.comment("Camera distance behind the physical eye in unscaled blocks.")
                .defineInRange("Distance", CameraSettings.DEFAULT.distance(), .5, 12);
        CAMERA_HORIZONTAL_OFFSET = BUILDER.comment("Screen-right camera displacement in unscaled blocks; positive places the player on the left.")
                .defineInRange("Horizontal_Offset", CameraSettings.DEFAULT.horizontal(), -3, 3);
        CAMERA_FIXED_HORIZONTAL_OFFSET = BUILDER.comment("Screen-right displacement for the centered-crosshair fixed view, independent of the automatic view.")
                .defineInRange("Fixed_Horizontal_Offset", CameraSettings.FIXED_HORIZONTAL_DEFAULT, -3, 3);
        CAMERA_VERTICAL_OFFSET = BUILDER.comment("Screen-up camera displacement in unscaled blocks; positive places the player lower.")
                .defineInRange("Vertical_Offset", CameraSettings.DEFAULT.vertical(), -2, 3);
        CAMERA_FOLLOW_SECONDS = BUILDER.comment("Position follow time constant in seconds; zero follows immediately, independent of frame rate.")
                .defineInRange("Follow_Seconds", CameraSettings.DEFAULT.followSeconds(), 0, 1);
        CAMERA_TRANSITION_SECONDS = BUILDER.comment("Seconds to blend into shoulder composition; vanilla first/front perspective cuts remain immediate.")
                .defineInRange("Transition_Seconds", CameraSettings.DEFAULT.transitionSeconds(), 0, 1);
        CAMERA_RECOVERY_SECONDS = BUILDER.comment("Time constant for returning to the intended distance after an obstruction clears; retraction is immediate.")
                .defineInRange("Collision_Recovery_Seconds", CameraSettings.DEFAULT.recoverySeconds(), 0, 1);
        CAMERA_COLLISION_RADIUS = BUILDER.comment("Minimum unscaled camera clearance radius; the viewport near-plane footprint can increase it.")
                .defineInRange("Collision_Radius", CameraSettings.DEFAULT.radius(), .03, .5);
        CAMERA_AIM_DISTANCE = BUILDER.comment("Automatic view visual-query distance; fixed view virtual-target distance ahead of the eye, ignoring world hits and camera retraction. Does not extend interaction reach.")
                .defineInRange("Aim_Distance", 64.0, 16, 128);
        BUILDER.pop();
        SPEC = BUILDER;
    }

    public static void load(Path path) {
        SPEC.load(path);
        migrateDefaults();
        save();
    }

    public static void save() {
        SPEC.save();
    }

    private static void migrateDefaults() {
        int schema = CAMERA_DEFAULT_SCHEMA.get();
        if (schema >= 2) return;
        CAMERA_HORIZONTAL_OFFSET.set(CameraSettings.migrateHorizontal(CAMERA_HORIZONTAL_OFFSET.get(), schema));
        CAMERA_DEFAULT_SCHEMA.set(2);
    }
}
