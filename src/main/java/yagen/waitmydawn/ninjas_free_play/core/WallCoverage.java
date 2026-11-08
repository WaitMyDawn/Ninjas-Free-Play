package yagen.waitmydawn.ninjas_free_play.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Exact rectangle-union coverage, avoiding point probes that can miss gaps in a collision shape.
 */
public final class WallCoverage {
    public record Rectangle(double left, double right, double bottom, double top) {
        public Rectangle {
            if (!Double.isFinite(left) || !Double.isFinite(right) || !Double.isFinite(bottom) || !Double.isFinite(top) || left >= right || bottom >= top)
                throw new IllegalArgumentException("Invalid collision face");
        }
    }

    private WallCoverage() {
    }

    public static boolean covers(List<Rectangle> faces, double left, double right, double bottom, double top) {
        if (left >= right || bottom >= top) throw new IllegalArgumentException("Empty wall footprint");
        List<Double> levels = new ArrayList<>();
        levels.add(bottom);
        levels.add(top);
        for (var face : faces) {
            if (face.bottom() > bottom && face.bottom() < top) levels.add(face.bottom());
            if (face.top() > bottom && face.top() < top) levels.add(face.top());
        }
        levels.sort(Double::compare);
        for (int i = 1; i < levels.size(); i++) {
            if (levels.get(i) - levels.get(i - 1) < 1e-8) continue;
            double y = (levels.get(i) + levels.get(i - 1)) / 2;
            List<Rectangle> row = faces.stream().filter(f -> f.bottom() <= y && f.top() >= y)
                    .sorted(Comparator.comparingDouble(Rectangle::left)).toList();
            double covered = left;
            for (var face : row) {
                if (face.left() > covered + 1e-7) break;
                if (face.right() > covered) covered = face.right();
            }
            if (covered < right - 1e-7) return false;
        }
        return true;
    }
}
