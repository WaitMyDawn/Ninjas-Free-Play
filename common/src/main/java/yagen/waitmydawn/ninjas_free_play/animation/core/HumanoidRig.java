package yagen.waitmydawn.ninjas_free_play.animation.core;

import java.util.EnumMap;
import java.util.Map;

public final class HumanoidRig {
    public record Frame(Vector3 position, Rotation rotation) {
        public Vector3 transform(Vector3 point) {
            return position.add(rotation.apply(point));
        }

        Frame child(Vector3 bind, BonePose pose) {
            return new Frame(transform(bind.add(pose.position())), rotation.multiply(Rotation.fromEuler(pose.rotation())));
        }
    }

    private HumanoidRig() {
    }

    public static Vector3 palmAnchor(Bone bone, boolean slim) {
        if (bone != Bone.LEFT_ARM && bone != Bone.RIGHT_ARM)
            throw new IllegalArgumentException("Palm anchor requires an arm");
        double side = bone == Bone.LEFT_ARM ? 1 : -1;
        return new Vector3(side * (slim ? 0.5 : 1), slim ? 9.5 : 10, 0);
    }

    public static double bendCenter(Bone bone) {
        return switch (bone) {
            case LEFT_ARM, RIGHT_ARM -> 4;
            case LEFT_LEG, RIGHT_LEG -> 6;
            default -> 0;
        };
    }

    public static Bone parent(Bone bone) {
        return switch (bone) {
            case ROOT -> null;
            case BODY, LEFT_LEG, RIGHT_LEG -> Bone.ROOT;
            case HEAD, LEFT_ARM, RIGHT_ARM -> Bone.BODY;
        };
    }

    /**
     * Absolute bind pivots in the model's Y-down coordinate space, before animation offsets.
     */
    public static Vector3 bindAnchor(Bone bone, boolean lowerJoint, boolean slim) {
        Vector3 anchor = switch (bone) {
            case ROOT, BODY -> new Vector3(0, 12, 0);
            case HEAD -> Vector3.ZERO;
            case LEFT_ARM -> new Vector3(5, slim ? 2.5 : 2, 0);
            case RIGHT_ARM -> new Vector3(-5, slim ? 2.5 : 2, 0);
            case LEFT_LEG -> new Vector3(1.9, 12, 0);
            case RIGHT_LEG -> new Vector3(-1.9, 12, 0);
        };
        return lowerJoint ? anchor.add(new Vector3(0, bendCenter(bone), 0)) : anchor;
    }

    /**
     * The renderer applies ROOT once to the complete player, including equipment layers.
     */
    public static Map<Bone, Frame> evaluateBody(AnimationPose pose, boolean slim) {
        Frame root = new Frame(Vector3.ZERO, Rotation.IDENTITY);
        Vector3 hip = bindAnchor(Bone.BODY, false, slim);
        Frame body = root.child(hip, pose.bone(Bone.BODY));
        EnumMap<Bone, Frame> frames = new EnumMap<>(Bone.class);
        frames.put(Bone.ROOT, root);
        frames.put(Bone.BODY, new Frame(body.transform(hip.scale(-1)), body.rotation()));
        for (Bone bone : new Bone[]{Bone.HEAD, Bone.LEFT_ARM, Bone.RIGHT_ARM}) {
            frames.put(bone, body.child(bindAnchor(bone, false, slim).add(hip.scale(-1)), pose.bone(bone)));
        }
        for (Bone bone : new Bone[]{Bone.LEFT_LEG, Bone.RIGHT_LEG}) {
            frames.put(bone, root.child(bindAnchor(bone, false, slim), pose.bone(bone)));
        }
        return Map.copyOf(frames);
    }
}
