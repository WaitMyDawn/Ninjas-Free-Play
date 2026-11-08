package yagen.waitmydawn.ninjas_free_play.animation;

import org.junit.jupiter.api.Test;
import yagen.waitmydawn.ninjas_free_play.animation.core.*;
import yagen.waitmydawn.ninjas_free_play.animation.resource.AnimationJsonCodec;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ParkourAnimationTest {
    private AnimationClip clip(String id) throws Exception {
        try(var reader=Files.newBufferedReader(Path.of("build/generated/animation-resources/assets/ninjas_free_play/ninja_animations/"+id+".json"))) {
            return AnimationJsonCodec.read(reader);
        }
    }
    private Vector3 world(AnimationPose pose,Vector3 point) {
        var root=pose.bone(Bone.ROOT);
        return PlayerModelSpace.offset(point,root.position(),Rotation.fromEuler(root.rotation()),LaunchOrientation.entityModelRotation(0),1,1);
    }
    private Vector3 end(AnimationPose pose,Bone bone,double length) {
        var frame=HumanoidRig.evaluateBody(pose,false).get(bone);
        return world(pose,frame.transform(new LimbDeformer(pose.bone(bone).bend(),HumanoidRig.bendCenter(bone),10).position(new Vector3(0,length,0))));
    }
    @Test void glideReachesForwardWithTheRightHandAndBalancesLeftAndBack() throws Exception {
        var source=clip("aim_glide");
        for(int i=0;i<16;i++) {
            var pose=source.sample(source.duration()*i/16);
            var frames=HumanoidRig.evaluateBody(pose,false);
            var right=end(pose,Bone.RIGHT_ARM,10).add(world(pose,frames.get(Bone.RIGHT_ARM).position()).scale(-1));
            var left=end(pose,Bone.LEFT_ARM,10).add(world(pose,frames.get(Bone.LEFT_ARM).position()).scale(-1));
            assertTrue(right.z()>.4,"The right arm must reach toward the travel-facing rope grip");
            assertTrue(left.x()>.2 && left.z()<-.1,"The left arm must extend left and backward for balance: "+left);
        }
    }
    @Test void latchUsesOneExtendedLegAndABentLegWhoseEndPointsIntoTheWall() throws Exception {
        var source=clip("wall_latch");
        var pose=source.sample(.3);
        assertEquals(0,pose.bone(Bone.LEFT_LEG).bend(),1e-9);
        assertTrue(pose.bone(Bone.RIGHT_LEG).bend()>Math.toRadians(80));
        assertEquals(Vector3.ZERO,pose.bone(Bone.RIGHT_LEG).position(),"The bent leg must remain attached at its hip pivot");
        for(double inflation:new double[]{0,.25,1}) {
            var left=FootSupport.point(pose,Bone.LEFT_LEG,new Vector3(0,0,1),inflation);
            var right=FootSupport.point(pose,Bone.RIGHT_LEG,new Vector3(0,0,1),inflation);
            assertEquals(left.z(),right.z(),.5,"Skin and standard boots must remain within the bounded support correction");
        }
        var straight=end(pose,Bone.LEFT_LEG,12).add(end(pose,Bone.LEFT_LEG,0).scale(-1)).normalized();
        assertEquals(30,Math.toDegrees(Math.asin(straight.z())),.2);
        var bent=end(pose,Bone.RIGHT_LEG,12).add(end(pose,Bone.RIGHT_LEG,11.9).scale(-1)).normalized();
        assertTrue(bent.z()>.95 && Math.abs(bent.y())<.1,"The bent leg's shin must end approximately perpendicular to the wall");
        var torso=world(pose,HumanoidRig.evaluateBody(pose,false).get(Bone.BODY).rotation().apply(new Vector3(0,-1,0)))
                .add(world(pose,Vector3.ZERO).scale(-1)).normalized();
        assertEquals(30,Math.toDegrees(Math.asin(Math.abs(torso.z()))),1e-6);
    }
    @Test void airJumpFlipCompletesAFullTurnAndKickLeadsWithTheFoot() throws Exception {
        var flip=clip("air_flip");
        assertEquals(Math.PI*2,flip.sample(flip.duration()).bone(Bone.ROOT).rotation().x(),1e-8);
        var kick=clip("flying_kick").sample(.18);
        var hip=world(kick,HumanoidRig.evaluateBody(kick,false).get(Bone.LEFT_LEG).position());
        assertTrue(end(kick,Bone.LEFT_LEG,12).z()-hip.z()>.6,"The extended leg must lead the flying kick");
    }

    @Test void glideSpeedAdaptationPlacesTheAuthoredTorsoOnTheRequestedAxis() throws Exception {
        var pose=clip("aim_glide").sample(0);
        var axis=Rotation.fromEuler(pose.bone(Bone.ROOT).rotation())
                .multiply(Rotation.fromEuler(pose.bone(Bone.BODY).rotation())).apply(new Vector3(0,-1,0));
        for(double speed:new double[]{0,8,16,10000}) {
            var target=GlideOrientation.correction(pose,speed).apply(axis);
            assertEquals(GlideOrientation.inclinationDegrees(speed),Math.toDegrees(Math.atan2(-target.y(),Math.hypot(target.x(),target.z()))),1e-6);
            assertTrue(target.z()<0,"The body must lean forward in the travel-facing frame");
        }
    }

    @Test void slideTorsoAndExtendedLegHaveDistinctDynamicGroundAngles() throws Exception {
        var pose=clip("slide").sample(.2);
        var frames=HumanoidRig.evaluateBody(pose,false);
        var body=frames.get(Bone.BODY).rotation().apply(new Vector3(0,-1,0));
        var leg=frames.get(Bone.LEFT_LEG).rotation().apply(new Vector3(0,1,0));
        assertEquals(60,Math.toDegrees(Math.asin(Math.abs(body.y()))),.3);
        assertEquals(30,Math.toDegrees(Math.asin(Math.abs(leg.y()))),.3);
        assertEquals(30,Math.toDegrees(Math.acos(Math.abs(body.x()*leg.x()+body.y()*leg.y()+body.z()*leg.z()))),1);
    }

    @Test void backwardFlipSeparatesTheLegsWhileRetainingAFullTurn() throws Exception {
        var source=clip("roll_backward");
        for(double phase:new double[]{.25,.5,.72}) {
            var pose=source.sample(source.duration()*phase);
            var left=end(pose,Bone.LEFT_LEG,12);var right=end(pose,Bone.RIGHT_LEG,12);
            assertTrue(Math.abs(left.x()-right.x())>.5,"The legs must remain visibly separated during the backward flip");
        }
        assertEquals(-2*Math.PI,source.sample(source.duration()).bone(Bone.ROOT).rotation().x(),1e-8);
    }

    @Test void forwardRollStartsWithAnExtendedDiveAndKeepsTheLegsOpenDuringBackContact() throws Exception {
        var source=clip("roll");var dive=source.sample(.07);
        var hip=world(dive,HumanoidRig.evaluateBody(dive,false).get(Bone.LEFT_LEG).position());
        for(Bone arm:new Bone[]{Bone.LEFT_ARM,Bone.RIGHT_ARM})
            assertTrue(end(dive,arm,10).z()>hip.z()+.4,"The opening must visibly reach forward into the dive");
        for(double time:new double[]{.2,.25,.3,.36,.4}) for(Bone leg:new Bone[]{Bone.LEFT_LEG,Bone.RIGHT_LEG}) {
            var pose=source.sample(time);
            assertTrue(pose.bone(leg).bend()<=Math.toRadians(85),"Back contact must not compress both knees to their joint limits");
            assertEquals(Vector3.ZERO,pose.bone(leg).position());
        }
    }

    @Test void fourWallDashesUseBothArmsAndACompressionPushRecoverySequence() throws Exception {
        for(String id:new String[]{"wall_dash","wall_dash_left","wall_dash_right","wall_dash_away"}) {
            var source=clip(id);
            var brace=source.sample(.05);var push=source.sample(.17);
            for(Bone arm:new Bone[]{Bone.LEFT_ARM,Bone.RIGHT_ARM}) {
                assertTrue(Math.abs(Rotation.fromEuler(brace.bone(arm).rotation()).dot(Rotation.fromEuler(push.bone(arm).rotation())))<.99);
                assertTrue(Math.abs(brace.bone(arm).bend()-push.bone(arm).bend())>.3);
            }
            Bone support=id.equals("wall_dash_right")?Bone.RIGHT_LEG:Bone.LEFT_LEG;
            assertTrue(brace.bone(support).bend()>push.bone(support).bend()+.5);
        }
    }

    @Test void parkourPoseInterruptionsPreservePositionAndAngularVelocity() throws Exception {
        String[] ids={"aim_glide","wall_latch","wall_dash","wall_dash_left","wall_dash_right","wall_dash_away","slide","roll_backward","flying_kick"};
        double time=.19,step=1e-6;
        for(String from:ids) for(String to:ids) {
            var player=new AnimationPlayer();player.play(clip(from),0,.15);
            var previous=player.sample(time).pose();var before=player.sample(time-step).pose();
            player.play(clip(to),time,.15);
            var current=player.sample(time).pose();var after=player.sample(time+step).pose();
            for(Bone bone:Bone.values()) for(int axis=0;axis<3;axis++) {
                assertEquals(previous.bone(bone).rotation().component(axis),current.bone(bone).rotation().component(axis),1e-6);
                assertEquals(previous.bone(bone).position().component(axis),current.bone(bone).position().component(axis),1e-6);
                double incoming=(previous.bone(bone).rotation().component(axis)-before.bone(bone).rotation().component(axis))/step;
                double outgoing=(after.bone(bone).rotation().component(axis)-current.bone(bone).rotation().component(axis))/step;
                assertEquals(incoming,outgoing,.03,from+" -> "+to+" "+bone);
            }
        }
    }
}
