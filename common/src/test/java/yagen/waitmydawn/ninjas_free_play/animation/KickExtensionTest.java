package yagen.waitmydawn.ninjas_free_play.animation;

import org.junit.jupiter.api.Test;
import yagen.waitmydawn.ninjas_free_play.animation.core.*;
import yagen.waitmydawn.ninjas_free_play.animation.resource.AnimationJsonCodec;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class KickExtensionTest {
    private AnimationClip clip() throws Exception {
        try(var reader=Files.newBufferedReader(Path.of(System.getProperty("nfp.generatedResources"), "assets/ninjas_free_play/ninja_animations/flying_kick.json"))) {
            return AnimationJsonCodec.read(reader);
        }
    }
    private static double dot(Vector3 a,Vector3 b) {return a.x()*b.x()+a.y()*b.y()+a.z()*b.z();}
    private Vector3 endpoint(AnimationPose pose,Bone bone,double y) {
        var frame=HumanoidRig.evaluateBody(pose,false).get(bone);
        var local=new LimbDeformer(pose.bone(bone).bend(),HumanoidRig.bendCenter(bone),10).position(new Vector3(0,y,0));
        return Rotation.fromEuler(pose.bone(Bone.ROOT).rotation()).apply(frame.transform(local).add(new Vector3(0,-12,0)));
    }
    @Test void oneShotKickChambersThenRapidlyStraightensAndHoldsWithoutRetractingAgain() throws Exception {
        var clip=clip();var leg=KickOrientation.leadingLeg(clip.sample(clip.duration()));
        assertFalse(clip.loop());assertEquals(1,clip.duration(),1e-9);
        for(double time:new double[]{0,.05,.09}) assertTrue(clip.sample(time).bone(leg).bend()>Math.toRadians(100));
        assertEquals(0,clip.sample(.18).bone(leg).bend(),1e-9);
        for(double time:new double[]{.24,.5,.8,1,1.1}) assertEquals(0,clip.sample(time).bone(leg).bend(),1e-9);
    }
    @Test void attackingFootReachIncreasesMonotonicallyDuringTheFastExtension() throws Exception {
        var clip=clip();var terminal=clip.sample(clip.duration());var leg=KickOrientation.leadingLeg(terminal);
        var axis=KickOrientation.footAxis(terminal,leg);
        double first=dot(endpoint(clip.sample(.09),leg,12),axis),previous=first;
        for(int i=1;i<=30;i++) {
            double reach=dot(endpoint(clip.sample(.09+.09*i/30),leg,12),axis);
            assertTrue(reach>=previous-1e-8);previous=reach;
        }
        assertTrue((previous-first)*PlayerModelSpace.RENDER_SCALE/16>.3,"The attack must visibly extend beyond the chambered foot");
    }
    @Test void stableTerminalFacingKeepsTheToePointingAtTheTargetThroughoutExtension() throws Exception {
        var clip=clip();var terminal=clip.sample(clip.duration());var leg=KickOrientation.leadingLeg(terminal);
        for(double yaw:new double[]{0,123}) for(double pitch:new double[]{-90,-45,0,45,90}) {
            var direction=LaunchOrientation.fromDegrees(yaw,pitch);
            var facing=KickOrientation.facing(terminal,direction);
            for(double time:new double[]{0,.09,.12,.15,.18,.8}) {
                var toe=facing.apply(KickOrientation.footAxis(clip.sample(time),leg));
                assertEquals(1,dot(toe,direction.direction()),1e-8,"The toe axis must not swing with knee extension");
            }
        }
    }
    @Test void extendedAttackLegLeadsBothHandsAndTheFoldedTrailingFoot() throws Exception {
        var pose=clip().sample(.24);var leg=KickOrientation.leadingLeg(pose);var axis=KickOrientation.footAxis(pose,leg);
        double attack=dot(endpoint(pose,leg,12),axis);
        var head=Rotation.fromEuler(pose.bone(Bone.ROOT).rotation())
                .multiply(HumanoidRig.evaluateBody(pose,false).get(Bone.HEAD).rotation()).apply(new Vector3(0,0,-1));
        assertTrue(dot(head,axis)>.65,"The head must glance toward the strike while the torso lies along the motion axis");
        assertTrue(Math.sqrt(dot(pose.bone(Bone.HEAD).rotation(),pose.bone(Bone.HEAD).rotation()))<Math.toRadians(55),
                "Looking toward the strike must not require an extreme neck rotation");
        for(Bone other:new Bone[]{Bone.LEFT_ARM,Bone.RIGHT_ARM,Bone.RIGHT_LEG}) {
            double reach=dot(endpoint(pose,other,other==Bone.RIGHT_LEG?12:10),axis);
            assertTrue(attack-reach>2.5,"The attack leg must have a clear forward silhouette lead over "+other);
        }
    }
    @Test void levelFlightLiesOnTheRightSideForEveryHeading() throws Exception {
        var clip=clip();var terminal=clip.sample(clip.duration());
        for(double yaw:new double[]{0,70,180,275}) {
            var direction=LaunchOrientation.fromDegrees(yaw,0);
            var facing=KickOrientation.facing(terminal,direction);
            for(double time:new double[]{0,.09,.135,.18,.5,1}) {
                var pose=clip.sample(time);
                var left=facing.apply(endpoint(pose,Bone.LEFT_ARM,0));
                var right=facing.apply(endpoint(pose,Bone.RIGHT_ARM,0));
                assertTrue(left.y()-right.y()>4,"The right shoulder must stay below the left during the bank and extended hold");
                var root=Rotation.fromEuler(pose.bone(Bone.ROOT).rotation());
                var torso=facing.multiply(root).multiply(HumanoidRig.evaluateBody(pose,false).get(Bone.BODY).rotation());
                assertTrue(Math.abs(dot(torso.apply(new Vector3(0,0,-1)),direction.direction()))<.2,
                        "The chest must face sideways rather than reverting to a seated forward-facing kick");
                assertTrue(dot(torso.apply(new Vector3(0,-1,0)),direction.direction())<-.98,
                        "The head end of the torso must trail the pelvis along the motion axis");
            }
        }
    }
}
