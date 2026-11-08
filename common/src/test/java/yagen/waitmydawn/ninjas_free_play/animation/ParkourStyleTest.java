package yagen.waitmydawn.ninjas_free_play.animation;

import org.junit.jupiter.api.Test;
import yagen.waitmydawn.ninjas_free_play.animation.core.*;
import yagen.waitmydawn.ninjas_free_play.animation.resource.AnimationJsonCodec;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class ParkourStyleTest {
    private AnimationPose pose(String name,double time) throws Exception {
        try(var reader=Files.newBufferedReader(Path.of(System.getProperty("nfp.generatedResources"), "assets/ninjas_free_play/ninja_animations/"+name+".json"))) {
            return AnimationJsonCodec.read(reader).sample(time);
        }
    }
    private Vector3 endpoint(AnimationPose pose,Bone bone,double length) {
        var frame=HumanoidRig.evaluateBody(pose,false).get(bone);
        var local=new LimbDeformer(pose.bone(bone).bend(),HumanoidRig.bendCenter(bone),10).position(new Vector3(0,length,0));
        var root=pose.bone(Bone.ROOT);
        return PlayerModelSpace.offset(frame.transform(local),root.position(),Rotation.fromEuler(root.rotation()),LaunchOrientation.entityModelRotation(0),1,1);
    }
    private static double dot(Vector3 a,Vector3 b) {return a.x()*b.x()+a.y()*b.y()+a.z()*b.z();}
    private static double length(Vector3 v) {return Math.sqrt(dot(v,v));}

    @Test void sideKickKeepsTheTorsoAndAttackingLegOnOneLineWithARelaxedTrailingLeg() throws Exception {
        for(double time:new double[]{.2,.4,.6,.95}) {
            var pose=pose("flying_kick",time);var frames=HumanoidRig.evaluateBody(pose,false);
            var torso=frames.get(Bone.BODY).rotation().apply(new Vector3(0,-1,0));
            var leg=frames.get(Bone.LEFT_LEG).rotation().apply(new Vector3(0,-1,0));
            double angle=Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,dot(torso,leg)))));
            assertTrue(angle<12,"The torso and extended leg must share a long striking axis: "+angle);
            assertEquals(0,pose.bone(Bone.LEFT_LEG).bend(),1e-9);
            assertTrue(pose.bone(Bone.RIGHT_LEG).bend()>Math.toRadians(60)
                    && pose.bone(Bone.RIGHT_LEG).bend()<Math.toRadians(100));
            assertTrue(endpoint(pose,Bone.RIGHT_LEG,0).y()<endpoint(pose,Bone.LEFT_LEG,0).y()-.15,"The supporting hip must lie on the lower right side of the body");
            assertTrue(Math.abs(pose.bone(Bone.LEFT_ARM).bend())<Math.toRadians(45));
            assertTrue(Math.abs(pose.bone(Bone.RIGHT_ARM).bend())<Math.toRadians(45));
        }
    }

    @Test void wallHandsPullDownForUpwardDashesAndAgainstLateralTravel() throws Exception {
        for(Bone arm:new Bone[]{Bone.LEFT_ARM,Bone.RIGHT_ARM}) {
            var reach=endpoint(pose("wall_dash",.04),arm,10);
            var pull=endpoint(pose("wall_dash",.15),arm,10);
            assertTrue(reach.y()-pull.y()>.3,"Upward dash must visibly reach high then pull down");
            for(String name:new String[]{"wall_dash_left","wall_dash_right"}) {
                double sign=name.endsWith("left")?1:-1;
                reach=endpoint(pose(name,.04),arm,10);pull=endpoint(pose(name,.15),arm,10);
                assertTrue((reach.x()-pull.x())*sign>.25,name+" must pull horizontally opposite its travel");
            }
        }
    }

    @Test void latchTurnsThroughAllViewsWithAnAngledTorsoAttachedHipsAndSupportedFeet() throws Exception {
        var front=pose("wall_latch",.3);var left=pose("wall_latch_left",.3);
        var back=pose("wall_latch_back",.3);var right=pose("wall_latch_right",.3);
        for(int degrees=-180;degrees<=180;degrees+=5) for(double inflation:new double[]{0,.25,1}) {
            var blended=WallLatchBlend.adapt(front,front,left,back,right,Math.toRadians(degrees));
            var up=Rotation.fromEuler(blended.bone(Bone.BODY).rotation()).apply(new Vector3(0,-1,0));
            assertEquals(.5,up.z(),1e-6,"The body axis must keep its 30 degree wall angle");
            var normal=new Vector3(0,0,1);
            double plane=FootSupport.point(blended,Bone.LEFT_LEG,normal,inflation).z();
            var supported=FootSupport.fit(blended,Bone.RIGHT_LEG,normal,plane,inflation);
            assertEquals(plane,FootSupport.point(supported,Bone.RIGHT_LEG,normal,inflation).z(),.05,"Foot plane at yaw "+degrees);
            assertEquals(front.bone(Bone.RIGHT_LEG).position(),supported.bone(Bone.RIGHT_LEG).position());
        }
        var facing=Rotation.fromEuler(front.bone(Bone.BODY).rotation()).apply(new Vector3(0,0,-1));
        var reversed=Rotation.fromEuler(back.bone(Bone.BODY).rotation()).apply(new Vector3(0,0,-1));
        assertTrue(dot(facing,reversed)<-.99,"Back-facing latch must rotate the torso, not only the head");
    }

    @Test void rearViewWrapHasNoPoseOrBendDiscontinuity() throws Exception {
        var front=pose("wall_latch",0);var left=pose("wall_latch_left",0);var back=pose("wall_latch_back",0);var right=pose("wall_latch_right",0);
        var a=WallLatchBlend.adapt(front,front,left,back,right,Math.PI-1e-6);
        var b=WallLatchBlend.adapt(front,front,left,back,right,-Math.PI+1e-6);
        for(Bone bone:Bone.values()) {
            assertEquals(1,Math.abs(Rotation.fromEuler(a.bone(bone).rotation()).dot(Rotation.fromEuler(b.bone(bone).rotation()))),1e-8);
            assertEquals(a.bone(bone).bend(),b.bone(bone).bend(),1e-8);
        }
    }

    @Test void smoothedRearViewCrossingContinuesAroundTheBackWithoutUnwindingThroughTheFront() {
        var smoother=new RotationSmoother();
        Rotation target=Rotation.fromEuler(new Vector3(0,Math.toRadians(170),0));
        smoother.sample(0,target,.15);
        Rotation previous=smoother.sample(1,target,.15);
        for(int frame=1;frame<=120;frame++) {
            double yaw=170+20*frame/120.;
            target=Rotation.fromEuler(new Vector3(0,Math.IEEEremainder(Math.toRadians(yaw),2*Math.PI),0));
            Rotation current=smoother.sample(1+frame/120.,target,.15);
            assertTrue(Math.abs(previous.dot(current))>.999,"Rear seam must not reverse the whole-body turn");
            assertTrue(current.apply(new Vector3(0,0,-1)).z()>.9,"The body must stay back-facing throughout the crossing");
            previous=current;
        }
    }

    @Test void itemAimPreservesTheAuthoredBodyAxisAtEveryGlideInclination() {
        for(double tilt:new double[]{30,45,60}) for(double yaw:new double[]{-170,-90,0,90,170}) {
            var rotation=Rotation.fromEuler(new Vector3(Math.toRadians(tilt),Math.toRadians(yaw),.1));
            var aim=BodyAim.align(rotation,new Vector3(0,0,-1));
            var before=rotation.apply(new Vector3(0,-1,0));var after=aim.apply(new Vector3(0,-1,0));
            assertEquals(0,length(before.add(after.scale(-1))),1e-9);
        }
    }

    @Test void eitherConsumptionArmReachesTheHeadWithoutMovingItsShoulderOrStraighteningTheBody() throws Exception {
        var pose=pose("aim_glide",0);
        for(boolean slim:new boolean[]{false,true}) for(Bone arm:new Bone[]{Bone.LEFT_ARM,Bone.RIGHT_ARM}) {
            var frames=HumanoidRig.evaluateBody(pose,slim);
            Rotation head=Rotation.fromEuler(new Vector3(-Math.PI/4,0,0));
            var solved=ConsumptionArmPose.solve(pose,arm,head,slim,0);
            var palm=new LimbDeformer(solved.bend(),4,10).position(HumanoidRig.palmAnchor(arm,slim));
            var hand=frames.get(arm).position().add(Rotation.fromEuler(solved.rotation()).apply(palm));
            var mouth=frames.get(Bone.HEAD).position().add(head.apply(new Vector3(0,-3.4,-5.1)));
            assertTrue(length(hand.add(mouth.scale(-1)))<1,"The food hand must stay near the mouth: "+length(hand.add(mouth.scale(-1))));
            assertEquals(pose.bone(arm).position(),solved.position());
            assertTrue(solved.bend()<=0);
        }
    }
}
