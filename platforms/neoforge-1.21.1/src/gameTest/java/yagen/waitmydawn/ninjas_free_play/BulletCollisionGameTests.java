package yagen.waitmydawn.ninjas_free_play;

import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import yagen.waitmydawn.ninjas_free_play.api.attribute.NAttributes;
import yagen.waitmydawn.ninjas_free_play.core.*;
import yagen.waitmydawn.ninjas_free_play.network.MovementStatePayload;
import yagen.waitmydawn.ninjas_free_play.player.MovementPhysics;

@GameTestHolder(NinjasFreePlay.MODID)
@PrefixGameTestTemplate(false)
public final class BulletCollisionGameTests {
    private BulletCollisionGameTests() {}

    private static Player player(GameTestHelper helper) {
        helper.setBlock(2,1,2,Blocks.STONE);
        var player=helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(new Vec3(2.5,2,2.5)));
        player.setOnGround(true);
        return player;
    }

    private static Player bullet(GameTestHelper helper) {
        var player=player(helper);var session=MovementPhysics.controller(player);var values=NAttributes.read(player);
        session.accept(new MovementInput(0,true,false,0,0),true,true,MovementPhysics.canStand(player),0,values);
        MovementPhysics.applyCrouch(player);
        player.tickCount=1;
        var input=new MovementInput(1,true,true,0,0);
        var decision=session.accept(input,true,true,MovementPhysics.canStand(player),1,values);
        helper.assertTrue(decision.jump()==JumpKind.BULLET,"The collision fixture must start a real accepted bullet jump");
        MovementPhysics.applyCrouch(player);MovementPhysics.applyJump(player,decision.jump(),input,values);
        player.tickCount=2;
        session.accept(new MovementInput(2,false,false,0,0),true,false,MovementPhysics.canStand(player),2,values);
        MovementPhysics.applyCrouch(player);
        return player;
    }

    @GameTest(template="empty")
    public static void bulletJumpPassesAOneBlockTunnelAfterCrouchRelease(GameTestHelper helper) {
        for(int z=2;z<=8;z++) helper.setBlock(2,1,z,Blocks.STONE);
        for(int z=2;z<=5;z++) {
            helper.setBlock(2,3,z,Blocks.STONE);
            helper.setBlock(1,2,z,Blocks.STONE);helper.setBlock(3,2,z,Blocks.STONE);
        }
        var player=bullet(helper);double start=player.getZ();
        for(int step=0;step<5;step++) {
            player.tickCount=3+step;
            MovementPhysics.observe(player);MovementPhysics.applyCrouch(player);
            player.move(MoverType.SELF,player.getDeltaMovement());
            helper.assertTrue(Math.abs(player.getBbHeight()-.9)<1e-5&&Math.abs(player.getBbWidth()-.6)<1e-5,
                    "Bullet collision must retain the slide's default width and half height after releasing C");
            helper.assertTrue(player.level().noCollision(player,player.getBoundingBox().deflate(1e-6)),
                    "Default upward lift must resolve against the ceiling without block penetration");
        }
        helper.assertTrue(player.getZ()-start>4,"Real collision movement must carry the player through the complete one-block opening");
        helper.assertTrue(MovementPhysics.controller(player).bulletJumping(),"Leaving the tunnel must not depend on holding crouch");
        helper.succeed();
    }

    @GameTest(template="empty")
    public static void bulletAndSlideShareConfiguredCollisionAtAllPitchesAndScales(GameTestHelper helper) {
        for(double ratio:new double[]{.4,.5,.75}) for(double scale:new double[]{1,1.25}) for(float pitch:new float[]{-90,0,45}) {
            var player=player(helper);
            player.getAttribute(NAttributes.CROUCH_HEIGHT_RATIO).setBaseValue(ratio);
            player.getAttribute(Attributes.SCALE).setBaseValue(scale);player.refreshDimensions();
            var session=MovementPhysics.controller(player);var values=NAttributes.read(player);
            var frame=new ParkourFrame(0,.3,0,0,.3,WallContact.NONE);
            var slide=new MovementInput(0,true,false,123,pitch,false,false,0,1,false);
            helper.assertTrue(session.accept(slide,true,true,true,true,0,values,frame).slideStarted(),"The size reference must be an actual slide");
            MovementPhysics.applyCrouch(player);double height=player.getBbHeight(),width=player.getBbWidth();
            var input=new MovementInput(1,true,true,123,pitch);
            player.tickCount=1;
            var decision=session.accept(input,true,true,true,1,values);
            helper.assertTrue(decision.jump()==JumpKind.BULLET,"Slide+jump must enter bullet jump");
            MovementPhysics.applyCrouch(player);MovementPhysics.applyJump(player,decision.jump(),input,values);
            var standing=player.getDimensions(Pose.STANDING);
            helper.assertTrue(Math.abs(player.getBbHeight()-height)<1e-5&&Math.abs(player.getBbWidth()-width)<1e-5,
                    "Bullet and slide must use the same configured collision independently of visual pitch: ratio="+ratio+" scale="+scale+" pitch="+pitch
                            +" slide="+width+"x"+height+" bullet="+player.getBbWidth()+"x"+player.getBbHeight());
            helper.assertTrue(Math.abs(height-standing.height()*ratio)<1e-5&&Math.abs(width-standing.width())<1e-5,
                    "Configured compact height must scale with standing size without narrowing the player");
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template="empty")
    public static void interruptedBulletKeepsLowCeilingSafetyForNewObserversThenStandsWhenClear(GameTestHelper helper) {
        helper.setBlock(2,3,2,Blocks.STONE);var player=bullet(helper);var session=MovementPhysics.controller(player);
        var input=new MovementInput(3,false,true,0,0);
        player.tickCount=3;
        var decision=session.accept(input,true,false,false,3,NAttributes.read(player));
        helper.assertTrue(decision.jump()==JumpKind.AIR&&!session.bulletJumping(),"An air jump must end the bullet action while consuming its normal resource");
        MovementPhysics.applyCrouch(player);MovementPhysics.applyJump(player,decision.jump(),input,NAttributes.read(player));
        helper.assertTrue(Math.abs(player.getBbHeight()-.9)<1e-5&&session.snapshot(3).posture().blockedLow(),
                "Ending bullet collision must retain explicit low-ceiling state instead of standing into blocks");
        var payload=new MovementStatePayload(player.getUUID(),session.snapshot(3),false,player.getDeltaMovement());
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        helper.setBlock(5,1,2,Blocks.STONE);helper.setBlock(5,3,2,Blocks.STONE);
        var observer=helper.makeMockPlayer(GameType.SURVIVAL);
        observer.setPos(helper.absoluteVec(new Vec3(5.5,2,2.5)));observer.setOnGround(false);
        try {
            MovementStatePayload.STREAM_CODEC.encode(buffer,payload);
            MovementPhysics.controller(observer).restore(MovementStatePayload.STREAM_CODEC.decode(buffer).state(),100);
        } finally {buffer.release();}
        observer.tickCount=100;MovementPhysics.applyCrouch(observer);
        helper.assertTrue(Math.abs(observer.getBbHeight()-.9)<1e-5,"A fresh observer must reconstruct low collision from authoritative state");
        helper.setBlock(2,3,2,Blocks.AIR);
        helper.setBlock(5,3,2,Blocks.AIR);
        MovementPhysics.observe(player);MovementPhysics.applyCrouch(player);
        MovementPhysics.observe(observer);MovementPhysics.applyCrouch(observer);
        helper.assertTrue(Math.abs(player.getBbHeight()-1.8)<1e-5&&Math.abs(observer.getBbHeight()-1.8)<1e-5,
                "Both original and restored players must safely regain standing dimensions when headroom returns: original="+player.getBbHeight()
                        +" observer="+observer.getBbHeight()+" headroom="+MovementPhysics.canStand(player)+"/"+MovementPhysics.canStand(observer));
        helper.assertTrue(session.resources().jumpsUsed()==2&&session.resources().bulletJumpUsed(),"Changing collision must not refund jump resources");
        helper.succeed();
    }

    @GameTest(template="empty")
    public static void landingSideRollAndGlideEndBulletCollisionWithoutRevivingIt(GameTestHelper helper) {
        for(int action=0;action<3;action++) {
            var player=bullet(helper);var session=MovementPhysics.controller(player);var values=NAttributes.read(player);
            player.tick();
            helper.assertTrue(player.getPose()==Pose.CROUCHING&&Math.abs(player.getBbHeight()-.9)<1e-5,
                    "Vanilla pose updates must preserve bullet collision after the crouch key is released");
            if(action==0) session.observe(true,true,true,3);
            else {
                var input=new MovementInput(3,false,false,0,0,action==1,false,1,0,action==2);
                var decision=session.accept(input,true,false,true,true,3,values,ParkourFrame.EMPTY);
                if(action==1) helper.assertTrue(decision.rollStarted(),"The side-roll replacement must be accepted");
            }
            MovementPhysics.applyCrouch(player);
            helper.assertTrue(!session.bulletJumping()&&Math.abs(player.getBbHeight()-1.8)<1e-5,
                    "Landing, side roll and glide must restore ordinary collision when it fits");
            player.tickCount=30;
            session.accept(new MovementInput(4,false,false,0,0),true,false,true,true,30,values,ParkourFrame.EMPTY);
            MovementPhysics.applyCrouch(player);
            helper.assertTrue(!MovementPhysics.low(player)&&Math.abs(player.getBbHeight()-1.8)<1e-5,
                    "An expired replacement must not resurrect an old bullet collision request");
        }
        helper.succeed();
    }
}
