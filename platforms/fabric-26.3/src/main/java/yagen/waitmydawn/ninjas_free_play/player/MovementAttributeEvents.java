package yagen.waitmydawn.ninjas_free_play.player;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.level.storage.LevelResource;
import yagen.waitmydawn.ninjas_free_play.api.attribute.NAttributes;
import yagen.waitmydawn.ninjas_free_play.config.ServerConfigs;
import yagen.waitmydawn.ninjas_free_play.core.MovementAttribute;

import java.nio.file.Files;

public final class MovementAttributeEvents {
    private static final String SERVER_CONFIG = "ninjas_free_play/ninjas_free_play-server.toml";

    private MovementAttributeEvents() {}

    public static void reload(MinecraftServer server) {
        var worldConfig = server.getWorldPath(LevelResource.ROOT).resolve("serverconfig").resolve(SERVER_CONFIG);
        var globalConfig = FabricLoader.getInstance().getConfigDir().resolve(SERVER_CONFIG);
        ServerConfigs.load(Files.isRegularFile(worldConfig) ? worldConfig : globalConfig);
        server.getPlayerList().getPlayers().forEach(MovementAttributeEvents::applyDefaults);
    }

    public static void copy(ServerPlayer oldPlayer, ServerPlayer newPlayer) {
        for (MovementAttribute definition : MovementAttribute.values()) {
            AttributeInstance old = oldPlayer.getAttribute(NAttributes.holder(definition));
            AttributeInstance next = newPlayer.getAttribute(NAttributes.holder(definition));
            // Preserve permanent modifiers without copying equipment and effect modifiers.
            if (old != null && next != null) next.apply(old.pack());
        }
        ((MovementAccess) newPlayer).ninjas$setAttributeDefaults(((MovementAccess) oldPlayer).ninjas$attributeDefaults().copy());
    }

    public static void applyDefaults(ServerPlayer player) {
        CompoundTag previous = ((MovementAccess) player).ninjas$attributeDefaults();
        for (MovementAttribute definition : MovementAttribute.values()) {
            AttributeInstance instance = player.getAttribute(NAttributes.holder(definition));
            if (instance == null)
                throw new IllegalStateException("Missing player movement attribute: " + definition.id());
            double oldDefault = previous.getDoubleOr(definition.id(), definition.defaultValue());
            double configured = ServerConfigs.defaultBase(definition);
            double base = definition.configuredBase(instance.getBaseValue(), oldDefault, configured);
            if (Double.compare(base, instance.getBaseValue()) != 0) instance.setBaseValue(base);
            previous.putDouble(definition.id(), configured);
        }
    }
}
