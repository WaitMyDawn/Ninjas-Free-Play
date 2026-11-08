package yagen.waitmydawn.ninjas_free_play.animation.core;

import java.util.List;

/**
 * Contact weights use normalized clip phase, so timing remains valid after animation retiming.
 */
public final class HandContactProfile {
    public record Weights(double left, double right) {
    }

    public record Key(double phase, double left, double right) {
        public Key {
            if (!Double.isFinite(phase) || phase < 0 || phase > 1 || !Double.isFinite(left) || left < 0 || left > 1
                    || !Double.isFinite(right) || right < 0 || right > 1)
                throw new IllegalArgumentException("Invalid hand contact key");
        }
    }

    public static final HandContactProfile NONE = new HandContactProfile(List.of(new Key(0, 0, 0), new Key(1, 0, 0)));
    private final List<Key> keys;

    public HandContactProfile(List<Key> keys) {
        this.keys = List.copyOf(keys);
        if (keys.size() < 2 || keys.get(0).phase() != 0 || keys.get(keys.size() - 1).phase() != 1) {
            throw new IllegalArgumentException("Hand contacts must cover the complete clip phase");
        }
        for (int i = 1; i < keys.size(); i++)
            if (keys.get(i).phase() <= keys.get(i - 1).phase()) {
                throw new IllegalArgumentException("Hand contact phases must increase");
            }
    }

    public Weights sample(double phase) {
        if (!Double.isFinite(phase)) throw new IllegalArgumentException("Non-finite contact phase");
        phase = Math.max(0, Math.min(1, phase));
        for (int i = 1; i < keys.size(); i++) {
            Key left = keys.get(i - 1), right = keys.get(i);
            if (phase <= right.phase()) {
                double t = (phase - left.phase()) / (right.phase() - left.phase());
                t = t * t * (3 - 2 * t);
                return new Weights(left.left() + (right.left() - left.left()) * t,
                        left.right() + (right.right() - left.right()) * t);
            }
        }
        return new Weights(0, 0);
    }
}
