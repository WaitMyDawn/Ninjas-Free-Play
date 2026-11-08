package yagen.waitmydawn.ninjas_free_play;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import yagen.waitmydawn.ninjas_free_play.api.attribute.NAttributes;
import yagen.waitmydawn.ninjas_free_play.animation.resource.AnimationJsonCodec;
import yagen.waitmydawn.ninjas_free_play.core.MovementAttribute;
import yagen.waitmydawn.ninjas_free_play.config.ServerConfigs;

@GameTestHolder(NinjasFreePlay.MODID)
@PrefixGameTestTemplate(false)
public final class FoundationGameTests {
    @GameTest(template="empty")
    public static void everySharedAttributeIsRegisteredForForgePlayers(GameTestHelper helper) {
        var player=helper.makeMockPlayer();
        for(var definition:MovementAttribute.values()) {
            var instance=player.getAttribute(NAttributes.holder(definition).get());
            helper.assertTrue(instance!=null,"Missing shared attribute: "+definition.id());
            helper.assertTrue(Double.isFinite(NAttributes.read(player).get(definition)),"Non-finite attribute value");
            helper.assertTrue(Double.isFinite(ServerConfigs.defaultBase(definition)),"Missing server default");
        }
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void generatedAnimationResourcesAreAvailableToTheForgeMod(GameTestHelper helper) throws Exception {
        try(var stream=FoundationGameTests.class.getResourceAsStream("/assets/ninjas_free_play/ninja_animations/bullet_jump.json")) {
            helper.assertTrue(stream!=null,"The Forge mod must package shared generated animations");
            var clip=AnimationJsonCodec.read(new InputStreamReader(stream,StandardCharsets.UTF_8));
            helper.assertTrue(clip.duration()>0,"The Java17 animation codec must load a real generated clip");
        }
        helper.succeed();
    }
}
