package yagen.waitmydawn.ninjas_free_play.animation.core;

/**
 * Bounded visual root correction; nullable surface hits cannot create a support point in empty air.
 */
public final class GroundContactSolver {
    public record Hand(Vector3 position, Vector3 surface, double weight) {
        public Hand {
            if (position == null || !Double.isFinite(weight) || weight < 0 || weight > 1)
                throw new IllegalArgumentException("Invalid hand observation");
        }
    }

    private Vector3 leftAnchor;
    private Vector3 rightAnchor;

    public Vector3 solve(Hand left, Hand right, double scale) {
        if (!Double.isFinite(scale) || scale <= 0) throw new IllegalArgumentException("Invalid grounding scale");
        leftAnchor = anchor(leftAnchor, left);
        rightAnchor = anchor(rightAnchor, right);
        double total = 0;
        Vector3 correction = Vector3.ZERO;
        if (leftAnchor != null) {
            correction = correction.add(leftAnchor.add(left.position().scale(-1)).scale(left.weight()));
            total += left.weight();
        }
        if (rightAnchor != null) {
            correction = correction.add(rightAnchor.add(right.position().scale(-1)).scale(right.weight()));
            total += right.weight();
        }
        if (total == 0) return Vector3.ZERO;
        correction = correction.scale(1 / Math.max(1, total));
        double horizontal = Math.hypot(correction.x(), correction.z());
        double amount = horizontal > 0.45 * scale ? 0.45 * scale / horizontal : 1;
        return new Vector3(correction.x() * amount, Math.max(-0.8 * scale, Math.min(0.8 * scale, correction.y())), correction.z() * amount);
    }

    private static Vector3 anchor(Vector3 existing, Hand hand) {
        if (hand.weight() <= 0 || hand.surface() == null) return null;
        return existing == null ? hand.surface() : existing;
    }
}
