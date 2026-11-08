package yagen.waitmydawn.ninjas_free_play.animation.core;

import java.util.Objects;

/**
 * Playback uses an external simulation clock in seconds, independent of render frame count.
 */
public final class AnimationPlayer {
    public record Sample(AnimationPose pose, double weight) {
        public static final Sample EMPTY = new Sample(AnimationPose.IDENTITY, 0);

        /**
         * Root Euler tracks retain their authored winding throughout a weighted turn.
         */
        public BonePose weightedRoot() {
            BonePose root = pose.bone(Bone.ROOT);
            return new BonePose(root.position().scale(weight), root.rotation().scale(weight), root.bend() * weight);
        }
    }

    private record WeightCurve(double from, double velocity, double to, double duration) {
        static WeightCurve create(double from, double velocity, double to, double duration) {
            // Keep all Bezier control values in [0, 1] without discarding the incoming derivative.
            if (velocity > 0) duration = Math.min(duration, 3 * (1 - from) / velocity);
            if (velocity < 0) duration = Math.min(duration, -3 * from / velocity);
            return new WeightCurve(from, velocity, to, duration);
        }

        double value(double elapsed) {
            if (duration == 0 || elapsed >= duration) return to;
            double s = Math.max(0, elapsed / duration);
            return Math.max(0, Math.min(1, from + (to - from) * s * s * (3 - 2 * s)
                    + velocity * duration * s * (1 - s) * (1 - s)));
        }

        double derivative(double elapsed) {
            if (duration == 0 || elapsed >= duration) return 0;
            double s = Math.max(0, elapsed / duration);
            return (to - from) * 6 * s * (1 - s) / duration + velocity * (1 - 4 * s + 3 * s * s);
        }
    }

    private static final double DERIVATIVE_STEP = 1e-4;
    private AnimationClip clip;
    private double startedAt;
    private double transition;
    private PoseTransition correction;
    private WeightCurve entering;
    private WeightCurve ending;
    private double stoppedAt = Double.NaN;
    private WeightCurve stopping;
    private Vector3 endingRootShift = Vector3.ZERO;
    private Vector3 stoppingRootShift = Vector3.ZERO;

    public void play(AnimationClip clip, double now, double transition) {
        Objects.requireNonNull(clip);
        requireTime(now);
        if (!Double.isFinite(transition) || transition < 0)
            throw new IllegalArgumentException("Invalid transition length");
        Sample previous = sample(now);
        Sample before = sample(now - DERIVATIVE_STEP);
        double weightVelocity = weightDerivative(now);
        this.clip = clip;
        this.startedAt = now;
        this.transition = transition;
        // A fixed simulation-time interval makes interruption independent of the last rendered frame.
        correction = previous.weight() == 0 || transition == 0 ? null
                : new PoseTransition(previous.pose(), before.weight() == 0 ? previous.pose() : before.pose(), clip.sample(0), clip.sample(DERIVATIVE_STEP),
                DERIVATIVE_STEP, transition);
        entering = WeightCurve.create(previous.weight(), weightVelocity, 1, transition);
        ending = WeightCurve.create(entering.value(clip.duration()), entering.derivative(clip.duration()), 0, transition);
        endingRootShift = !clip.loop() && entering.value(clip.duration()) == 1
                ? PoseTransition.wholeTurns(poseAt(clip.duration()).bone(Bone.ROOT).rotation()) : Vector3.ZERO;
        stoppedAt = Double.NaN;
        stoppingRootShift = Vector3.ZERO;
    }

    public void stop(double now) {
        stop(now, transition);
    }

    public void stop(double now, double fadeSeconds) {
        requireTime(now);
        if (!Double.isFinite(fadeSeconds) || fadeSeconds < 0) throw new IllegalArgumentException("Invalid fade length");
        if (clip == null || !Double.isNaN(stoppedAt)) return;
        Sample current = sample(now);
        stopping = WeightCurve.create(current.weight(), weightDerivative(now), 0, fadeSeconds);
        // Rebase only at full influence: changing a whole-turn branch at partial influence changes the visible pose.
        stoppingRootShift = current.weight() == 1
                ? PoseTransition.wholeTurns(current.pose().bone(Bone.ROOT).rotation()) : Vector3.ZERO;
        stoppedAt = now;
    }

    public Sample sample(double now) {
        requireTime(now);
        if (clip == null) return Sample.EMPTY;
        double elapsed = Math.max(0, now - startedAt);
        double weight = weightAt(now);
        if (weight == 0 && (elapsed > 0 || !Double.isNaN(stoppedAt))) return Sample.EMPTY;
        AnimationPose pose = poseAt(elapsed);
        if (!clip.loop() && elapsed >= clip.duration() && (Double.isNaN(stoppedAt) || stoppedAt >= startedAt + clip.duration())) {
            pose = PoseTransition.shiftRoot(pose, endingRootShift);
        }
        if (!Double.isNaN(stoppedAt) && now >= stoppedAt) pose = PoseTransition.shiftRoot(pose, stoppingRootShift);
        return new Sample(pose, weight);
    }

    private AnimationPose poseAt(double elapsed) {
        AnimationPose pose = clip.sample(elapsed);
        if (!clip.loop() && elapsed > clip.duration() && transition > 0) {
            double step = Math.min(DERIVATIVE_STEP, clip.duration());
            pose = PoseTransition.continueEndpoint(clip.sample(clip.duration()), clip.sample(clip.duration() - step),
                    step, elapsed - clip.duration(), transition);
        }
        return correction == null ? pose : correction.apply(pose, elapsed);
    }

    private double weightAt(double now) {
        if (!Double.isNaN(stoppedAt) && now >= stoppedAt) return stopping.value(now - stoppedAt);
        double elapsed = Math.max(0, now - startedAt);
        return !clip.loop() && elapsed >= clip.duration() ? ending.value(elapsed - clip.duration()) : entering.value(elapsed);
    }

    private double weightDerivative(double now) {
        if (clip == null) return 0;
        if (!Double.isNaN(stoppedAt) && now >= stoppedAt) return stopping.derivative(now - stoppedAt);
        double elapsed = Math.max(0, now - startedAt);
        return !clip.loop() && elapsed >= clip.duration() ? ending.derivative(elapsed - clip.duration()) : entering.derivative(elapsed);
    }

    private static void requireTime(double time) {
        if (!Double.isFinite(time)) throw new IllegalArgumentException("Clock must be finite");
    }
}
