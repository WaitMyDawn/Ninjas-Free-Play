package yagen.waitmydawn.ninjas_free_play.animation;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import yagen.waitmydawn.ninjas_free_play.animation.core.*;
import yagen.waitmydawn.ninjas_free_play.animation.resource.*;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class SupportedPoseTest {
    private AnimationClip clip(String id) throws Exception {
        try (var reader = Files.newBufferedReader(Path.of("build/generated/animation-resources/assets/ninjas_free_play/ninja_animations/" + id + ".json"))) {
            return AnimationJsonCodec.read(reader);
        }
    }
    private Vector3 point(AnimationPose pose, Bone bone, double y) {
        var frame = HumanoidRig.evaluateBody(pose, false).get(bone);
        double center = HumanoidRig.bendCenter(bone);
        Vector3 local = new LimbDeformer(pose.bone(bone).bend(), center, 10).position(new Vector3(0, y, 0));
        return frame.transform(local);
    }
    private Vector3 world(AnimationPose pose, Vector3 point) {
        var root = pose.bone(Bone.ROOT);
        return PlayerModelSpace.offset(point, root.position(), Rotation.fromEuler(root.rotation()), LaunchOrientation.entityModelRotation(0), 1, 1);
    }

    @Test void rearKneeStaysAboveTheFloorAndPointsForward() throws Exception {
        var pose = clip("crouch_idle").sample(0);
        var rearKnee = world(pose, point(pose, Bone.RIGHT_LEG, 6));
        var rearHip = world(pose, HumanoidRig.evaluateBody(pose, false).get(Bone.RIGHT_LEG).position());
        assertTrue(rearKnee.y() > 0.06 && rearKnee.y() < 0.35, rearKnee.toString());
        assertTrue(rearKnee.z() > rearHip.z() + 0.02);
        assertEquals(Math.PI / 2, pose.bone(Bone.LEFT_LEG).bend(), 1e-6);
        assertEquals(Math.PI / 2, -pose.bone(Bone.LEFT_LEG).rotation().x(), 1e-6);
        assertTrue(Math.abs(world(pose, point(pose, Bone.LEFT_LEG, 12)).y()) < 0.005);
        assertTrue(point(pose, Bone.LEFT_ARM, 12).x() > 4);
        assertTrue(point(pose, Bone.RIGHT_ARM, 12).x() < -4);
    }

    @Test void eachRollTurnsTowardItsIntendedLocalDirection() throws Exception {
        String[] ids = {"roll", "roll_left", "roll_right", "roll_backward"};
        Vector3[] expected = {new Vector3(0, 0, 1), new Vector3(1, 0, 0), new Vector3(-1, 0, 0), new Vector3(0, 0, -1)};
        for (int i = 0; i < ids.length; i++) {
            var source = clip(ids[i]);
            Vector3 up = LaunchOrientation.entityModelRotation(0).apply(Rotation.fromEuler(source.sample(source.duration() * 0.25)
                    .bone(Bone.ROOT).rotation()).apply(new Vector3(0, -1, 0)));
            double along = up.x() * expected[i].x() + up.z() * expected[i].z();
            assertTrue(along > 0.7, ids[i] + ": " + up);
        }
    }

    @Test void crouchedArmsRestAheadOfTheFeetWithoutAHuggingPose() throws Exception {
        for (String id : new String[]{"crouch_idle","crouch_walk"}) {
            var clip=clip(id);
            for (int i=0;i<16;i++) {
                var pose=clip.sample(clip.duration()*i/16);
                var frames=HumanoidRig.evaluateBody(pose,false);
                for (Bone arm : new Bone[]{Bone.LEFT_ARM,Bone.RIGHT_ARM}) {
                    double side=arm==Bone.LEFT_ARM ? 1 : -1;
                    var shoulder=frames.get(arm).position();
                    var elbow=point(pose,arm,4);
                    var palm=frames.get(arm).transform(new LimbDeformer(pose.bone(arm).bend(),4,10)
                            .position(HumanoidRig.palmAnchor(arm,false)));
                    assertTrue(side*(elbow.x()-shoulder.x())>0.5, id+" needs relaxed lateral elbow clearance");
                    assertTrue(side*(palm.x()-elbow.x())>1, id+" forearms must open naturally instead of curling into a hug");
                    assertTrue(Math.abs(pose.bone(arm).bend())>Math.toRadians(30) && Math.abs(pose.bone(arm).bend())<Math.toRadians(50));
                    for (double yaw : new double[]{0, Math.PI/2, -Math.PI/2, Math.PI}) {
                        var root=pose.bone(Bone.ROOT);
                        var position=PlayerModelSpace.offset(palm,root.position(),Rotation.fromEuler(root.rotation()),
                                LaunchOrientation.entityModelRotation(yaw),1,1);
                        double forward=-Math.sin(yaw)*position.x()+Math.cos(yaw)*position.z();
                        assertTrue(forward>0.15 && forward<0.6,id+" hands must rest ahead of the feet: "+position);
                        assertTrue(position.y()>0.15 && position.y()<0.65,id+" hands must stay clear of the ground");
                    }
                }
            }
        }
    }

    @Test void crouchedForearmsPointForwardFromTheElbowsForBothSkinWidths() throws Exception {
        for (String id : new String[]{"crouch_idle","crouch_walk"}) {
            var source=clip(id);
            for (boolean slim : new boolean[]{false,true}) for (int i=0;i<32;i++) {
                var pose=source.sample(source.duration()*i/32);
                var frames=HumanoidRig.evaluateBody(pose,slim);
                for (Bone arm : new Bone[]{Bone.LEFT_ARM,Bone.RIGHT_ARM}) {
                    var frame=frames.get(arm);
                    var deform=new LimbDeformer(pose.bone(arm).bend(),HumanoidRig.bendCenter(arm),10);
                    var elbow=frame.transform(deform.position(new Vector3(0,4,0)));
                    var palm=frame.transform(deform.position(HumanoidRig.palmAnchor(arm,slim)));
                    var root=pose.bone(Bone.ROOT);
                    for (int heading=0;heading<8;heading++) {
                        double yaw=heading*Math.PI/4;
                        var base=LaunchOrientation.entityModelRotation(yaw);
                        var from=PlayerModelSpace.offset(elbow,root.position(),Rotation.fromEuler(root.rotation()),base,1,1);
                        var to=PlayerModelSpace.offset(palm,root.position(),Rotation.fromEuler(root.rotation()),base,1,1);
                        double forward=-Math.sin(yaw)*(to.x()-from.x())+Math.cos(yaw)*(to.z()-from.z());
                        assertTrue(forward>0.05,id+" palm must be ahead of its elbow: "+forward);
                    }
                }
            }
        }
    }

    @Test void crouchedForearmMeshesClearTheArmoredTorsoBelowTheShoulderJoint() throws Exception {
        for (String id : new String[]{"crouch_idle","crouch_walk"}) {
            var source=clip(id);
            for (boolean slim : new boolean[]{false,true}) for (int i=0;i<32;i++) {
                var pose=source.sample(source.duration()*i/32);
                var frames=HumanoidRig.evaluateBody(pose,slim);
                var body=frames.get(Bone.BODY);
                for (Bone arm : new Bone[]{Bone.LEFT_ARM,Bone.RIGHT_ARM}) {
                    double side=arm==Bone.LEFT_ARM ? 1 : -1;
                    double inner=-1,outer=slim ? 2 : 3;
                    var frame=frames.get(arm);
                    var deform=new LimbDeformer(pose.bone(arm).bend(),HumanoidRig.bendCenter(arm),10);
                    // One-pixel inflation covers the standard outer armor mesh; the shoulder attachment is excluded.
                    for (double y=4;y<=11;y+=0.5) for (double x : new double[]{side*(inner-1),side*(outer+1)}) {
                        for (double z : new double[]{-3,3}) {
                            var point=frame.transform(deform.position(new Vector3(x,y,z)));
                            var local=body.rotation().inverse().apply(point.add(body.position().scale(-1)));
                            assertTrue(side*local.x()>5.25,id+" forearm must clear the armored torso: "+local);
                        }
                    }
                }
            }
        }
    }

    @Test void crouchedTorsoRisesAboveTheGroundAndKeepsTheHeadAttached() throws Exception {
        for (String id : new String[]{"crouch_idle","crouch_walk"}) {
            var source=clip(id);
            for (int i=0;i<16;i++) {
                var pose=source.sample(source.duration()*i/16);
                var frames=HumanoidRig.evaluateBody(pose,false);
                var up=LaunchOrientation.entityModelRotation(0).apply(frames.get(Bone.BODY).rotation().apply(new Vector3(0,-1,0)));
                double elevation=Math.toDegrees(Math.atan2(up.y(),Math.hypot(up.x(),up.z())));
                assertTrue(elevation>=30 && elevation<=45,id+" torso elevation: "+elevation);
                assertEquals(Vector3.ZERO,pose.bone(Bone.HEAD).position(),"The head must stay at its neck pivot");
                var head=frames.get(Bone.HEAD).rotation().apply(new Vector3(0,-1,0));
                assertEquals(-1,head.y(),1e-6,"The head must counter the torso lean");
            }
        }
    }

    @Test void authoredSupportPosesMeetTheFloorWithTheActiveHand() throws Exception {
        for (String id : new String[]{"roll_left", "roll_right", "roll_backward"}) {
            var source = clip(id);
            HandContactProfile profile;
            try (var reader = Files.newBufferedReader(Path.of("src/main/resources/assets/ninjas_free_play/ninja_contacts/" + id + ".json"))) {
                profile = HandContactCodec.read(JsonParser.parseReader(reader));
            }
            for (double phase : new double[]{0.3, 0.4, 0.5, 0.6}) {
                var pose = source.sample(source.duration() * phase);
                var weight = profile.sample(phase);
                if (weight.left() == 1) assertEquals(0, world(pose, point(pose, Bone.LEFT_ARM, 10)).y(), 0.03, id);
                if (weight.right() == 1) assertEquals(0, world(pose, point(pose, Bone.RIGHT_ARM, 10)).y(), 0.03, id);
            }
            var inverted = source.sample(source.duration() * 0.5);
            var headFrame = HumanoidRig.evaluateBody(inverted, false).get(Bone.HEAD);
            for (double x : new double[]{-4,4}) for (double z : new double[]{-4,4}) {
                assertTrue(world(inverted, headFrame.transform(new Vector3(x,-8,z))).y() > 0.05,
                        id + " must support the body on its hands, clear of the head");
            }
        }
    }

    @Test void contactAnchorStaysFixedWhileThePlayerContinuesMoving() {
        var solver = new GroundContactSolver();
        var empty = new GroundContactSolver.Hand(Vector3.ZERO, null, 0);
        var first = solver.solve(new GroundContactSolver.Hand(new Vector3(0, 0.1, 0), Vector3.ZERO, 1), empty, 1);
        assertEquals(-0.1, first.y(), 1e-9);
        Vector3 wrist = new Vector3(0.2, 0.2, 0.1);
        var correction = solver.solve(new GroundContactSolver.Hand(wrist, new Vector3(0.2, 0, 0.1), 1), empty, 1);
        Vector3 planted = wrist.add(correction);
        assertEquals(0, planted.x(), 1e-9);
        assertEquals(0, planted.y(), 1e-9);
        assertEquals(0, planted.z(), 1e-9);
    }

    @Test void missingGroundCannotSupplyAnAnchorAndCorrectionsStayBounded() {
        var solver = new GroundContactSolver();
        var hand = new GroundContactSolver.Hand(new Vector3(2, 3, 4), null, 1);
        assertEquals(Vector3.ZERO, solver.solve(hand, hand, 1));
        var distant = new GroundContactSolver.Hand(new Vector3(2, 3, 4), Vector3.ZERO, 1);
        var offset = solver.solve(distant, hand, 1);
        assertTrue(Math.hypot(offset.x(), offset.z()) <= 0.45 + 1e-9);
        assertTrue(Math.abs(offset.y()) <= 0.8);
        assertEquals(Vector3.ZERO, solver.solve(hand, hand, 1));
    }

    @Test void contactContractRejectsStringsAndInvalidPhaseCoverage() {
        assertThrows(IllegalArgumentException.class, () -> HandContactCodec.read(JsonParser.parseString("{\"format_version\":\"1\",\"keys\":[]}")));
        assertThrows(IllegalArgumentException.class, () -> new HandContactProfile(java.util.List.of(
                new HandContactProfile.Key(0.2, 0, 0), new HandContactProfile.Key(1, 0, 0))));
    }

    @Test void forwardRollPresentsTheBackBeforeTheChestAndKeepsTheHeadClear() throws Exception {
        var source = clip("roll");
        var pose = source.sample(source.duration() * 0.45);
        var frames = HumanoidRig.evaluateBody(pose, false);
        double back = Double.POSITIVE_INFINITY, chest = Double.POSITIVE_INFINITY, head = Double.POSITIVE_INFINITY;
        for (double x : new double[]{-4,4}) for (double y : new double[]{0,12}) {
            back = Math.min(back, world(pose, frames.get(Bone.BODY).transform(new Vector3(x,y,2))).y());
            chest = Math.min(chest, world(pose, frames.get(Bone.BODY).transform(new Vector3(x,y,-2))).y());
        }
        for (double x : new double[]{-4,4}) for (double y : new double[]{-8,0}) for (double z : new double[]{-4,4}) {
            head = Math.min(head, world(pose, frames.get(Bone.HEAD).transform(new Vector3(x,y,z))).y());
        }
        assertTrue(back < chest, "The first body contact must be the back, not the chest");
        assertTrue(head > back + 0.01, "The head must be tucked clear of the first shoulder/back contact: " + head + " / " + back);
        assertTrue(back < 0.08, "The back must actually reach the ground during the contact phase: " + back);
    }

    @Test void interruptedContactReleasesPositionAndVelocityContinuously() {
        var player = new ContactOffsetPlayer();
        player.sample(0, new Vector3(0.1,0.1,0), true, 0.15);
        Vector3 before = player.sample(0.1, new Vector3(0.2,0.15,0), true, 0.15);
        assertEquals(before, player.sample(0.1,Vector3.ZERO,false,0.15));
        double step = 1e-6;
        var next = player.sample(0.1+step,Vector3.ZERO,false,0.15);
        assertEquals(1,(next.x()-before.x())/step,0.001);
        assertEquals(0.5,(next.y()-before.y())/step,0.001);
        assertEquals(Vector3.ZERO,player.sample(0.25,Vector3.ZERO,false,0.15));
    }
}
