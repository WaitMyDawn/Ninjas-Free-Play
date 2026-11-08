package yagen.waitmydawn.ninjas_free_play.animation.core;

/**
 * Pixel-to-world transform shared with the default player renderer's post-scale root pivot.
 */
public final class PlayerModelSpace {
    public static final double ORIGIN = 1.501;
    public static final double ROOT_PIVOT = 0.75;
    public static final double RENDER_SCALE = 0.9375;

    private PlayerModelSpace() {
    }

    public static Vector3 offset(Vector3 point, Vector3 rootPosition, Rotation rootRotation, Rotation base, double height, double scale) {
        Vector3 model = rootRotation.apply(point.scale(1 / 16.0).add(new Vector3(0, -ROOT_PIVOT, 0)))
                .add(rootPosition.scale(1 / 16.0)).add(new Vector3(0, -ORIGIN + ROOT_PIVOT, 0));
        return base.apply(new Vector3(model.x(), model.y() * height, model.z()).scale(RENDER_SCALE * scale));
    }
}
