package yagen.waitmydawn.ninjas_free_play;

import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import yagen.waitmydawn.ninjas_free_play.api.attribute.NAttributes;
import yagen.waitmydawn.ninjas_free_play.core.JumpKind;
import yagen.waitmydawn.ninjas_free_play.core.MovementInput;
import yagen.waitmydawn.ninjas_free_play.core.MovementSnapshot;
import yagen.waitmydawn.ninjas_free_play.core.PostureSnapshot;
import yagen.waitmydawn.ninjas_free_play.core.RollKind;
import yagen.waitmydawn.ninjas_free_play.network.MovementInputPayload;
import yagen.waitmydawn.ninjas_free_play.network.MovementStatePayload;
import yagen.waitmydawn.ninjas_free_play.player.MovementPhysics;

@GameTestHolder(NinjasFreePlay.MODID)
@PrefixGameTestTemplate(false)
public final class MovementGameTests {
    private MovementGameTests() {}

    private static Player player(GameTestHelper helper) {
        helper.setBlock(2, 1, 2, Blocks.STONE);
        Player player = helper.makeMockPlayer();
        player.setPos(helper.absoluteVec(new Vec3(2.5, 2, 2.5)));
        player.setOnGround(true);
        return player;
    }

    @GameTest(template = "empty")
    public static void crouchUsesCollisionHeightAndCannotStandUnderALowCeiling(GameTestHelper helper) {
        Player player = player(helper);
        helper.setBlock(2, 3, 2, Blocks.STONE);
        var controller = MovementPhysics.controller(player);
        controller.accept(new MovementInput(0, true, false, 0, 0), true, true, MovementPhysics.canStand(player), 0, NAttributes.read(player));
        player.tick();
        helper.assertTrue(player.getPose() == Pose.CROUCHING && Math.abs(player.getBbHeight() - 0.9) < 1e-6,
                "Crouching must use half the standing collision height");
        controller.accept(new MovementInput(1, false, false, 0, 0), true, true, MovementPhysics.canStand(player), 1, NAttributes.read(player));
        player.tick();
        helper.assertTrue(player.getPose() == Pose.CROUCHING, "Releasing C must not stand into a ceiling");
        helper.setBlock(2, 3, 2, Blocks.AIR);
        player.tick();
        helper.assertTrue(player.getPose() == Pose.STANDING, "Standing must recover after the ceiling is removed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void wallContactKeepsCrouchAndDoesNotConsumeJumpResources(GameTestHelper helper) {
        Player player=player(helper);
        player.setDeltaMovement(0,-0.08,0);
        helper.setBlock(2,2,3,Blocks.STONE);
        helper.setBlock(2,3,3,Blocks.STONE);
        var controller=MovementPhysics.controller(player);
        long sequence=0;
        for (int tick=0;tick<61;tick++) {
            player.tickCount=tick;
            if (tick%20==0) {
                controller.accept(new MovementInput(sequence++,true,false,0,0,false,false,0,1),
                        true,MovementPhysics.grounded(player),MovementPhysics.canStand(player),tick,NAttributes.read(player));
            }
            MovementPhysics.observe(player);
            controller.expire(tick);
            MovementPhysics.applyCrouch(player);
            player.travel(new Vec3(0,0,0.55));
            helper.assertTrue(MovementPhysics.grounded(player),"The wall fixture must retain real floor contact");
            yagen.waitmydawn.ninjas_free_play.player.MovementActionHooks.tick(player);
            helper.assertTrue(controller.posture().crouching() && MovementPhysics.low(player)
                    && Math.abs(player.getBbHeight()-0.9)<1e-6,"Wall collision must not release the held compact posture");
            helper.assertTrue(controller.resources().jumpsUsed()==0 && !controller.resources().bulletJumpUsed(),
                    "Contacting a wall must not manufacture a jump or spend its resources");
        }
        helper.assertTrue(player.horizontalCollision,"The fixture must actually collide with the wall");
        helper.assertTrue(yagen.waitmydawn.ninjas_free_play.api.movement.MovementApi.isActive(player,
                yagen.waitmydawn.ninjas_free_play.api.movement.MovementAction.CROUCH),"Crouch must remain an active gameplay action at the wall");
        var manual=controller.accept(new MovementInput(sequence,true,true,0,-45),true,true,true,62,NAttributes.read(player));
        helper.assertTrue(manual.jump()==JumpKind.BULLET,"A deliberate jump press at the wall must still allow the crouch/jump combination");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bulletImpulsePreservesMomentumAndUsesPitch(GameTestHelper helper) {
        Player player = player(helper);
        player.setDeltaMovement(0.1, 0.2, 0.3);
        var input = new MovementInput(0, true, true, 90, -45);
        var values = NAttributes.read(player);
        var decision = MovementPhysics.controller(player).accept(input, true, MovementPhysics.grounded(player), MovementPhysics.canStand(player), 0, values);
        MovementPhysics.applyJump(player, decision.jump(), input, values);
        Vec3 motion = player.getDeltaMovement();
        double diagonal = values.bulletJumpPerTick() / Math.sqrt(2);
        helper.assertTrue(Math.abs(motion.x - (0.1 - diagonal)) < 1e-6 && Math.abs(motion.y - (0.2 + diagonal + values.bulletLiftPerTick())) < 1e-6
                && Math.abs(motion.z - 0.3) < 1e-6, "Bullet impulse must add to momentum along the full look direction");
        helper.assertFalse(player.onGround(), "Takeoff must suppress vanilla duplicate ground-jump inference");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void airJumpReadsTheAttributeAndPreservesExternalMomentum(GameTestHelper helper) {
        Player player = player(helper);
        player.getAttribute(NAttributes.AIR_JUMP_STRENGTH.get()).setBaseValue(12);
        player.setDeltaMovement(0.17, -1, -0.2);
        player.fallDistance = 5;
        var input = new MovementInput(0, false, true, 0, 0);
        MovementPhysics.applyJump(player, JumpKind.AIR, input, NAttributes.read(player));
        Vec3 motion = player.getDeltaMovement();
        helper.assertTrue(Math.abs(motion.x - 0.17) < 1e-6 && Math.abs(motion.y - 0.6) < 1e-6
                && Math.abs(motion.z + 0.2) < 1e-6, "Air jump must use its effective attribute and preserve horizontal momentum");
        helper.assertTrue(player.fallDistance == 5, "Air jump must not silently clear accumulated fall distance");
        player.setDeltaMovement(motion.x, 0.8, motion.z);
        MovementPhysics.applyJump(player, JumpKind.AIR, input, NAttributes.read(player));
        helper.assertTrue(Math.abs(player.getDeltaMovement().y - 0.8) < 1e-6, "Air jump must not reduce a faster upward impulse");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bulletJumpUsesVanillaWallCollision(GameTestHelper helper) {
        Player player = player(helper);
        helper.setBlock(2, 2, 3, Blocks.STONE);
        helper.setBlock(2, 3, 3, Blocks.STONE);
        double originalZ = player.getZ();
        MovementPhysics.applyJump(player, JumpKind.BULLET, new MovementInput(0, true, true, 0, 0), NAttributes.read(player));
        player.move(MoverType.SELF, player.getDeltaMovement());
        helper.assertTrue(player.getZ() <= originalZ + 0.20001, "Movement must stop at the wall instead of teleporting through it");
        helper.assertTrue(player.level().noCollision(player, player.getBoundingBox().deflate(1e-7)), "Collision must not leave the player embedded");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void payloadsPreserveInitialAcknowledgementAndActionPhase(GameTestHelper helper) {
        Player player = player(helper);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var input = new MovementInputPayload(new MovementInput(17, true, true, 179.9f, -89.9f, true, true, -0.5f, 0.75f),
                    JumpKind.BULLET, 1.2, 0.42, true, 0.6);
            MovementInputPayload.write(buffer, input);
            helper.assertTrue(input.equals(MovementInputPayload.read(buffer)), "Input payload must retain its sequence, buttons and angles");
            buffer.clear();
            var initial = new MovementSnapshot(-1, 1, true, false, false, false, -1, JumpKind.NONE, 0, 0, 0);
            initial = initial.withPosture(new PostureSnapshot(true, true, false, true, true, 17, -90, 3, 9, 13, 12,
                    RollKind.LEFT, 0, 0.6));
            var state = new MovementStatePayload(player.getUUID(), initial, true, new Vec3(0.1, -0.4, 0.3));
            MovementStatePayload.write(buffer, state);
            helper.assertTrue(state.equals(MovementStatePayload.read(buffer)), "State payload must retain the signed initial sequence and correction velocity");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void crouchHeightChangesRespectTheCeilingAndScaleTheEye(GameTestHelper helper) {
        Player player = player(helper);
        var controller = MovementPhysics.controller(player);
        controller.accept(new MovementInput(0, true, false, 0, 0), true, true, true, 0, NAttributes.read(player));
        MovementPhysics.applyCrouch(player);
        helper.assertTrue(Math.abs(player.getEyeHeight() - 0.81) < 1e-5, "Half-height crouch must also lower the eye");
        helper.setBlock(2, 3, 2, Blocks.STONE);
        player.getAttribute(NAttributes.CROUCH_HEIGHT_RATIO.get()).setBaseValue(0.75);
        MovementPhysics.applyCrouch(player);
        helper.assertTrue(Math.abs(player.getBbHeight() - 0.9) < 1e-5, "Increasing height must wait for sufficient headroom");
        helper.setBlock(2, 3, 2, Blocks.AIR);
        MovementPhysics.applyCrouch(player);
        helper.assertTrue(Math.abs(player.getBbHeight() - 1.35) < 1e-5, "The configured ratio must apply once it fits");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void zeroLiftRestoresThePureLookImpulse(GameTestHelper helper) {
        Player player = player(helper);
        var input = new MovementInput(0, true, true, 0, 0);
        player.setDeltaMovement(0.1, 0.2, 0.3);
        MovementPhysics.applyJump(player, JumpKind.BULLET, input, NAttributes.read(player));
        helper.assertTrue(Math.abs(player.getDeltaMovement().y - 0.62) < 1e-6, "Default lift must add one ordinary-jump-sized vertical impulse");
        player.getAttribute(NAttributes.BULLET_JUMP_LIFT.get()).setBaseValue(0);
        player.setDeltaMovement(0.1, 0.2, 0.3);
        MovementPhysics.applyJump(player, JumpKind.BULLET, input, NAttributes.read(player));
        helper.assertTrue(Math.abs(player.getDeltaMovement().y - 0.2) < 1e-6, "Zero lift must preserve the original vertical momentum");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rollKeepsTheHalfHeightAndCannotStandAfterExpiringUnderACeiling(GameTestHelper helper) {
        Player player = player(helper);
        var values = NAttributes.read(player);
        var controller = MovementPhysics.controller(player);
        var input = new MovementInput(0, false, false, 0, 0, true, false, 0, 1);
        var decision = controller.accept(input, true, true, true, 0, values);
        helper.assertTrue(decision.rollStarted(), "The roll input must start a roll");
        MovementPhysics.applyCrouch(player);
        MovementPhysics.applyRoll(player);
        helper.assertTrue(Math.abs(player.getBbHeight() - 0.9) < 1e-5, "Roll must use the compact collision height");
        helper.assertTrue(controller.resources().jumpsUsed() == 0, "Roll must not spend a jump");
        helper.setBlock(2, 3, 2, Blocks.STONE);
        player.tickCount = values.rollTicks();
        MovementPhysics.observe(player);
        MovementPhysics.applyCrouch(player);
        helper.assertTrue(Math.abs(player.getBbHeight() - 0.9) < 1e-5, "Roll expiry must preserve the low collision height under a ceiling");
        helper.setBlock(2, 3, 2, Blocks.AIR);
        MovementPhysics.observe(player);
        MovementPhysics.applyCrouch(player);
        helper.assertTrue(player.getPose() == Pose.STANDING, "Roll expiry must recover standing when it fits");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rollingSustainsTranslationAcrossMultipleFrictionTicks(GameTestHelper helper) {
        Player player = player(helper);
        for (int z=1; z<=6; z++) helper.setBlock(2,1,z,Blocks.STONE);
        var input = new MovementInput(0,false,false,0,0,true,false,0,1);
        var values = NAttributes.read(player);
        var result = MovementPhysics.controller(player).accept(input,true,true,true,true,0,values);
        helper.assertTrue(result.rollStarted(), "Forward roll must start");
        MovementPhysics.applyCrouch(player);
        MovementPhysics.applyRoll(player);
        double start = player.getZ();
        for (int tick=0; tick<6; tick++) {
            player.tickCount=tick;
            player.travel(Vec3.ZERO);
        }
        helper.assertTrue(player.getZ()-start > 3.3 && player.getZ()-start < 3.7,
                "Six roll ticks must keep moving instead of collapsing under ordinary walking friction");
        helper.assertTrue(player.level().noCollision(player,player.getBoundingBox().deflate(1e-7)), "Roll drive must use collision-resolved movement");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sideRollUsesStandingHeightAndRequiresHandstandClearance(GameTestHelper helper) {
        Player player = player(helper);
        var input = new MovementInput(0,false,false,0,0,true,false,1,0);
        helper.setBlock(2,4,2,Blocks.STONE);
        helper.assertTrue(MovementPhysics.canStand(player), "The ordinary standing volume should fit");
        helper.assertFalse(MovementPhysics.canRoll(player,input), "A handspring must also have overhead clearance");
        var controller=MovementPhysics.controller(player);
        helper.assertFalse(controller.accept(input,true,true,true,false,0,NAttributes.read(player)).rollStarted(), "Reject an obstructed side roll");
        helper.setBlock(2,4,2,Blocks.AIR);
        controller.accept(new MovementInput(1,false,false,0,0),true,true,true,true,1,NAttributes.read(player));
        var next=new MovementInput(2,false,false,0,0,true,false,1,0);
        helper.assertTrue(controller.accept(next,true,true,true,true,2,NAttributes.read(player)).rollStarted(), "Side roll should start in open space");
        MovementPhysics.applyCrouch(player);
        helper.assertTrue(Math.abs(player.getBbHeight()-1.8)<1e-5 && !MovementPhysics.low(player), "A side roll must not be flattened to half height");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void blockedRollStopsItsMotorAtTheWall(GameTestHelper helper) {
        Player player=player(helper);
        helper.setBlock(2,2,3,Blocks.STONE);
        helper.setBlock(2,3,3,Blocks.STONE);
        double start=player.getZ();
        var controller=MovementPhysics.controller(player);
        controller.accept(new MovementInput(0,false,false,0,0,true,false,0,1),true,true,true,true,0,NAttributes.read(player));
        MovementPhysics.applyCrouch(player);
        MovementPhysics.applyRoll(player);
        player.travel(Vec3.ZERO);
        player.tickCount=1;
        player.travel(Vec3.ZERO);
        helper.assertTrue(player.getZ()<=start+0.20001, "Roll drive must not push through the wall");
        helper.assertFalse(controller.posture().rolling(1), "A blocked roll must stop its motor");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void airRollPreservesBulletMomentumInEveryDirection(GameTestHelper helper) {
        for (float[] input : new float[][]{{0,1},{1,0},{-1,0},{0,-1}}) {
            Player player=player(helper);
            player.setPos(helper.absoluteVec(new Vec3(2.5,12,2.5)));
            player.setOnGround(false);
            helper.assertTrue(player.level().noCollision(player,player.getBoundingBox()),"Air-momentum fixture requires an unobstructed initial volume");
            player.setDeltaMovement(0.25,0.42,1.1);
            var controller=MovementPhysics.controller(player);
            controller.accept(new MovementInput(0,false,false,0,0,true,true,input[0],input[1]),
                    true,false,true,true,0,NAttributes.read(player));
            MovementPhysics.applyCrouch(player);
            MovementPhysics.applyRoll(player);
            for (int tick=0;tick<3;tick++) {
                player.tickCount=tick;
                player.travel(Vec3.ZERO);
                Vec3 velocity=player.getDeltaMovement();
                helper.assertTrue(Math.abs(velocity.x-0.25)<1e-6 && Math.abs(velocity.z-1.1)<1e-6,
                        "Air roll must retain incoming momentum: direction="+input[0]+","+input[1]+", tick="+tick+", velocity="+velocity);
            }
            double expectedY=((0.42-0.08)*0.98-0.08)*0.98;
            expectedY=(expectedY-0.08)*0.98;
            helper.assertTrue(Math.abs(player.getDeltaMovement().y-expectedY)<1e-6,
                    "Preserving horizontal momentum must not disable vertical gravity or drag");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rollMomentumStillResolvesWallsAndRestoresNormalDrag(GameTestHelper helper) {
        Player player=player(helper);
        for (int z=2;z<7;z++) helper.setBlock(2,1,z,Blocks.STONE);
        player.setDeltaMovement(0,0,0.95);
        var controller=MovementPhysics.controller(player);
        controller.accept(new MovementInput(0,false,false,0,0,true,false,0,1),true,true,true,true,0,NAttributes.read(player));
        MovementPhysics.applyCrouch(player);
        player.travel(Vec3.ZERO);
        helper.assertTrue(Math.abs(player.getDeltaMovement().z-0.95)<1e-6,"Ground friction must not erase incoming momentum during the roll");
        controller.posture().stopRoll(1);
        player.tickCount=1;
        player.travel(Vec3.ZERO);
        helper.assertTrue(player.getDeltaMovement().z<0.9,"Normal friction must resume when the roll ends");
        helper.succeed();
    }
}
