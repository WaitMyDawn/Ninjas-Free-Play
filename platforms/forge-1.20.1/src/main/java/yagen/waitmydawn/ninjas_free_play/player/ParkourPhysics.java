package yagen.waitmydawn.ninjas_free_play.player;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import yagen.waitmydawn.ninjas_free_play.api.attribute.NAttributes;
import yagen.waitmydawn.ninjas_free_play.core.*;

public final class ParkourPhysics {
    private ParkourPhysics() {
    }

    public static ParkourRuntime runtime(Player player) {
        return ((MovementAccess) player).ninjas$parkourRuntime();
    }

    public static ParkourFrame frame(Player player, boolean probeWall) {
        Vec3 measured = player instanceof ServerPlayer ? VanillaMovement.knownMovement(player) : runtime(player).displacement;
        // Teleports and corrections are not movement qualifications.
        if (measured.lengthSqr() > 64) measured = Vec3.ZERO;
        Vec3 velocity = player instanceof ServerPlayer ? measured : player.getDeltaMovement();
        return new ParkourFrame(measured.x, measured.z, velocity.x, velocity.y, velocity.z,
                probeWall ? wall(player) : WallContact.NONE,
                MovementValues.SLIDE_ENTER_BLOCKS_PER_TICK * Math.max(0, player.getAttributeValue(Attributes.MOVEMENT_SPEED)) / .1);
    }

    private static WallContact wall(Player player) {
        var runtime = runtime(player);
        if (runtime.wallTick != player.tickCount || runtime.wallLevel != player.level() || runtime.wallWidth != player.getBbWidth()
                || !player.position().equals(runtime.wallPosition)) {
            runtime.wallTick = player.tickCount;
            runtime.wallLevel = player.level();
            runtime.wallWidth = player.getBbWidth();
            runtime.wallPosition = player.position();
            runtime.wall = WallProbe.find(player);
        }
        return runtime.wall;
    }

    public static void update(Player player) {
        var controller = MovementPhysics.controller(player);
        var state = controller.snapshot(player.tickCount);
        boolean wall = controller.parkour().aim() || state.jumpHeld() || state.parkour().mode() == ParkourMode.WALL_LATCH;
        var frame = frame(player, wall);
        controller.update(MovementPhysics.eligible(player), MovementPhysics.grounded(player), MovementPhysics.canStand(player),
                player.tickCount, NAttributes.read(player), frame);
        if (controller.parkour().mode() == ParkourMode.SLIDE && state.parkour().mode() != ParkourMode.SLIDE)
            applySlide(player, frame, NAttributes.read(player));
        if (controller.parkour().mode() == ParkourMode.WALL_LATCH) player.resetFallDistance();
    }

    public static void beforeTravel(Player player) {
        runtime(player).travelStart = player.position();
        update(player);
        var state = MovementPhysics.controller(player).snapshot(player.tickCount);
        var mode = state.parkour().mode();
        if (mode == ParkourMode.WALL_LATCH) {
            var wall = state.parkour().wall();
            double pull = Math.min(0.2 * player.getScale(), Math.max(0, wall.distance() - 0.03 * player.getScale()) * 0.6);
            player.setDeltaMovement(-wall.x() * pull, 0, -wall.z() * pull);
        }
        if (MovementPhysics.controller(player).parkour().gliding()) {
            Vec3 velocity = player.getDeltaMovement();
            player.setDeltaMovement(velocity.x, Math.max(velocity.y, -0.12), velocity.z);
        }
    }

    public static void applySlide(Player player, ParkourFrame frame, MovementValues values) {
        applySlide(player, frame, values, true);
    }

    /**
     * Ordinary prediction replay reserves the reference from recorded input facts without reapplying motion.
     */
    public static void applySlide(Player player, ParkourFrame frame, MovementValues values, boolean applyMotion) {
        Vec3 current = applyMotion ? player.getDeltaMovement() : new Vec3(frame.velocityX(), frame.velocityY(), frame.velocityZ());
        var launch = MovementPhysics.controller(player).parkour().slideMomentum().launch(frame,
                new yagen.waitmydawn.ninjas_free_play.animation.core.Vector3(current.x, current.y, current.z), values);
        if (applyMotion) player.setDeltaMovement(launch.x(), launch.y(), launch.z());
    }

    public static void captureTravelSpeed(Player player) {
        runtime(player).glideSpeedCeiling = Math.max(player.getDeltaMovement().horizontalDistance(), MovementValues.SLIDE_ENTER_BLOCKS_PER_TICK);
    }

    public static void afterTravel(Player player) {
        runtime(player).displacement = player.position().subtract(runtime(player).travelStart);
        if (MovementPhysics.controller(player).parkour().gliding()) {
            Vec3 velocity = player.getDeltaMovement();
            double speed = velocity.horizontalDistance(), ceiling = runtime(player).glideSpeedCeiling;
            // Air control may turn carried momentum, but cannot repeatedly accelerate it beyond its incoming speed.
            if (speed > ceiling && speed > 0)
                player.setDeltaMovement(velocity.x * ceiling / speed, velocity.y, velocity.z * ceiling / speed);
        }
    }

    public static void wallImpulse(Player player, MovementInput input, MovementValues values) {
        var plan = MovementPhysics.controller(player).snapshot(player.tickCount).parkour().wallDash();
        var launch = plan.direction();
        Vec3 direction = new Vec3(launch.x(), launch.y(), launch.z());
        double power = values.get(MovementAttribute.WALL_DASH_STRENGTH) / 20;
        player.setDeltaMovement(direction.scale(power));
        player.resetFallDistance();
    }

    public static double gravity(Player player, double original) {
        var parkour = MovementPhysics.controller(player).parkour();
        if (parkour.mode() == ParkourMode.WALL_LATCH) return 0;
        return parkour.gliding() ? original * NAttributes.read(player).get(player.getDeltaMovement().y > 0
                ? MovementAttribute.GLIDE_ASCENT_GRAVITY_SCALE : MovementAttribute.GLIDE_GRAVITY_SCALE) : original;
    }

    public static double retention(Player player) {
        if (MovementPhysics.controller(player).posture().rolling(player.tickCount)) return 1;
        return switch (MovementPhysics.controller(player).parkour().mode()) {
            case SLIDE -> NAttributes.read(player).slideRetention();
            case AIM_GLIDE, FLYING_KICK -> 1;
            default -> -1;
        };
    }
}
