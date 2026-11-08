package yagen.waitmydawn.ninjas_free_play.animation.core;

public record Vector3(double x, double y, double z) {
    public static final Vector3 ZERO = new Vector3(0, 0, 0);

    public Vector3 {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Vector components must be finite");
        }
    }

    public Vector3 add(Vector3 other) {
        return new Vector3(x + other.x, y + other.y, z + other.z);
    }

    public double component(int axis) {
        return switch (axis) {
            case 0 -> x;
            case 1 -> y;
            case 2 -> z;
            default -> throw new IllegalArgumentException("Axis must be 0, 1, or 2");
        };
    }

    public Vector3 scale(double amount) {
        return new Vector3(x * amount, y * amount, z * amount);
    }

    public Vector3 lerp(Vector3 other, double weight) {
        return scale(1 - weight).add(other.scale(weight));
    }

    public Vector3 normalized() {
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length < 1e-12) {
            throw new IllegalArgumentException("Cannot normalize a zero-length vector");
        }
        return scale(1 / length);
    }
}
