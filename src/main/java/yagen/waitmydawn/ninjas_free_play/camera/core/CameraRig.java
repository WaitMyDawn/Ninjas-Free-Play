package yagen.waitmydawn.ninjas_free_play.camera.core;

import yagen.waitmydawn.ninjas_free_play.animation.core.Vector3;

/**
 * Smooths composition while collision always limits the complete path from the current physical eye.
 */
public final class CameraRig {
    @FunctionalInterface
    public interface Sweep {
        double fraction(Vector3 from, Vector3 to, double radius);
    }

    /**
     * Composition stores unscaled backward/right/up offsets before collision contraction.
     */
    public record Frame(Vector3 position, CameraGeometry.Angles angles, double collisionFraction, Vector3 composition) {
    }

    private Vector3 anchor, lastEye, entryLocal, targetLocal, lastLocal;
    private double started, lastTime, clearance = 1;

    public void reset() {
        anchor = null;
        lastEye = null;
        entryLocal = null;
        targetLocal = null;
        lastLocal = null;
        clearance = 1;
    }

    public Frame sample(double now, Vector3 eye, double yaw, double pitch, Vector3 vanillaOffset,
                        double scale, CameraSettings settings, Sweep sweep) {
        if (!Double.isFinite(now) || !Double.isFinite(scale) || scale <= 0)
            throw new IllegalArgumentException("Invalid camera sample");
        var basis = CameraGeometry.basis(yaw, pitch);
        var requested = new Vector3(settings.distance(), settings.horizontal(), settings.vertical());
        if (anchor == null || now < lastTime || CameraGeometry.length(eye.add(lastEye.scale(-1))) > 8 * scale) {
            anchor = eye;
            started = now;
            lastTime = now;
            clearance = 1;
            entryLocal = new Vector3(-CameraGeometry.dot(vanillaOffset, basis.forward()) / scale,
                    CameraGeometry.dot(vanillaOffset, basis.right()) / scale, CameraGeometry.dot(vanillaOffset, basis.up()) / scale);
            targetLocal = requested;
            lastLocal = entryLocal;
        }
        if (!requested.equals(targetLocal)) {
            entryLocal = lastLocal;
            targetLocal = requested;
            started = now;
        }
        double dt = Math.max(0, now - lastTime);
        anchor = anchor.lerp(eye, response(dt, settings.followSeconds()));
        var lag = anchor.add(eye.scale(-1));
        double lagLength = CameraGeometry.length(lag);
        if (lagLength > .5 * scale) anchor = eye.add(lag.scale(.5 * scale / lagLength));
        double phase = settings.transitionSeconds() == 0 ? 1 : Math.max(0, Math.min(1, (now - started) / settings.transitionSeconds()));
        double blend = phase * phase * (3 - 2 * phase);
        lastLocal = entryLocal.lerp(targetLocal, blend);
        var local = lastLocal.scale(scale);
        var desired = anchor.add(CameraGeometry.offset(basis, local.x(), local.y(), local.z()));
        double allowed = sweep.fraction(eye, desired, settings.radius() * scale);
        if (!Double.isFinite(allowed) || allowed < 0 || allowed > 1)
            throw new IllegalArgumentException("Invalid camera sweep result");
        // Retraction is immediate; only movement back into newly free space is smoothed.
        clearance = allowed <= clearance ? allowed : clearance + (allowed - clearance) * response(dt, settings.recoverySeconds());
        var position = eye.lerp(desired, clearance);
        lastEye = eye;
        lastTime = now;
        return new Frame(position, new CameraGeometry.Angles(yaw, pitch), clearance, lastLocal);
    }

    private static double response(double dt, double seconds) {
        return seconds == 0 ? 1 : -Math.expm1(-dt / seconds);
    }
}
