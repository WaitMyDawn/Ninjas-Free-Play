package yagen.waitmydawn.ninjas_free_play.animation.core;

import java.util.EnumMap;
import java.util.Map;

public final class AnimationClip {
    public enum CoordinateSpace {ENTITY, LAUNCH}

    public record Track(KeyframeTrack position, KeyframeTrack rotation, KeyframeTrack bend) {
        BonePose sample(double time) {
            return new BonePose(position == null ? Vector3.ZERO : position.sample(time),
                    rotation == null ? Vector3.ZERO : rotation.sample(time), bend == null ? 0 : bend.sample(time).x());
        }
    }

    private final double duration;
    private final boolean loop;
    private final Map<Bone, Track> tracks;
    private final Map<Bone, Vector3> loopWindings;
    private final CoordinateSpace coordinateSpace;

    public AnimationClip(double duration, boolean loop, Map<Bone, Track> tracks) {
        this(duration, loop, tracks, CoordinateSpace.ENTITY);
    }

    public AnimationClip(double duration, boolean loop, Map<Bone, Track> tracks, CoordinateSpace coordinateSpace) {
        if (!Double.isFinite(duration) || duration <= 0) {
            throw new IllegalArgumentException("Animation duration must be finite and positive");
        }
        this.duration = duration;
        this.loop = loop;
        this.tracks = Map.copyOf(tracks);
        this.coordinateSpace = java.util.Objects.requireNonNull(coordinateSpace);
        for (Track track : tracks.values()) {
            for (KeyframeTrack channel : new KeyframeTrack[]{track.position(), track.rotation(), track.bend()}) {
                if (channel != null && channel.endTime() > duration) {
                    throw new IllegalArgumentException("Keyframe exceeds animation duration");
                }
            }
        }
        EnumMap<Bone, Vector3> windings = new EnumMap<>(Bone.class);
        if (loop) tracks.forEach((bone, track) -> {
            if (track.rotation() == null) return;
            Vector3 delta = track.rotation().sample(duration).add(track.rotation().sample(0).scale(-1));
            Vector3 turns = PoseTransition.wholeTurns(delta);
            Vector3 remainder = delta.add(turns.scale(-1));
            if (Math.abs(remainder.x()) <= 1e-6 && Math.abs(remainder.y()) <= 1e-6 && Math.abs(remainder.z()) <= 1e-6
                    && !turns.equals(Vector3.ZERO)) windings.put(bone, turns);
        });
        loopWindings = Map.copyOf(windings);
    }

    public double duration() {
        return duration;
    }

    public boolean loop() {
        return loop;
    }

    public CoordinateSpace coordinateSpace() {
        return coordinateSpace;
    }

    public AnimationClip retimed(double seconds) {
        if (!Double.isFinite(seconds) || seconds <= 0) throw new IllegalArgumentException("Invalid clip duration");
        double factor = seconds / duration;
        EnumMap<Bone, Track> result = new EnumMap<>(Bone.class);
        tracks.forEach((bone, track) -> result.put(bone, new Track(
                track.position() == null ? null : track.position().retimed(factor, seconds),
                track.rotation() == null ? null : track.rotation().retimed(factor, seconds),
                track.bend() == null ? null : track.bend().retimed(factor, seconds))));
        return new AnimationClip(seconds, loop, result, coordinateSpace);
    }

    public static AnimationClip still(AnimationPose pose) {
        EnumMap<Bone, Track> tracks = new EnumMap<>(Bone.class);
        for (Bone bone : Bone.values()) {
            BonePose part = pose.bone(bone);
            tracks.put(bone, new Track(constant(part.position()), constant(part.rotation()),
                    constant(new Vector3(part.bend(), 0, 0))));
        }
        return new AnimationClip(1, true, tracks);
    }

    private static KeyframeTrack constant(Vector3 value) {
        return new KeyframeTrack(java.util.List.of(new KeyframeTrack.Keyframe(0, value)), KeyframeTrack.Interpolation.LINEAR);
    }

    public AnimationPose sample(double elapsed) {
        if (!Double.isFinite(elapsed)) throw new IllegalArgumentException("Elapsed time must be finite");
        double time = Math.max(0, elapsed);
        double cycles = loop ? Math.floor(time / duration) : 0;
        time = loop ? time % duration : Math.min(time, duration);
        EnumMap<Bone, BonePose> result = new EnumMap<>(Bone.class);
        double sampleTime = time;
        tracks.forEach((bone, track) -> {
            BonePose pose = track.sample(sampleTime);
            Vector3 winding = loopWindings.get(bone);
            // Preserve complete turns across a loop seam, including while influence is only partial.
            if (winding != null && cycles > 0)
                pose = new BonePose(pose.position(), pose.rotation().add(winding.scale(cycles)), pose.bend());
            result.put(bone, pose);
        });
        return new AnimationPose(result);
    }
}
