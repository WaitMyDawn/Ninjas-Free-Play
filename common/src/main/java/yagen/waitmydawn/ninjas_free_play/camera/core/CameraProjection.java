package yagen.waitmydawn.ninjas_free_play.camera.core;

import yagen.waitmydawn.ninjas_free_play.animation.core.Vector3;

/**
 * Projects a camera-relative world point with the actual column-major world-render view/projection matrix.
 */
public final class CameraProjection {
    public record Point(double x, double y, boolean visible) {
        public static final Point HIDDEN = new Point(0, 0, false);
    }

    private CameraProjection() {
    }

    public static Point project(Vector3 relative, double[] matrix) {
        if (matrix.length != 16) throw new IllegalArgumentException("A view-projection matrix requires 16 entries");
        for (double value : matrix)
            if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite view-projection matrix");
        double x = matrix[0] * relative.x() + matrix[4] * relative.y() + matrix[8] * relative.z() + matrix[12];
        double y = matrix[1] * relative.x() + matrix[5] * relative.y() + matrix[9] * relative.z() + matrix[13];
        double w = matrix[3] * relative.x() + matrix[7] * relative.y() + matrix[11] * relative.z() + matrix[15];
        if (!Double.isFinite(w) || w <= 1e-7) return Point.HIDDEN;
        x /= w;
        y /= w;
        if (!Double.isFinite(x) || !Double.isFinite(y)) return Point.HIDDEN;
        return new Point(x, y, Math.abs(x) <= 1 && Math.abs(y) <= 1);
    }
}
