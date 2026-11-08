package yagen.waitmydawn.ninjas_free_play.camera.core;

import yagen.waitmydawn.ninjas_free_play.animation.core.LaunchOrientation;
import yagen.waitmydawn.ninjas_free_play.animation.core.Vector3;

public final class CameraGeometry {
    public record Basis(Vector3 forward, Vector3 right, Vector3 up) {
    }

    public record Angles(double yaw, double pitch) {
    }

    private CameraGeometry() {
    }

    public static Basis basis(double yaw, double pitch) {
        var forward = LaunchOrientation.fromDegrees(yaw, pitch).direction();
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        return new Basis(forward, new Vector3(-Math.cos(y), 0, -Math.sin(y)),
                new Vector3(-Math.sin(y) * Math.sin(p), Math.cos(p), Math.cos(y) * Math.sin(p)));
    }

    public static Vector3 offset(Basis basis, double distance, double horizontal, double vertical) {
        return basis.forward().scale(-distance).add(basis.right().scale(horizontal)).add(basis.up().scale(vertical));
    }

    public static double dot(Vector3 a, Vector3 b) {
        return a.x() * b.x() + a.y() * b.y() + a.z() * b.z();
    }

    public static double length(Vector3 vector) {
        return Math.sqrt(dot(vector, vector));
    }
}
