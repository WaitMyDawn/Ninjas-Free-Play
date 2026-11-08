package yagen.waitmydawn.ninjas_free_play.player;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import yagen.waitmydawn.ninjas_free_play.NinjasFreePlay;
import yagen.waitmydawn.ninjas_free_play.api.attribute.NAttributes;
import yagen.waitmydawn.ninjas_free_play.config.ServerConfigs;
import yagen.waitmydawn.ninjas_free_play.core.MovementAttribute;

@EventBusSubscriber(modid = NinjasFreePlay.MODID)
public final class MovementAttributeEvents {
    private static final String DEFAULTS_KEY = NinjasFreePlay.MODID + ":attribute_defaults";

    private MovementAttributeEvents() {
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && ServerConfigs.SPEC.isLoaded()) {
            ServerConfigs.migrateDefaults();
            applyDefaults(player);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        for (MovementAttribute definition : MovementAttribute.values()) {
            AttributeInstance old = event.getOriginal().getAttribute(NAttributes.holder(definition));
            AttributeInstance next = player.getAttribute(NAttributes.holder(definition));
            // Persist base overrides and permanent modifiers, excluding temporary equipment/effect modifiers.
            if (old != null && next != null) next.load(old.save());
        }
        if (event.getOriginal().getPersistentData().contains(DEFAULTS_KEY, Tag.TAG_COMPOUND)) {
            player.getPersistentData().put(DEFAULTS_KEY, event.getOriginal().getPersistentData().getCompound(DEFAULTS_KEY).copy());
        }
    }

    public static void onConfigLoading(ModConfigEvent.Loading event) {
        refresh(event);
    }

    public static void onConfigReloading(ModConfigEvent.Reloading event) {
        refresh(event);
    }

    private static void refresh(ModConfigEvent event) {
        if (event.getConfig().getSpec() != ServerConfigs.SPEC) return;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) server.execute(() -> {
            if (ServerConfigs.SPEC.isLoaded()) {
                ServerConfigs.migrateDefaults();
                server.getPlayerList().getPlayers().forEach(MovementAttributeEvents::applyDefaults);
            }
        });
    }

    private static void applyDefaults(ServerPlayer player) {
        CompoundTag persistent = player.getPersistentData();
        CompoundTag previous = persistent.getCompound(DEFAULTS_KEY);
        for (MovementAttribute definition : MovementAttribute.values()) {
            AttributeInstance instance = player.getAttribute(NAttributes.holder(definition));
            if (instance == null)
                throw new IllegalStateException("Missing player movement attribute: " + definition.id());
            double oldDefault = previous.contains(definition.id(), Tag.TAG_ANY_NUMERIC)
                    ? previous.getDouble(definition.id()) : definition.defaultValue();
            double configured = ServerConfigs.defaultBase(definition);
            double base = definition.configuredBase(instance.getBaseValue(), oldDefault, configured);
            if (Double.compare(base, instance.getBaseValue()) != 0) instance.setBaseValue(base);
            previous.putDouble(definition.id(), configured);
        }
        persistent.put(DEFAULTS_KEY, previous);
    }
}
