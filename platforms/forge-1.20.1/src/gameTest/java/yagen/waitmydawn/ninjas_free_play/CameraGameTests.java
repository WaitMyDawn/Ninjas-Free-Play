package yagen.waitmydawn.ninjas_free_play;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.EndRodBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import yagen.waitmydawn.ninjas_free_play.animation.core.Vector3;
import yagen.waitmydawn.ninjas_free_play.camera.CameraCollision;
import yagen.waitmydawn.ninjas_free_play.camera.CameraTargeting;
import yagen.waitmydawn.ninjas_free_play.camera.core.*;
import yagen.waitmydawn.ninjas_free_play.player.MovementPhysics;

@GameTestHolder(NinjasFreePlay.MODID)
@PrefixGameTestTemplate(false)
public final class CameraGameTests {
    private static final CameraRig.Sweep CLEAR_SWEEP=(a,b,r) -> 1;
    private CameraGameTests() {}
    private static Vector3 point(GameTestHelper helper,double x,double y,double z) {
        var p=helper.absoluteVec(new Vec3(x,y,z));return new Vector3(p.x,p.y,p.z);
    }
    private static Player player(GameTestHelper helper) {
        var player=helper.makeMockPlayer();
        player.setPos(helper.absoluteVec(new Vec3(4.5,2,4.5)));return player;
    }
    private static double sweep(GameTestHelper helper,Player player,Vector3 a,Vector3 b,double radius) {
        return CameraCollision.fraction(helper.getLevel(),player,a,b,radius);
    }
    private static boolean clear(GameTestHelper helper,Vector3 position,double radius) {
        return !helper.getLevel().getBlockCollisions(null,new AABB(position.x()-radius,position.y()-radius,position.z()-radius,
                position.x()+radius,position.y()+radius,position.z()+radius).deflate(1e-6)).iterator().hasNext();
    }

    @GameTest(template="empty")
    public static void diagonalShoulderPathDetectsWallsMissedByVanillaBackwardZoom(GameTestHelper helper) {
        helper.setBlock(3,3,2,Blocks.STONE);var player=player(helper);
        var eye=point(helper,4.5,3.5,4.5);var target=eye.add(new Vector3(-1,.25,-3.2));
        helper.assertTrue(sweep(helper,player,eye,eye.add(new Vector3(0,0,-3.2)),.12)==1,"The ordinary backward path must remain unobstructed in this corner fixture");
        double fraction=sweep(helper,player,eye,target,.12);
        helper.assertTrue(fraction>0&&fraction<1,"The full lateral/upward/backward path must detect the off-axis wall");
        helper.assertTrue(clear(helper,eye.lerp(target,fraction),.12),"The contracted camera volume must remain outside the wall");
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void cameraClearanceFindsNearWallObstructionEvenWhenTheCenterRayMisses(GameTestHelper helper) {
        helper.setBlock(3,3,2,Blocks.STONE);var player=player(helper);
        var eye=point(helper,4.05,3.5,4.5);var target=eye.add(new Vector3(0,0,-3.2));
        helper.assertTrue(sweep(helper,player,eye,target,0)==1,"The center ray must pass just outside the wall");
        double fraction=sweep(helper,player,eye,target,.12);
        helper.assertTrue(fraction>0&&fraction<1,"The camera corners must detect the clearance obstruction");
        helper.assertTrue(clear(helper,eye.lerp(target,fraction),.12),"The near-plane clearance must stop before the wall");
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void sweptCameraVolumeDetectsThinShapesBetweenCenterAndCornerRays(GameTestHelper helper) {
        helper.setBlock(3,3,2,Blocks.END_ROD.defaultBlockState().setValue(EndRodBlock.FACING,Direction.NORTH));
        var player=player(helper);var eye=point(helper,3.3,3.5,4.5);var target=eye.add(new Vector3(0,0,-3.2));
        helper.assertTrue(sweep(helper,player,eye,target,0)==1,"The center ray must miss the narrow horizontal shape");
        for(int corner=0;corner<8;corner++) {
            var offset=new Vector3((corner&1)==0?-.2:.2,(corner&2)==0?-.2:.2,(corner&4)==0?-.2:.2);
            helper.assertTrue(sweep(helper,player,eye.add(offset),target.add(offset),0)==1,"The shape must lie between the corner rays");
        }
        helper.assertTrue(sweep(helper,player,eye,target,.2)<1,"The complete swept volume must detect the shape missed by all sampled rays");
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void cameraQueriesUseActualSlabShapesAndIncludeVerticalOffset(GameTestHelper helper) {
        var player=player(helper);
        helper.setBlock(3,3,2,Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE,SlabType.BOTTOM));
        var low=point(helper,3.5,3.25,4.5);var high=point(helper,3.5,3.8,4.5);
        helper.assertTrue(sweep(helper,player,low,low.add(new Vector3(0,0,-3.2)),.1)<1,"The lower slab portion must obstruct the camera");
        helper.assertTrue(sweep(helper,player,high,high.add(new Vector3(0,0,-3.2)),.1)==1,"Empty space above a bottom slab must not be treated as a full block");
        helper.setBlock(4,3,3,Blocks.STONE);
        var eye=point(helper,4.5,2.8,4.5);
        helper.assertTrue(sweep(helper,player,eye,eye.add(new Vector3(0,0,-3.2)),.1)==1,"The flat path must fit below the ceiling");
        helper.assertTrue(sweep(helper,player,eye,eye.add(new Vector3(0,1,-3.2)),.1)<1,"The height offset must be included in collision queries");
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void shoulderRigRecoversAfterWallRemovalWithoutChangingPlayerMotionOrResources(GameTestHelper helper) {
        var player=player(helper);helper.setBlock(3,3,2,Blocks.STONE);
        player.setDeltaMovement(.2,.3,.4);var originalPosition=player.position();var originalMotion=player.getDeltaMovement();
        var originalState=MovementPhysics.controller(player).snapshot(0);
        var eye=player.getEyePosition();var anchor=new Vector3(eye.x,eye.y,eye.z);
        var rig=new CameraRig();var settings=new CameraSettings(3.2,1,.25,0,0,.12,.12);
        var blocked=rig.sample(0,anchor,0,0,new Vector3(0,0,-4),1,settings,(a,b,r) -> sweep(helper,player,a,b,r));
        helper.assertTrue(blocked.collisionFraction()<1,"The complete rig must retract at the off-axis wall");
        helper.setBlock(3,3,2,Blocks.AIR);
        var recovering=rig.sample(.05,anchor,0,0,new Vector3(0,0,-4),1,settings,(a,b,r) -> sweep(helper,player,a,b,r));
        helper.assertTrue(recovering.collisionFraction()>blocked.collisionFraction()&&recovering.collisionFraction()<1,"Removing the wall must permit a smooth return rather than an immediate distance jump");
        helper.assertTrue(player.position().equals(originalPosition)&&player.getDeltaMovement().equals(originalMotion)
                &&MovementPhysics.controller(player).snapshot(0).equals(originalState),"Camera computation must not move the player or modify action resources");
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void visualCameraQueryUsesItsOriginButDoesNotExtendEyeReach(GameTestHelper helper) {
        var player=player(helper);player.setPos(helper.absoluteVec(new Vec3(6.5,2,6.5)));
        var originalPosition=player.position();var originalRotation=player.getYRot();
        helper.setBlock(1,3,1,Blocks.STONE);
        var origin=helper.absoluteVec(new Vec3(1.5,3.5,6.5));
        var hit=CameraTargeting.pick(player,origin,new Vec3(0,0,-1),64);
        var intended=helper.absolutePos(new BlockPos(1,3,1));
        helper.assertTrue(hit.getType()==HitResult.Type.BLOCK && ((BlockHitResult)hit).getBlockPos().equals(intended),
                "The centered camera ray must use its off-axis origin: "+hit.getLocation()+" expected "+intended);
        var eyePick=player.pick(player.getBlockReach(),1,false);
        helper.assertTrue(hit.getLocation().distanceTo(player.getEyePosition())>player.getBlockReach()
                        && (!(eyePick instanceof BlockHitResult block) || !block.getBlockPos().equals(intended)),
                "A visual camera target must not grant out-of-reach eye interaction");
        helper.assertTrue(player.position().equals(originalPosition)&&player.getYRot()==originalRotation,
                "Target discovery must not mutate player state");
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void cameraQuerySelectsPickableEntitiesOnlyBeforeTheFirstBlock(GameTestHelper helper) {
        var player=player(helper);var actor=helper.spawn(net.minecraft.world.entity.EntityType.ZOMBIE,new Vec3(2.5,2,5.5));
        actor.setNoAi(true);
        var origin=helper.absoluteVec(new Vec3(2.5,3,1.5));
        var hit=CameraTargeting.pick(player,origin,new Vec3(0,0,1),16);
        helper.assertTrue(hit instanceof EntityHitResult entity && entity.getEntity()==actor,"A centered ray must discover an exposed target entity");
        helper.setBlock(2,3,3,Blocks.STONE);
        helper.assertTrue(CameraTargeting.pick(player,origin,new Vec3(0,0,1),16).getType()==HitResult.Type.BLOCK,
                "Targets behind the first block must not win camera selection");
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void aNearWallAndRealCameraRetractionCannotPullFixedFacingTowardTheNearHit(GameTestHelper helper) {
        var player=player(helper);var eye=player.getEyePosition();var anchor=new Vector3(eye.x,eye.y,eye.z);
        var settings=new CameraSettings(3.2,2,.25,0,0,0,.12);var angles=new CameraGeometry.Angles(0,0);
        var offset=new Vector3(-2,.25,-3.2);var open=new CameraRig().sample(0,anchor,0,0,offset,1,settings,CLEAR_SWEEP);
        for(int x=0;x<8;x++) for(int y=2;y<5;y++) helper.setBlock(x,y,5,Blocks.STONE);
        var near=CameraTargeting.pick(player,new Vec3(open.position().x(),open.position().y(),open.position().z()),new Vec3(0,0,1),64);
        helper.assertTrue(near.getType()==HitResult.Type.BLOCK,"The center ray must actually hit the nearby wall");
        var far=CameraOrbit.virtualTarget(anchor,angles,open.composition(),1,64);
        var facing=CameraOrbit.aim(anchor,far,0);
        helper.assertTrue(Math.abs(facing.yaw())<2,"Virtual facing must stay near the mouse heading instead of turning sideways into the wall");
        player.setYRot((float)facing.yaw());player.setXRot((float)facing.pitch());
        helper.assertTrue(player.pick(player.getBlockReach(),1,false).getType()==HitResult.Type.BLOCK,
                "Actual eye picking must still stop at the nearby wall");
        for(int x=0;x<8;x++) for(int y=2;y<5;y++) helper.setBlock(x,y,3,Blocks.STONE);
        var blocked=new CameraRig().sample(0,anchor,0,0,offset,1,settings,(a,b,r) -> sweep(helper,player,a,b,r));
        helper.assertTrue(blocked.collisionFraction()<1,"The rear wall must cause real camera retraction");
        var clippedFacing=CameraOrbit.aim(anchor,CameraOrbit.virtualTarget(anchor,angles,blocked.composition(),1,64),0);
        helper.assertTrue(clippedFacing.equals(facing),"Camera retraction cannot change the virtual far aim");
        helper.succeed();
    }
}
