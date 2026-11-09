package yagen.waitmydawn.ninjas_free_play;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import yagen.waitmydawn.ninjas_free_play.api.attribute.NAttributes;
import yagen.waitmydawn.ninjas_free_play.api.movement.ContactDamageContext;
import yagen.waitmydawn.ninjas_free_play.core.*;
import yagen.waitmydawn.ninjas_free_play.player.*;
import yagen.waitmydawn.ninjas_free_play.network.MovementStatePayload;
import yagen.waitmydawn.ninjas_free_play.network.MovementInputPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

public final class ParkourGameTests {
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
    @GameTest
    public void slideKeepsNativeDamageAndKnockbackThroughAirborneTravel(GameTestHelper helper) {
        var player=air(helper);
        for(int x=0;x<=5;x++) for(int z=0;z<=15;z++) helper.setBlock(x,11,z,Blocks.STONE);
        player.setOnGround(true);player.setDeltaMovement(0,-.08,.5);
        ParkourPhysics.runtime(player).displacement=new Vec3(0,0,.5);
        var controller=MovementPhysics.controller(player);var values=NAttributes.read(player);
        var frame=ParkourPhysics.frame(player,false);
        controller.accept(input(0,true,false,false),true,true,true,true,0,values,frame);
        ParkourPhysics.applySlide(player,frame,values);MovementPhysics.applyCrouch(player);
        long instance=controller.snapshot(0).parkour().instance();
        player.hurt(player.damageSources().fall(),4);
        helper.assertTrue(player.getHealth()==16,"Sliding must retain ordinary fall damage");
        player.knockback(.4,-1,0,player.damageSources().generic(),0);
        helper.assertTrue(player.getDeltaMovement().x>0 && player.getDeltaMovement().y>0,
                "Native knockback must still deflect and lift the sliding player");
        double initialY=player.getY();
        boolean airborne=false;
        for(int tick=1;tick<=5;tick++) {
            player.tickCount=tick;player.aiStep();
            airborne |= !player.onGround();
            helper.assertTrue(controller.parkour().mode()==ParkourMode.SLIDE
                    && controller.snapshot(tick).parkour().instance()==instance,
                    "Damage and a real knockback lift must preserve the same slide instance");
        }
        helper.assertTrue(airborne && player.getY()>initialY,"Knockback must affect the actual travel trajectory");
        controller.accept(input(1,false,false,false),true,MovementPhysics.grounded(player),true,true,6,values,ParkourPhysics.frame(player,false));
        helper.assertTrue(controller.parkour().mode()==ParkourMode.NONE,"Releasing C must still stop an airborne slide");
        helper.succeed();
    }
    @GameTest
    public void expiredKickRetainsSynchronizedLandingIntentAndBoostsOnlyOnce(GameTestHelper helper) {
        var player=air(helper);kick(player);
        var controller=MovementPhysics.controller(player);var values=NAttributes.read(player);
        player.tickCount=25;ParkourPhysics.update(player);
        helper.assertTrue(controller.parkour().mode()==ParkourMode.NONE && controller.snapshot(25).parkour().landingSlide(),
                "The kick clip may finish while its held-C landing intent remains active");
        var payload=new MovementStatePayload(player.getUUID(),controller.snapshot(25),false,player.getDeltaMovement());
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try {
            MovementStatePayload.STREAM_CODEC.encode(buffer,payload);
            var decoded=MovementStatePayload.STREAM_CODEC.decode(buffer);
            helper.assertTrue(payload.equals(decoded),"Landing intent must round-trip even after the kick mode ends");
            controller.restore(decoded.state(),25);
        } finally {buffer.release();}
        helper.setBlock(2,11,2,Blocks.STONE);player.setOnGround(true);player.tickCount=26;
        ParkourPhysics.update(player);ParkourPhysics.update(player);
        helper.assertTrue(controller.parkour().mode()==ParkourMode.SLIDE && Math.abs(player.getDeltaMovement().z-.75)<1e-6,
                "A fast held-C landing must start one boosted slide after the kick clip has ended");
        controller.accept(input(1,false,false,false),true,true,true,true,27,values,ParkourPhysics.frame(player,false));
        helper.assertTrue(controller.parkour().mode()==ParkourMode.NONE && !controller.snapshot(27).parkour().landingSlide(),
                "Releasing C must clear the slide and its continuation intent");
        helper.succeed();
    }
    @GameTest
    public void ordinaryJumpImmediatelyGlidingRisesOneBlockThenDescends(GameTestHelper helper) {
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
    @GameTest
    public void latchStopsFallingAndWallRemovalContinuesTheSameBudget(GameTestHelper helper) {
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
    @GameTest
    public void wallDashRefreshesResourcesAndResetsFallDistance(GameTestHelper helper) {
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
    @GameTest
    public void walkingIntoSlideCarriesActualSpeedThroughTwentyFullPhysicsSteps(GameTestHelper helper) {
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
            double before=player.getZ();
            // A server-side mock has no controlling client. Exercise travel without its replica-only AI damping.
            player.travel(Vec3.ZERO);
            helper.assertTrue(controller.parkour().mode()==ParkourMode.SLIDE,"The slide must not fall below its threshold immediately after entry");
            helper.assertTrue(Math.abs(player.getZ()-before-speed*1.5*Math.pow(.97,step))<.001,
                    "Each full step must carry the one-shot 1.5 boost and configured friction: step="+step+" actual="+(player.getZ()-before));
        }
        helper.assertTrue(player.getZ()-start>4.8,"The real walking-to-slide path must retain useful travel distance");
        helper.succeed();
    }
    @GameTest
    public void kickAddsImpactVelocityWithNativeResistanceWithoutOverwritingCarriedMotion(GameTestHelper helper) {
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
    @GameTest
    public void contactGuardOnlyCoversTheActualTouchedTargetInsideTheCollisionCallback(GameTestHelper helper) throws Exception {
        var player=air(helper);kick(player);
        Slime slime=helper.spawn(EntityTypes.SLIME,new Vec3(2.5,12,2.8));slime.setSize(3,true);
        var touch=Player.class.getDeclaredMethod("touch",Entity.class);touch.setAccessible(true);
        touch.invoke(player,slime);
        helper.assertTrue(player.getHealth()==20&&ParkourPhysics.runtime(player).hitTargets.contains(slime.getUUID()),
                "The actual playerTouch callback must apply the kick before suppressing its contact damage");
        player.damageCooldownTime=0;slime.playerTouch(player);
        helper.assertTrue(player.getHealth()<20,"The same entity's melee damage outside the collision scope must remain effective");
        player.setHealth(20);player.damageCooldownTime=0;
        try(var scope=ContactDamageContext.collision(player,slime)) {
            player.hurt(player.damageSources().thorns(slime),2);
        }
        helper.assertTrue(player.getHealth()<20,"Thorns and other damage types must not become contact invulnerability");
        player.setHealth(20);player.damageCooldownTime=0;
        var other=helper.spawn(EntityTypes.ZOMBIE,new Vec3(3.5,12,2.5));
        try(var scope=ContactDamageContext.collision(player,slime)) {
            player.hurt(player.damageSources().mobAttack(other),2);
        }
        helper.assertTrue(player.getHealth()<20,"Another entity's attack must not be suppressed by the touched target's scope");
        player.setHealth(20);player.damageCooldownTime=0;
        var arrow=EntityTypes.ARROW.create(helper.getLevel(), EntitySpawnReason.MOB_SUMMONED);
        try(var scope=ContactDamageContext.collision(player,slime)) { player.hurt(player.damageSources().arrow(arrow,slime),2); }
        helper.assertTrue(player.getHealth()<20,"The touched entity's projectile must retain its independent damage source");
        helper.succeed();
    }
    @GameTest
    public void rollingAndGlidingReduceFallDamageByExactlySeventyFivePercent(GameTestHelper helper) {
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
