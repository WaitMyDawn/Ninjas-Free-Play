package yagen.waitmydawn.ninjas_free_play.core;

import yagen.waitmydawn.ninjas_free_play.animation.core.Vector3;

/**
 * Launch geometry is retained independently of the wall currently detected during flight.
 */
public record WallDashPlan(WallDashKind kind, int normalX, int normalZ) {
    public static final WallDashPlan NONE = new WallDashPlan(WallDashKind.NONE, 0, 0);

    public WallDashPlan {
        if (kind == null || normalX < -1 || normalX > 1 || normalZ < -1 || normalZ > 1
                || Math.abs(normalX) + Math.abs(normalZ) != (kind == WallDashKind.NONE ? 0 : 1))
            throw new IllegalArgumentException("Invalid wall dash plan");
    }

    public static WallDashPlan resolve(MovementInput input, WallContact wall) {
        if (!wall.present()) return NONE;
        double yaw = Math.toRadians(input.yaw());
        double normal = -Math.sin(yaw) * wall.x() + Math.cos(yaw) * wall.z();
        double left = Math.sin(yaw) * wall.z() + Math.cos(yaw) * wall.x();
        double forwardNormal, forwardLeft;
        // The parallel sector includes both boundaries at 30 degrees from the wall plane.
        if (normal < -0.5 - 1e-7) {
            forwardNormal = -1;
            forwardLeft = 0;
        } else if (normal > 0.5 + 1e-7) {
            forwardNormal = 1;
            forwardLeft = 0;
        } else {
            forwardNormal = 0;
            forwardLeft = left >= 0 ? 1 : -1;
        }
        double toward = forwardNormal * input.forward() + forwardLeft * input.strafe();
        double along = forwardLeft * input.forward() - forwardNormal * input.strafe();
        WallDashKind kind;
        if (Math.abs(toward) >= Math.abs(along) && Math.abs(toward) > 1e-6)
            kind = toward > 0 ? WallDashKind.AWAY : WallDashKind.UP;
        else if (Math.abs(along) > 1e-6) kind = along > 0 ? WallDashKind.LEFT : WallDashKind.RIGHT;
        else kind = normal > 0.5 ? WallDashKind.AWAY : WallDashKind.UP;
        return new WallDashPlan(kind, wall.x(), wall.z());
    }

    public Vector3 direction() {
        double normal = kind == WallDashKind.AWAY ? 0.85 : -0.06;
        double vertical = kind == WallDashKind.UP ? 1 : kind == WallDashKind.AWAY ? 0.55 : 0.32;
        double along = kind == WallDashKind.LEFT ? 0.94 : kind == WallDashKind.RIGHT ? -0.94 : 0;
        return kind == WallDashKind.NONE ? Vector3.ZERO
                : new Vector3(normalX * normal - normalZ * along, vertical, normalZ * normal + normalX * along).normalized();
    }

    public String animation() {
        return switch (kind) {
            case LEFT -> "wall_dash_left";
            case RIGHT -> "wall_dash_right";
            case AWAY -> "wall_dash_away";
            case UP, NONE -> "wall_dash";
        };
    }
}
