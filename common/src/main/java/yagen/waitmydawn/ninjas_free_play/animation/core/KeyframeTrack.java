package yagen.waitmydawn.ninjas_free_play.animation.core;

import java.util.List;
import java.util.Objects;

public final class KeyframeTrack {
    public enum Interpolation {LINEAR, SMOOTH, STEP, BEZIER}

    public record Keyframe(double time, Vector3 value, Interpolation interpolation,
                           Vector3 leftTime, Vector3 leftValue, Vector3 rightTime, Vector3 rightValue) {
        public Keyframe(double time, Vector3 value) {
            this(time, value, null, Vector3.ZERO, Vector3.ZERO, Vector3.ZERO, Vector3.ZERO);
        }

        public Keyframe {
            if (!Double.isFinite(time) || time < 0) {
                throw new IllegalArgumentException("Keyframe time must be finite and non-negative");
            }
            Objects.requireNonNull(value);
            Objects.requireNonNull(leftTime);
            Objects.requireNonNull(leftValue);
            Objects.requireNonNull(rightTime);
            Objects.requireNonNull(rightValue);
            for (int axis = 0; axis < 3; axis++) {
                if (leftTime.component(axis) > 0 || rightTime.component(axis) < 0) {
                    throw new IllegalArgumentException("Bezier time handles must face their adjacent keyframes");
                }
            }
        }
    }

    private final List<Keyframe> keyframes;
    private final Interpolation interpolation;

    public KeyframeTrack(List<Keyframe> keyframes, Interpolation interpolation) {
        this.keyframes = List.copyOf(keyframes);
        this.interpolation = Objects.requireNonNull(interpolation);
        if (keyframes.isEmpty() || keyframes.get(0).time() != 0) {
            throw new IllegalArgumentException("Track must begin at time zero");
        }
        for (int index = 1; index < keyframes.size(); index++) {
            if (keyframes.get(index).time() <= keyframes.get(index - 1).time()) {
                throw new IllegalArgumentException("Keyframe times must be strictly increasing");
            }
        }
    }

    public double endTime() {
        return keyframes.get(keyframes.size() - 1).time();
    }

    public KeyframeTrack retimed(double factor, double duration) {
        if (!Double.isFinite(factor) || factor <= 0 || !Double.isFinite(duration) || duration <= 0) {
            throw new IllegalArgumentException("Invalid track time scale");
        }
        // Bound roundoff at the final endpoint without changing the already-validated source timeline.
        return new KeyframeTrack(keyframes.stream().map(key -> new Keyframe(Math.min(duration, key.time() * factor), key.value(),
                key.interpolation(), key.leftTime().scale(factor), key.leftValue(),
                key.rightTime().scale(factor), key.rightValue())).toList(), interpolation);
    }

    public List<Keyframe> keyframes() {
        return keyframes;
    }

    public Vector3 sample(double time) {
        if (!Double.isFinite(time)) {
            throw new IllegalArgumentException("Sample time must be finite");
        }
        if (time <= 0) return keyframes.get(0).value();
        for (int index = 1; index < keyframes.size(); index++) {
            Keyframe right = keyframes.get(index);
            if (time < right.time()) {
                Keyframe left = keyframes.get(index - 1);
                double weight = (time - left.time()) / (right.time() - left.time());
                Interpolation mode = left.interpolation() == null ? interpolation : left.interpolation();
                if (mode != Interpolation.STEP && (mode == Interpolation.BEZIER || right.interpolation() == Interpolation.BEZIER)) {
                    return new Vector3(bezier(left, right, time, 0), bezier(left, right, time, 1), bezier(left, right, time, 2));
                }
                weight = switch (mode) {
                    case LINEAR -> weight;
                    case SMOOTH -> weight * weight * (3 - 2 * weight);
                    case STEP -> 0;
                    case BEZIER -> throw new IllegalStateException("Bezier must use its time handles");
                };
                // Tracks retain authored turns; shortest-path wrapping only applies to pose transitions.
                return left.value().lerp(right.value(), weight);
            }
        }
        return keyframes.get(keyframes.size() - 1).value();
    }

    private static double bezier(Keyframe left, Keyframe right, double time, int axis) {
        double gap = right.time() - left.time();
        double firstTime = Math.min(gap, left.rightTime().component(axis));
        double secondTime = gap + Math.max(-gap, right.leftTime().component(axis));
        double target = time - left.time();
        double low = 0, high = 1;
        for (int iteration = 0; iteration < 48; iteration++) {
            double parameter = (low + high) / 2;
            if (cubic(0, firstTime, secondTime, gap, parameter) < target) low = parameter;
            else high = parameter;
        }
        double from = left.value().component(axis), to = right.value().component(axis);
        return cubic(from, from + left.rightValue().component(axis), to + right.leftValue().component(axis), to, (low + high) / 2);
    }

    private static double cubic(double a, double b, double c, double d, double time) {
        double inverse = 1 - time;
        return inverse * inverse * inverse * a + 3 * inverse * inverse * time * b
                + 3 * inverse * time * time * c + time * time * time * d;
    }
}
