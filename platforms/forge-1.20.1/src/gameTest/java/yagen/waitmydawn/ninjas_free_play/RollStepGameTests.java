package yagen.waitmydawn.ninjas_free_play;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import yagen.waitmydawn.ninjas_free_play.api.attribute.NAttributes;
import yagen.waitmydawn.ninjas_free_play.core.MovementInput;
import yagen.waitmydawn.ninjas_free_play.player.MovementPhysics;

@GameTestHolder(NinjasFreePlay.MODID)
@PrefixGameTestTemplate(false)
public final class RollStepGameTests {
    private RollStepGameTests() {}
    private static Player player(GameTestHelper helper) {
        for(int x=3;x<=7;x++) for(int z=3;z<=7;z++) helper.setBlock(x,1,z,Blocks.STONE);
        var player=helper.makeMockPlayer();
        player.setPos(helper.absoluteVec(new Vec3(5.5,2,5.5)));player.setOnGround(true);player.setDeltaMovement(0,-.08,0);
        return player;
    }
    private static void start(GameTestHelper helper,Player player,float strafe,float forward) {
        var input=new MovementInput(0,false,false,0,0,true,false,strafe,forward);
        var session=MovementPhysics.controller(player);
        var decision=session.accept(input,true,true,MovementPhysics.canStand(player),MovementPhysics.canRoll(player,input),0,NAttributes.read(player));
        helper.assertTrue(decision.rollStarted(),"The starting handspring envelope must be clear");
        MovementPhysics.applyCrouch(player);MovementPhysics.applyRoll(player);
    }

    @GameTest(template="empty")
    public static void bothSideFlipsAndBackFlipsStepOverHalfBlocksWithoutCancellingTheirDrive(GameTestHelper helper) {
        for(int[] direction:new int[][]{{1,0,6,5},{-1,0,4,5},{0,-1,5,4}}) {
            var player=player(helper);var start=player.position();
            helper.setBlock(direction[2],2,direction[3],Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE,SlabType.BOTTOM));
            start(helper,player,direction[0],direction[1]);player.travel(Vec3.ZERO);
            helper.assertTrue(Math.abs(player.getY()-start.y-.5)<1e-5,"Native stepping must raise the feet onto the half block");
            helper.assertTrue(player.position().subtract(start).horizontalDistance()>.5,"A half block must not discard roll momentum");
            helper.assertTrue(MovementPhysics.controller(player).posture().rolling(0),"The flip must continue after stepping");
            helper.assertTrue(Math.abs(player.getBbHeight()-1.8)<1e-5,"Side/back flips must retain standing collision height");
            helper.assertTrue(player.level().noCollision(player,player.getBoundingBox().deflate(1e-6)),"Step resolution must not penetrate the obstacle");
            helper.setBlock(direction[2],2,direction[3],Blocks.AIR);player.discard();
        }
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void lowObstacleSupportUsesActualCollisionShapesRatherThanBlockCategories(GameTestHelper helper) {
        for(BlockState obstacle:new BlockState[]{Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS,5),Blocks.WHITE_CARPET.defaultBlockState()}) {
            var player=player(helper);double start=player.getY();helper.setBlock(5,2,4,obstacle);
            double expected=obstacle.getCollisionShape(helper.getLevel(),helper.absolutePos(new net.minecraft.core.BlockPos(5,2,4))).bounds().maxY;
            start(helper,player,0,-1);player.travel(Vec3.ZERO);
            helper.assertTrue(Math.abs(player.getY()-start-expected)<1e-5,"The native collision shape must determine the step height");
            helper.assertTrue(MovementPhysics.controller(player).posture().rolling(0),"A valid low obstacle must not cancel the flip");
            helper.setBlock(5,2,4,Blocks.AIR);player.discard();
        }
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void fullBlocksStillStopFlipMotionWithoutBeingTurnedIntoSteps(GameTestHelper helper) {
        var player=player(helper);double start=player.getZ();helper.setBlock(5,2,4,Blocks.STONE);
        start(helper,player,0,-1);player.travel(Vec3.ZERO);player.tickCount=1;player.travel(Vec3.ZERO);
        helper.assertTrue(player.getZ()>=start-.20001&&Math.abs(player.getY()-helper.absoluteVec(new Vec3(0,2,0)).y)<1e-5,
                "A one-block obstacle must still stop native movement");
        helper.assertFalse(MovementPhysics.controller(player).posture().rolling(1),"An actually blocked flip must stop its motor");
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void raisedStepMustStillLeaveEnoughOverheadRoomForTheFlip(GameTestHelper helper) {
        var player=player(helper);helper.setBlock(5,2,4,Blocks.STONE_SLAB);
        helper.setBlock(5,4,5,Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE,SlabType.TOP));
        helper.assertTrue(MovementPhysics.canRoll(player,new MovementInput(0,false,false,0,0,true,false,0,-1)),
                "The initial envelope must fit below the high slab");
        start(helper,player,0,-1);
        helper.assertFalse(MovementPhysics.controller(player).posture().rolling(0),"A raised path that lacks handspring clearance must still be rejected");
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void lowerStepHeightAttributesRemainAuthoritative(GameTestHelper helper) {
        var player=player(helper);player.getAttribute(net.minecraftforge.common.ForgeMod.STEP_HEIGHT_ADDITION.get()).setBaseValue(-.35);
        helper.setBlock(5,2,4,Blocks.STONE_SLAB);double start=player.getY();
        start(helper,player,0,-1);player.travel(Vec3.ZERO);player.tickCount=1;player.travel(Vec3.ZERO);
        helper.assertTrue(Math.abs(player.getY()-start)<1e-5,"Roll validation must not override a lower step-height attribute");
        helper.assertFalse(MovementPhysics.controller(player).posture().rolling(1),"An obstacle exceeding the actual step limit must stop the flip");
        helper.succeed();
    }
}
