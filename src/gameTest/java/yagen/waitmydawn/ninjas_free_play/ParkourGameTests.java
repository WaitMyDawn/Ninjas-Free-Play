package yagen.waitmydawn.ninjas_free_play;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import yagen.waitmydawn.ninjas_free_play.api.attribute.NAttributes;
import yagen.waitmydawn.ninjas_free_play.api.movement.ContactDamageContext;
import yagen.waitmydawn.ninjas_free_play.core.*;
import yagen.waitmydawn.ninjas_free_play.player.*;
import yagen.waitmydawn.ninjas_free_play.network.MovementStatePayload;
import yagen.waitmydawn.ninjas_free_play.network.MovementInputPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

@GameTestHolder(NinjasFreePlay.MODID)
@PrefixGameTestTemplate(false)
public final class ParkourGameTests {
    private ParkourGameTests() {}
    private static Player air(GameTestHelper helper) {
        var player=helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(new Vec3(2.5,12,2.5)));
        player.setOnGround(false);player.setDeltaMovement(Vec3.ZERO);
        return player;
    }
    private static MovementInput input(long seq,boolean crouch,boolean jump,boolean aim) {
        return new MovementInput(seq,crouch,jump,0,-45,false,false,0,1,aim);
    }
    private static void kick(Player player) {
        player.setDeltaMovement(0,0,.5);
        ParkourPhysics.runtime(player).displacement=new Vec3(0,0,.5);
        MovementPhysics.controller(player).accept(input(0,true,false,false),true,false,true,true,0,NAttributes.read(player),
                new ParkourFrame(0,.5,0,0,.5,WallContact.NONE));
    }
    private static void wall(GameTestHelper helper,boolean upper) {
        helper.setBlock(2,12,3,Blocks.STONE);
        helper.setBlock(2,13,3,upper?Blocks.STONE:Blocks.AIR);
        helper.setBlock(2,14,3,upper?Blocks.STONE:Blocks.AIR);
    }
    @GameTest(template="empty")
    public static void ordinaryJumpImmediatelyGlidingRisesOneBlockThenDescends(GameTestHelper helper) {
        var player=air(helper);
        helper.setBlock(2,11,2,Blocks.STONE);
        player.setOnGround(true);
        var controller=MovementPhysics.controller(player);
        var jump=new MovementInput(0,false,true,0,0,false,false,0,0,true);
        var decision=controller.accept(jump,true,true,true,true,0,NAttributes.read(player),ParkourFrame.EMPTY);
        MovementPhysics.applyJump(player,decision.jump(),jump,NAttributes.read(player));
        controller.accept(new MovementInput(1,false,false,0,0,false,false,0,0,true),true,false,true,true,0,NAttributes.read(player),ParkourFrame.EMPTY);
        double initial=player.getY(),peak=initial;
        for(int tick=0;tick<20;tick++) {
            player.tickCount=tick;player.aiStep();peak=Math.max(peak,player.getY());
        }
        helper.assertTrue(Math.abs(peak-initial-1)<.015,"An ordinary default jump immediately gliding must rise about one block; actual="+(peak-initial));
        helper.assertTrue(player.getY()<peak && player.getDeltaMovement().y<0,"The player must descend after the apex instead of gaining indefinite height");
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void defaultDescendingGlideIsStrongerThanThePreviousFortyTickFall(GameTestHelper helper) {
        var player=air(helper);helper.setBlock(2,10,2,Blocks.STONE);
        MovementPhysics.controller(player).accept(input(0,false,false,true),true,false,true,true,0,NAttributes.read(player),ParkourFrame.EMPTY);
        int landed=0;
        for(int tick=0;tick<20;tick++) {
            player.tickCount=tick;player.aiStep();
            if(MovementPhysics.grounded(player)) {landed=tick+1;break;}
        }
        helper.assertTrue(landed>=10 && landed<=15,"The new default descending glide must fall one block in 10-15 ticks; actual="+landed);
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void glideCanSteerWithoutDestroyingOrInflatingCarriedMomentum(GameTestHelper helper) {
        var player=air(helper);player.setDeltaMovement(0,0,.5);
        MovementPhysics.controller(player).accept(input(0,false,false,true),true,false,true,true,0,NAttributes.read(player),ParkourFrame.EMPTY);
        player.travel(new Vec3(.35,0,0));
        helper.assertTrue(player.getDeltaMovement().x>0,"Air control must be able to turn the glide");
        helper.assertTrue(Math.abs(player.getDeltaMovement().horizontalDistance()-.5)<1e-6,"Steering must retain the incoming speed rather than grow or discard it");
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void latchRejectsOneBlockAndAcceptsTheContinuousLowerBodySurface(GameTestHelper helper) {
        var player=air(helper);wall(helper,false);
        helper.assertTrue(WallProbe.find(player).present()&&!WallProbe.find(player).latchable(),"A single protruding block can be dashed from but cannot support a latch");
        wall(helper,true);
        var full=WallProbe.find(player);
        helper.assertTrue(full.latchable()&&full.z()==-1,"A continuous lower-body wall must support a latch with the outward normal");
        helper.setBlock(2,13,3,Blocks.AIR);
        helper.assertFalse(WallProbe.find(player).latchable(),"A gap in the required vertical coverage must invalidate attachment");
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void latchStopsFallingAndWallRemovalContinuesTheSameBudget(GameTestHelper helper) {
        var player=air(helper);wall(helper,true);
        player.setDeltaMovement(0,-.6,0);player.fallDistance=12;
        var controller=MovementPhysics.controller(player);
        controller.accept(input(0,false,false,true),true,false,true,true,0,NAttributes.read(player),ParkourPhysics.frame(player,true));
        double y=player.getY();player.travel(Vec3.ZERO);
        helper.assertTrue(controller.parkour().mode()==ParkourMode.WALL_LATCH&&Math.abs(player.getY()-y)<1e-6,"A supported latch must hold altitude");
        helper.assertTrue(player.fallDistance==0,"A valid latch must reset accumulated falling");
        double spent=controller.resources().assistedSecondsUsed();
        for(int height=12;height<=14;height++) helper.setBlock(2,height,3,Blocks.AIR);
        player.tickCount=1;player.travel(Vec3.ZERO);
        helper.assertTrue(controller.parkour().mode()==ParkourMode.AIM_GLIDE,"Removing the wall must transition into glide while aim remains held");
        helper.assertTrue(controller.resources().assistedSecondsUsed()>spent,"Losing support must not reset the shared duration");
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void wallDashRefreshesResourcesAndResetsFallDistance(GameTestHelper helper) {
        var player=air(helper);wall(helper,true);
        var values=NAttributes.read(player);var controller=MovementPhysics.controller(player);
        controller.resources().consumeBulletJump(values);controller.resources().consumeJump(values);controller.resources().consumeAssistedSeconds(values,4);
        player.fallDistance=15;
        var input=input(0,false,true,false);
        var result=controller.accept(input,true,false,true,true,0,values,ParkourPhysics.frame(player,true));
        helper.assertTrue(result.jump()==JumpKind.WALL,"A jump near an actual wall must select wall dash even after ordinary resources are exhausted");
        MovementPhysics.applyJump(player,result.jump(),input,values);
        helper.assertTrue(controller.resources().jumpsUsed()==1&&!controller.resources().bulletJumpUsed()&&controller.resources().assistedSecondsUsed()==0,
                "Wall dash must refill resources and then charge its own jump");
        helper.assertTrue(player.fallDistance==0&&player.getDeltaMovement().y>0,"Wall dash must propel the player and clear falling");
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void groundWallContactKeepsTakeoffAndAirKeysSelectDistinctLaunches(GameTestHelper helper) {
        var player=air(helper);wall(helper,true);helper.setBlock(2,11,2,Blocks.STONE);
        player.setOnGround(true);
        var controller=MovementPhysics.controller(player);var values=NAttributes.read(player);
        var first=new MovementInput(0,false,true,0,0,false,false,0,1,false);
        var result=controller.accept(first,true,MovementPhysics.grounded(player),true,true,0,values,ParkourPhysics.frame(player,true));
        helper.assertTrue(result.jump()==JumpKind.GROUND,"A grounded wall contact must remain an ordinary takeoff");
        controller.accept(new MovementInput(1,false,false,0,0),true,false,true,true,1,values,ParkourPhysics.frame(player,true));
        long sequence=2;
        for(float[] keys:new float[][]{{0,0,1},{0,1,0},{0,-1,0},{0,0,-1},{90,0,1},{-90,0,1}}) {
            player.tickCount=(int)sequence*10;player.setOnGround(false);
            var input=new MovementInput(sequence++,false,true,keys[0],0,false,false,keys[1],keys[2],false);
            result=controller.accept(input,true,false,true,true,player.tickCount,values,ParkourPhysics.frame(player,true));
            MovementPhysics.applyJump(player,result.jump(),input,values);
            var plan=controller.snapshot(player.tickCount).parkour().wallDash();
            var expected=plan.direction();var actual=player.getDeltaMovement();
            helper.assertTrue(result.jump()==JumpKind.WALL && Math.abs(actual.length()-.7)<1e-6,"Each airborne wall launch must use the wall strength attribute");
            helper.assertTrue(actual.distanceTo(new Vec3(expected.x(),expected.y(),expected.z()).scale(.7))<1e-6,"Physics must use the same retained plan as animation and synchronization");
        }
        var payload=new MovementStatePayload(player.getUUID(),controller.snapshot(player.tickCount),false,Vec3.ZERO);
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try {
            MovementStatePayload.STREAM_CODEC.encode(buffer,payload);
            helper.assertTrue(payload.equals(MovementStatePayload.STREAM_CODEC.decode(buffer)),"The launch kind and original wall normal must survive synchronization");
        } finally {buffer.release();}
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void groundedWallTakeoffCannotChainUntilTheJumpKeyIsReleasedAndPressedInAir(GameTestHelper helper) {
        var player=air(helper);wall(helper,true);helper.setBlock(2,11,2,Blocks.STONE);
        player.setOnGround(true);var controller=MovementPhysics.controller(player);var values=NAttributes.read(player);
        var first=new MovementInput(0,false,true,0,0,false,false,0,1,false);
        var decision=controller.accept(first,true,MovementPhysics.grounded(player),true,true,0,values,ParkourPhysics.frame(player,true));
        MovementPhysics.applyJump(player,decision.jump(),first,values);
        double start=player.getY(),peak=start;
        for(int tick=0;tick<8;tick++) {
            player.tickCount=tick;
            if(tick>0) {
                var held=new MovementInput(tick,false,true,0,0,false,false,0,1,false);
                var next=controller.accept(held,true,MovementPhysics.grounded(player),true,true,tick,values,ParkourPhysics.frame(player,true));
                helper.assertTrue(next.jump()==JumpKind.NONE,"A held takeoff key must not become a wall jump on later airborne ticks");
                MovementPhysics.applyJump(player,next.jump(),held,values);
            }
            player.aiStep();peak=Math.max(peak,player.getY());
        }
        helper.assertTrue(decision.jump()==JumpKind.GROUND&&peak-start<1.4,"The real trajectory must remain an ordinary takeoff");
        controller.accept(new MovementInput(8,false,false,0,0),true,false,true,true,8,values,ParkourPhysics.frame(player,true));
        var press=new MovementInput(9,false,true,0,0,false,false,0,1,false);
        var airborne=controller.accept(press,true,false,true,true,9,values,ParkourPhysics.frame(player,true));
        helper.assertTrue(airborne.jump()==JumpKind.WALL,"A new airborne press must still start wall jumping");
        MovementPhysics.applyJump(player,airborne.jump(),press,values);
        helper.assertTrue(Math.abs(player.getDeltaMovement().length()-.7)<1e-6,"The deliberately started wall jump must keep its configured power");
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void airborneCrouchJumpNearAWallKeepsBulletPowerAndDoesNotRefreshSpentResources(GameTestHelper helper) {
        var player=air(helper);wall(helper,true);
        var controller=MovementPhysics.controller(player);var values=NAttributes.read(player);
        controller.resources().consumeAssistedSeconds(values,2);
        var intent=new MovementInput(0,true,true,0,0,false,false,0,1,false);
        var decision=controller.accept(intent,true,false,true,true,0,values,ParkourPhysics.frame(player,true));
        MovementPhysics.applyJump(player,decision.jump(),intent,values);
        helper.assertTrue(decision.jump()==JumpKind.BULLET,"Crouch+jump near the wall must choose bullet jump");
        helper.assertTrue(Math.abs(player.getDeltaMovement().z-.9)<1e-6&&Math.abs(player.getDeltaMovement().y-.42)<1e-6,"The selected action must apply bullet impulse and lift rather than wall impulse");
        helper.assertTrue(controller.resources().jumpsUsed()==2&&controller.resources().bulletJumpUsed(),"The ordinary shared jump slot and bullet eligibility must be consumed");
        helper.assertTrue(controller.resources().assistedSecondsUsed()==2,"Crouch+jump must not secretly refill resources through a wall jump");
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void slideUsesAttributeFrictionInsteadOfOrdinaryGroundDrag(GameTestHelper helper) {
        var player=air(helper);
        helper.setBlock(2,11,2,Blocks.STONE);helper.setBlock(2,11,3,Blocks.STONE);
        player.setOnGround(true);player.setDeltaMovement(0,-.08,.5);
        ParkourPhysics.runtime(player).displacement=new Vec3(0,0,.5);
        var controller=MovementPhysics.controller(player);
        var frame=ParkourPhysics.frame(player,false);
        var entry=controller.accept(input(0,true,false,false),true,true,true,true,0,NAttributes.read(player),frame);
        if(entry.slideStarted()) ParkourPhysics.applySlide(player,frame,NAttributes.read(player));
        MovementPhysics.applyCrouch(player);player.travel(Vec3.ZERO);
        helper.assertTrue(controller.parkour().mode()==ParkourMode.SLIDE,"Full-speed crouch must enter slide");
        helper.assertTrue(Math.abs(player.getDeltaMovement().z-.7275)<1e-6,"Slide must boost once then retain 97 percent, rather than ordinary walking drag: "+player.getDeltaMovement());
        helper.assertTrue(Math.abs(player.getBbHeight()-.9)<1e-5,"Slide must use the configured compact collision height");
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void slidePreservesSprintWhileCompactCrouchingStillCancelsIt(GameTestHelper helper) {
        var player=air(helper);helper.setBlock(2,11,2,Blocks.STONE);
        player.setOnGround(true);player.setSprinting(true);player.setDeltaMovement(0,-.08,.5);
        ParkourPhysics.runtime(player).displacement=new Vec3(0,0,.5);
        var controller=MovementPhysics.controller(player);
        var frame=ParkourPhysics.frame(player,false);
        var entry=controller.accept(input(0,true,false,false),true,true,true,true,0,NAttributes.read(player),frame);
        if(entry.slideStarted()) ParkourPhysics.applySlide(player,frame,NAttributes.read(player));
        MovementPhysics.applyCrouch(player);
        helper.assertTrue(controller.parkour().mode()==ParkourMode.SLIDE&&player.isSprinting(),"Slide collision posture must preserve the sprint flag");
        player.travel(Vec3.ZERO);
        helper.assertTrue(Math.abs(player.getDeltaMovement().z-.7275)<1e-6,"Sprint must not replace slide inertia or the friction attribute");
        controller.accept(input(1,false,false,false),true,true,true,true,1,NAttributes.read(player),ParkourFrame.EMPTY);
        controller.accept(input(2,true,false,false),true,true,true,true,2,NAttributes.read(player),ParkourFrame.EMPTY);
        MovementPhysics.applyCrouch(player);
        helper.assertFalse(player.isSprinting(),"Stationary crouching must retain its ordinary sprint cancellation");
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void walkingIntoSlideCarriesActualSpeedThroughTwentyFullPhysicsSteps(GameTestHelper helper) {
        var player=air(helper);
        for(int z=1;z<=15;z++) helper.setBlock(2,11,z,Blocks.STONE);
        player.setOnGround(true);player.setDeltaMovement(0,-.08,0);
        for(int tick=0;tick<18;tick++) {player.tickCount=tick;player.zza=1;player.aiStep();}
        var frame=ParkourPhysics.frame(player,false);
        helper.assertTrue(frame.speed()>.2157 && player.getDeltaMovement().horizontalDistance()<.13,
                "Walking must expose the real distinction between measured movement and post-drag velocity");
        var controller=MovementPhysics.controller(player);
        var entry=controller.accept(input(0,true,false,false),true,true,true,true,18,NAttributes.read(player),frame);
        if(entry.slideStarted()) ParkourPhysics.applySlide(player,frame,NAttributes.read(player));
        MovementPhysics.applyCrouch(player);
        double start=player.getZ(),speed=frame.speed();
        for(int step=0;step<20;step++) {
            player.tickCount=18+step;player.zza=0;
            if(step==5) {
                var held=controller.accept(input(1,true,false,false),true,true,true,true,player.tickCount,NAttributes.read(player),ParkourPhysics.frame(player,false));
                helper.assertFalse(held.slideStarted(),"Held crouch must not reapply the entry boost");
            }
            double before=player.getZ();player.aiStep();
            helper.assertTrue(controller.parkour().mode()==ParkourMode.SLIDE,"The slide must not fall below its threshold immediately after entry");
            helper.assertTrue(Math.abs(player.getZ()-before-speed*1.5*Math.pow(.97,step))<.001,
                    "Each full step must carry the one-shot 1.5 boost and configured friction: step="+step+" actual="+(player.getZ()-before));
        }
        helper.assertTrue(player.getZ()-start>4.8,"The real walking-to-slide path must retain useful travel distance");
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void glideOverlaysRollAndKickWithoutReplacingTheirMotionOrDamageState(GameTestHelper helper) {
        for(boolean roll:new boolean[]{true,false}) {
            var player=air(helper);player.setDeltaMovement(0,-.4,.6);
            ParkourPhysics.runtime(player).displacement=new Vec3(0,0,.6);
            var input=new MovementInput(0,!roll,false,0,0,roll,false,0,1,true);
            var controller=MovementPhysics.controller(player);
            var decision=controller.accept(input,true,false,true,true,0,NAttributes.read(player),ParkourPhysics.frame(player,false));
            if(decision.rollStarted()) MovementPhysics.applyRoll(player);
            double before=player.getY();player.travel(Vec3.ZERO);
            helper.assertTrue(controller.parkour().gliding(),"Right-click must activate glide while the primary action continues");
            helper.assertTrue(roll?controller.posture().rolling(0):controller.parkour().mode()==ParkourMode.FLYING_KICK,"Glide must not replace the roll or kick");
            helper.assertTrue(Math.abs(player.getDeltaMovement().z-.6)<1e-6,"Glide must preserve roll/kick momentum");
            helper.assertTrue(Math.abs(before-player.getY()-.12)<1e-6,"The glide descent cap must apply to the real combined travel");
            var payload=new MovementStatePayload(player.getUUID(),controller.snapshot(0),false,Vec3.ZERO);
            var buffer=new FriendlyByteBuf(Unpooled.buffer());
            try {
                MovementStatePayload.STREAM_CODEC.encode(buffer,payload);
                helper.assertTrue(payload.equals(MovementStatePayload.STREAM_CODEC.decode(buffer)),"Both primary action and independent glide must round-trip");
            } finally {buffer.release();}
        }
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void slideMultiplierAttributeAndPredictionPacketUseTheSameEntryValue(GameTestHelper helper) {
        var player=air(helper);helper.setBlock(2,11,2,Blocks.STONE);player.setOnGround(true);
        player.getAttribute(NAttributes.SLIDE_SPEED_MULTIPLIER).setBaseValue(2);
        player.setDeltaMovement(0,-.08,.3);ParkourPhysics.runtime(player).displacement=new Vec3(0,0,.3);
        var values=NAttributes.read(player);var frame=ParkourPhysics.frame(player,false);
        var intent=input(0,true,false,false);var controller=MovementPhysics.controller(player);
        var decision=controller.accept(intent,true,true,true,true,0,values,frame);
        if(decision.slideStarted()) ParkourPhysics.applySlide(player,frame,values);
        MovementPhysics.applyCrouch(player);player.travel(Vec3.ZERO);
        helper.assertTrue(Math.abs(player.getDeltaMovement().z-.582)<1e-6,"A custom multiplier must apply before the configured friction");
        var payload=new MovementInputPayload(intent,JumpKind.NONE,0,0,false,0,ParkourMode.SLIDE,0,0,true,2,.6);
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try {
            MovementInputPayload.STREAM_CODEC.encode(buffer,payload);
            helper.assertTrue(payload.equals(MovementInputPayload.STREAM_CODEC.decode(buffer)),"Slide prediction and multiplier must round-trip for server correction");
        } finally {buffer.release();}
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void landingKickStartsOneBoostedSlideWithoutStackingRepeatedUpdates(GameTestHelper helper) {
        var player=air(helper);kick(player);
        helper.setBlock(2,11,2,Blocks.STONE);player.setOnGround(true);
        ParkourPhysics.update(player);ParkourPhysics.update(player);
        helper.assertTrue(MovementPhysics.controller(player).parkour().mode()==ParkourMode.SLIDE,"Landing kick must transition into slide");
        helper.assertTrue(Math.abs(player.getDeltaMovement().z-.75)<1e-6,"The landing transition must boost once across repeated updates");
        player.travel(Vec3.ZERO);
        helper.assertTrue(Math.abs(player.getDeltaMovement().z-.7275)<1e-6,"The first travel must decay rather than apply another boost");
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void oneHundredRealSlideEntriesKeepTheOriginalSpeedTarget(GameTestHelper helper) {
        var player=air(helper);helper.setBlock(2,11,2,Blocks.STONE);helper.setBlock(2,11,3,Blocks.STONE);
        var origin=player.position();var controller=MovementPhysics.controller(player);var values=NAttributes.read(player);
        double incoming=.3;
        for(int entry=0;entry<100;entry++) {
            player.tickCount=entry*2;player.setPos(origin);player.setOnGround(true);
            player.setDeltaMovement(0,-.08,incoming);
            ParkourPhysics.runtime(player).displacement=new Vec3(0,0,incoming);
            var frame=ParkourPhysics.frame(player,false);
            controller.accept(input(entry*2,false,false,false),true,true,true,true,player.tickCount,values,frame);
            var decision=controller.accept(input(entry*2+1,true,false,false),true,true,true,true,player.tickCount,values,frame);
            helper.assertTrue(decision.slideStarted(),"Each release and press must remain a responsive new slide");
            ParkourPhysics.applySlide(player,frame,values);
            helper.assertTrue(Math.abs(player.getDeltaMovement().z-.45)<1e-6,"Repeated entry must not compound the 1.5 multiplier");
            MovementPhysics.applyCrouch(player);player.travel(Vec3.ZERO);
            helper.assertTrue(Math.abs(player.getDeltaMovement().z-.4365)<1e-6,"Every real travel step must apply the 0.03 default friction");
            incoming=player.getDeltaMovement().z;
        }
        helper.assertTrue(Math.abs(controller.snapshot(player.tickCount).parkour().slideBoost().referenceSpeed()-.3)<1e-6,"The stored reference must remain the first unboosted speed");
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void slideReferenceRearmsOnlyAfterNormalGroundMovementAtCurrentAttributeSpeed(GameTestHelper helper) {
        var player=air(helper);helper.setBlock(2,11,2,Blocks.STONE);player.setOnGround(true);
        var controller=MovementPhysics.controller(player);var values=NAttributes.read(player);
        player.setDeltaMovement(0,-.08,.3);ParkourPhysics.runtime(player).displacement=new Vec3(0,0,.3);
        var frame=ParkourPhysics.frame(player,false);
        controller.accept(input(0,true,false,false),true,true,true,true,0,values,frame);ParkourPhysics.applySlide(player,frame,values);
        controller.accept(input(1,false,false,false),true,true,true,true,0,values,frame);MovementPhysics.applyCrouch(player);
        player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(.2);
        player.setDeltaMovement(0,-.08,.12);ParkourPhysics.runtime(player).displacement=new Vec3(0,0,.35);
        for(int tick=1;tick<=5;tick++) {player.tickCount=tick;ParkourPhysics.update(player);ParkourPhysics.update(player);}
        helper.assertTrue(controller.snapshot(5).parkour().slideBoost().active(),"Repeated updates in five ticks must not prematurely rearm");
        player.tickCount=6;ParkourPhysics.update(player);
        helper.assertFalse(controller.snapshot(6).parkour().slideBoost().active(),"Six genuine ordinary-ground samples must rearm even with modified walking speed");
        var next=ParkourPhysics.frame(player,false);
        controller.accept(input(2,true,false,false),true,true,true,true,6,values,next);ParkourPhysics.applySlide(player,next,values);
        helper.assertTrue(Math.abs(player.getDeltaMovement().z-.525)<1e-6,"Rearmed entry must capture a fresh actual-speed reference");
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void slideReferenceRoundTripsAndOrdinaryReplayReservesWithoutApplyingMotion(GameTestHelper helper) {
        var player=air(helper);var controller=MovementPhysics.controller(player);var values=NAttributes.read(player);
        player.setDeltaMovement(0,.2,.3);
        var pendingFrame=new ParkourFrame(0,.3,0,.2,.3,WallContact.NONE);
        var authoritative=controller.snapshot(0);
        controller.accept(input(0,true,false,false),true,true,true,true,0,values,pendingFrame);
        ParkourPhysics.applySlide(player,pendingFrame,values);
        helper.assertTrue(Math.abs(player.getDeltaMovement().z-.45)<1e-6,"The first predicted entry must apply its impulse");
        controller.restore(authoritative,100);
        controller.accept(input(0,true,false,false),true,true,true,true,100,values,pendingFrame);
        ParkourPhysics.applySlide(player,pendingFrame,values,false);
        helper.assertTrue(Math.abs(player.getDeltaMovement().z-.45)<1e-6,"Ordinary acknowledgement replay must not change predicted motion");
        helper.assertTrue(Math.abs(controller.snapshot(100).parkour().slideBoost().referenceSpeed()-.3)<1e-6,"Replay must reserve the original unboosted reference");
        controller.parkour().slideMomentum().observe(true,true,101,ParkourFrame.EMPTY);
        controller.parkour().slideMomentum().observe(true,true,102,ParkourFrame.EMPTY);
        var payload=new MovementStatePayload(player.getUUID(),controller.snapshot(102),false,Vec3.ZERO);
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try {
            MovementStatePayload.STREAM_CODEC.encode(buffer,payload);
            var decoded=MovementStatePayload.STREAM_CODEC.decode(buffer);
            helper.assertTrue(payload.equals(decoded),"The active reference and partial recovery must round-trip");
            controller.restore(decoded.state(),500);
        } finally {buffer.release();}
        player.setDeltaMovement(0,.2,1.2);
        ParkourPhysics.applySlide(player,new ParkourFrame(0,1.2,0,.2,1.2,WallContact.NONE),values);
        helper.assertTrue(Math.abs(player.getDeltaMovement().z-1.2)<1e-6,"Faster bullet or external momentum must survive an active reference unchanged");
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void unmodifiedNonSprintWalkingCanEnterSlideBeforeCrouchSlowdown(GameTestHelper helper) {
        var player=air(helper);
        for(int z=1;z<=7;z++) helper.setBlock(2,11,z,Blocks.STONE);
        player.setOnGround(true);player.setDeltaMovement(0,-.08,0);
        for(int tick=0;tick<18;tick++) {player.tickCount=tick;player.zza=1;player.aiStep();}
        var frame=ParkourPhysics.frame(player,false);
        helper.assertTrue(Math.abs(frame.speed()-.21585)<.0001&&!player.isSprinting(),"The fixture must attain ordinary unmodified non-sprint walking speed: "+frame.speed());
        MovementPhysics.controller(player).accept(input(0,true,false,false),true,true,true,true,18,NAttributes.read(player),frame);
        helper.assertTrue(MovementPhysics.controller(player).parkour().mode()==ParkourMode.SLIDE,"The measured walking speed must qualify before the crouch input slows movement");
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void kickDamagesEachTargetOnceIncludingPlayers(GameTestHelper helper) {
        var player=air(helper);kick(player);
        var victim=helper.makeMockPlayer(GameType.SURVIVAL);victim.setPos(player.position().add(0,0,.4));
        var second=helper.makeMockPlayer(GameType.SURVIVAL);second.setPos(player.position().add(.1,0,.4));
        float before=victim.getHealth();
        helper.assertTrue(ParkourCombat.hit(player,victim)&&ParkourCombat.hit(player,second),"A flying kick must accept multiple living targets, including players");
        ParkourCombat.hit(player,victim);
        helper.assertTrue(Math.abs(victim.getHealth()-(before-2))<1e-6&&Math.abs(second.getHealth()-18)<1e-6,"Each target takes the configured damage exactly once");
        helper.assertTrue(player.getHealth()==20,"The attacker must not damage itself");
        helper.assertFalse(ParkourCombat.hit(player,player),"The player cannot be its own kick target");
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void kickAddsImpactVelocityWithNativeResistanceWithoutOverwritingCarriedMotion(GameTestHelper helper) {
        var player=air(helper);kick(player);
        for(Vec3 velocity:new Vec3[]{new Vec3(.3,.4,.5),new Vec3(-.4,-.2,.6),new Vec3(0,.5,0),Vec3.ZERO}) {
            for(double multiplier:new double[]{0,.5,1}) for(double resistance:new double[]{0,.5,1}) {
                player.setDeltaMovement(velocity);
                player.getAttribute(NAttributes.FLYING_KICK_KNOCKBACK_MULTIPLIER).setBaseValue(multiplier);
                var victim=helper.makeMockPlayer(GameType.SURVIVAL);victim.setPos(player.position().add(0,0,.4));
                victim.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE).setBaseValue(resistance);
                Vec3 carried=new Vec3(.12,.07,-.03);victim.setDeltaMovement(carried);
                helper.assertTrue(ParkourCombat.hit(player,victim),"A new target must accept its strike");
                Vec3 expected=carried.add(velocity.scale(multiplier*(1-resistance)));
                helper.assertTrue(victim.getDeltaMovement().distanceTo(expected)<1e-6,
                        "Impact velocity must add once with native resistance: "+victim.getDeltaMovement()+" expected "+expected);
                ParkourCombat.hit(player,victim);
                helper.assertTrue(victim.getDeltaMovement().distanceTo(expected)<1e-6,"Repeated collision must not apply another impulse");
            }
        }
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void kickImpulseSupportsNativeKnockbackEventCancellationAndStrengthChanges(GameTestHelper helper) {
        var player=air(helper);kick(player);
        player.getAttribute(NAttributes.FLYING_KICK_KNOCKBACK_MULTIPLIER).setBaseValue(.5);
        var canceled=helper.makeMockPlayer(GameType.SURVIVAL);canceled.setPos(player.position().add(0,0,.4));
        var modified=helper.makeMockPlayer(GameType.SURVIVAL);modified.setPos(canceled.position());
        var count=new int[1];
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent> listener=event -> {
            if(ParkourCombat.ownsDamageKnockback(event.getEntity())) return;
            if(event.getEntity()==canceled) {event.setCanceled(true);count[0]++;}
            if(event.getEntity()==modified) {event.setStrength(event.getStrength()*2);event.setRatioX(-1);event.setRatioZ(0);count[0]++;}
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent.class,listener);
        try {
            ParkourCombat.hit(player,canceled);ParkourCombat.hit(player,modified);
            helper.assertTrue(canceled.getDeltaMovement().equals(Vec3.ZERO),"Canceled knockback must preserve motion");
            helper.assertTrue(modified.getDeltaMovement().distanceTo(new Vec3(.5,0,0))<1e-6,"Event strength and direction changes must apply to the intended impulse");
            helper.assertTrue(count[0]==2,"Exactly one intended knockback event per target must be exposed");
        } finally {net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);}
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void contactGuardOnlyCoversTheActualTouchedTargetInsideTheCollisionCallback(GameTestHelper helper) throws Exception {
        var player=air(helper);kick(player);
        Slime slime=helper.spawn(EntityType.SLIME,new Vec3(2.5,12,2.8));slime.setSize(3,true);
        var touch=Player.class.getDeclaredMethod("touch",Entity.class);touch.setAccessible(true);
        touch.invoke(player,slime);
        helper.assertTrue(player.getHealth()==20&&ParkourPhysics.runtime(player).hitTargets.contains(slime.getUUID()),
                "The actual playerTouch callback must apply the kick before suppressing its contact damage");
        player.invulnerableTime=0;slime.playerTouch(player);
        helper.assertTrue(player.getHealth()<20,"The same entity's melee damage outside the collision scope must remain effective");
        player.setHealth(20);player.invulnerableTime=0;
        try(var scope=ContactDamageContext.collision(player,slime)) {
            player.hurt(player.damageSources().thorns(slime),2);
        }
        helper.assertTrue(player.getHealth()<20,"Thorns and other damage types must not become contact invulnerability");
        player.setHealth(20);player.invulnerableTime=0;
        var other=helper.spawn(EntityType.ZOMBIE,new Vec3(3.5,12,2.5));
        try(var scope=ContactDamageContext.collision(player,slime)) {
            player.hurt(player.damageSources().mobAttack(other),2);
        }
        helper.assertTrue(player.getHealth()<20,"Another entity's attack must not be suppressed by the touched target's scope");
        player.setHealth(20);player.invulnerableTime=0;
        var arrow=EntityType.ARROW.create(helper.getLevel());
        try(var scope=ContactDamageContext.collision(player,slime)) { player.hurt(player.damageSources().arrow(arrow,slime),2); }
        helper.assertTrue(player.getHealth()<20,"The touched entity's projectile must retain its independent damage source");
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void protocolRoundTripsWallBudgetAndUnconsumedUseIntent(GameTestHelper helper) {
        var player=air(helper);wall(helper,true);
        var controller=MovementPhysics.controller(player);
        var input=input(0,false,false,true);
        controller.accept(input,true,false,true,true,10,NAttributes.read(player),ParkourPhysics.frame(player,true));
        controller.update(true,false,true,10,NAttributes.read(player),ParkourPhysics.frame(player,true));
        var state=new MovementStatePayload(player.getUUID(),controller.snapshot(10),false,Vec3.ZERO);
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try {
            MovementStatePayload.STREAM_CODEC.encode(buffer,state);
            helper.assertTrue(state.equals(MovementStatePayload.STREAM_CODEC.decode(buffer)),"Wall normal, spent budget and mode must survive the network codec");
        } finally {buffer.release();}
        var intent=new MovementInputPayload(input,JumpKind.NONE,0,0,false,0,ParkourMode.WALL_LATCH);
        buffer=new FriendlyByteBuf(Unpooled.buffer());
        try {
            MovementInputPayload.STREAM_CODEC.encode(buffer,intent);
            var copy=MovementInputPayload.STREAM_CODEC.decode(buffer);
            helper.assertTrue(copy.equals(intent)&&copy.input().aim(),"Use intent must remain a movement observation, independent of item-use cancellation");
        } finally {buffer.release();}
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void rollingAndGlidingReduceFallDamageByExactlySeventyFivePercent(GameTestHelper helper) {
        for(boolean roll:new boolean[]{false,true}) {
            var player=air(helper);
            var input=new MovementInput(0,false,false,0,0,roll,false,0,1,!roll);
            MovementPhysics.controller(player).accept(input,true,false,true,true,0,NAttributes.read(player),ParkourFrame.EMPTY);
            player.hurt(player.damageSources().fall(),8);
            helper.assertTrue(Math.abs(player.getHealth()-18)<1e-6,"Roll or glide must reduce eight fall-damage points to two");
        }
        var ordinary=air(helper);ordinary.hurt(ordinary.damageSources().fall(),8);
        helper.assertTrue(ordinary.getHealth()==12,"Ordinary falling must retain normal damage");
        helper.succeed();
    }
}
