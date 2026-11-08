package yagen.waitmydawn.ninjas_free_play;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import yagen.waitmydawn.ninjas_free_play.api.attribute.NAttributes;
import yagen.waitmydawn.ninjas_free_play.api.movement.*;
import yagen.waitmydawn.ninjas_free_play.core.MovementInput;
import yagen.waitmydawn.ninjas_free_play.player.MovementActionHooks;
import yagen.waitmydawn.ninjas_free_play.player.MovementPhysics;
import java.util.ArrayList;
import java.util.UUID;
import java.util.function.Consumer;

@GameTestHolder(NinjasFreePlay.MODID)
@PrefixGameTestTemplate(false)
public final class MovementApiGameTests {
    private MovementApiGameTests() {}

    @GameTest(template = "empty")
    public static void acceptedServerActionsPublishExactlyOneLifecycle(GameTestHelper helper) {
        var player=new ServerPlayer(helper.getLevel().getServer(),helper.getLevel(),
                new GameProfile(UUID.randomUUID(),"movement-api-test"),ClientInformation.createDefault());
        player.setPos(helper.absoluteVec(new Vec3(2.5,2,2.5)));
        var events=new ArrayList<ActionState>();
        int[] published={0};
        Consumer<MovementActionEvent> listener=event -> {
            published[0]++;
            if (event.getEntity()==player) events.add(event.getState());
        };
        NeoForge.EVENT_BUS.addListener(MovementActionEvent.class,listener);
        try {
            var input=new MovementInput(0,false,false,0,0,true,true,0,1);
            var controller=MovementPhysics.controller(player);
            controller.accept(input,true,true,true,true,0,NAttributes.read(player));
            MovementActionHooks.reconcile(player);
            MovementActionHooks.reconcile(player);
            helper.assertTrue(MovementApi.state(player,MovementAction.ROLL).startedThisTick(),"The accepted roll must be queryable in START phase");
            controller.accept(input,true,true,true,true,0,NAttributes.read(player));
            MovementActionHooks.reconcile(player);
            player.tickCount=1;
            MovementActionHooks.tick(player);
            MovementActionHooks.tick(player);
            controller.posture().stopRoll(2);
            player.tickCount=2;
            MovementActionHooks.reconcile(player);
            MovementActionHooks.reconcile(player);
            helper.assertTrue(events.size()==3 && events.get(0).phase()==ActionPhase.START
                    && events.get(1).phase()==ActionPhase.ACTIVE && events.get(2).phase()==ActionPhase.END,
                    "A consumer must receive exactly one START, one ACTIVE per tick, and one END");
            helper.assertTrue(events.get(0).instanceId()==events.get(2).instanceId(),"The same action instance must survive phase changes");
            helper.assertTrue(MovementApi.state(player,MovementAction.ROLL).endedThisTick()
                    && !MovementApi.isActive(player,MovementAction.ROLL),"A completed observation must not remain active");
            var local=helper.makeMockPlayer(GameType.SURVIVAL);
            int beforePrediction=published[0];
            MovementPhysics.controller(local).accept(input,true,true,true,true,0,NAttributes.read(local));
            MovementActionHooks.reconcile(local);
            helper.assertTrue(published[0]==beforePrediction,"Predicted/non-server players must not publish gameplay effects");
            helper.succeed();
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
    }
}
