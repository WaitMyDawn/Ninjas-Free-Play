package yagen.waitmydawn.ninjas_free_play.camera.core;

import yagen.waitmydawn.ninjas_free_play.animation.core.Vector3;

/**
 * Mouse orbit is independent of the player's aim correction toward the camera's center ray.
 */
public final class CameraOrbit {
    private CameraGeometry.Angles angles;

    public void reset() {
        angles = null;
    }

    public boolean initialized() {
        return angles != null;
    }

    public void initialize(double yaw, double pitch) {
        angles = new CameraGeometry.Angles(yaw, Math.max(-90, Math.min(90, pitch)));
    }

    public CameraGeometry.Angles angles() {
        if (angles == null) throw new IllegalStateException("Camera orbit is not initialized");
        return angles;
    }

    public void turn(double horizontal, double vertical) {
        var current = angles();
        initialize(current.yaw() + horizontal * .15, current.pitch() + vertical * .15);
    }

    /**
     * The target plane is ahead of the eye, independent of rear camera distance, world hits and collision retraction.
     */
    public static Vector3 virtualTarget(Vector3 eye, CameraGeometry.Angles angles, Vector3 composition, double scale, double forwardDistance) {
        if (!Double.isFinite(scale) || scale <= 0 || !Double.isFinite(forwardDistance) || forwardDistance <= 0)
            throw new IllegalArgumentException("Invalid virtual target distance or scale");
        var basis = CameraGeometry.basis(angles.yaw(), angles.pitch());
        return eye.add(basis.forward().scale(forwardDistance))
                .add(basis.right().scale(composition.y() * scale)).add(basis.up().scale(composition.z() * scale));
    }

    public static CameraGeometry.Angles aim(Vector3 eye, Vector3 target, double fallbackYaw) {
        var direction = target.add(eye.scale(-1));
        double horizontal = Math.hypot(direction.x(), direction.z());
        if (CameraGeometry.length(direction) < 1e-8) return new CameraGeometry.Angles(fallbackYaw, 0);
        double yaw = horizontal < 1e-8 ? fallbackYaw : Math.toDegrees(Math.atan2(-direction.x(), direction.z()));
        double pitch = -Math.toDegrees(Math.atan2(direction.y(), horizontal));
        return new CameraGeometry.Angles(yaw, pitch);
    }
}
