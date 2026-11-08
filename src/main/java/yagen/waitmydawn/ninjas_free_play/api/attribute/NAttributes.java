package yagen.waitmydawn.ninjas_free_play.api.attribute;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import yagen.waitmydawn.ninjas_free_play.NinjasFreePlay;
import yagen.waitmydawn.ninjas_free_play.core.MovementAttribute;
import yagen.waitmydawn.ninjas_free_play.core.MovementValues;

import java.util.EnumMap;
import java.util.Map;

public final class NAttributes {
    public static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(Registries.ATTRIBUTE, NinjasFreePlay.MODID);
    private static final Map<MovementAttribute, DeferredHolder<Attribute, Attribute>> HOLDERS = new EnumMap<>(MovementAttribute.class);

    static {
        for (MovementAttribute definition : MovementAttribute.values()) {
            HOLDERS.put(definition, ATTRIBUTES.register(definition.id(), () -> new RangedAttribute(
                    "attribute." + NinjasFreePlay.MODID + "." + definition.id(), definition.defaultValue(),
                    definition.minimum(), definition.maximum()).setSyncable(true)));
        }
    }

    public static final DeferredHolder<Attribute, Attribute> BULLET_JUMP_STRENGTH = holder(MovementAttribute.BULLET_JUMP_STRENGTH);
    public static final DeferredHolder<Attribute, Attribute> BULLET_JUMP_LIFT = holder(MovementAttribute.BULLET_JUMP_LIFT);
    public static final DeferredHolder<Attribute, Attribute> CROUCH_HEIGHT_RATIO = holder(MovementAttribute.CROUCH_HEIGHT_RATIO);
    public static final DeferredHolder<Attribute, Attribute> CROUCH_SPEED_MULTIPLIER = holder(MovementAttribute.CROUCH_SPEED_MULTIPLIER);
    public static final DeferredHolder<Attribute, Attribute> ROLL_HEIGHT_RATIO = holder(MovementAttribute.ROLL_HEIGHT_RATIO);
    public static final DeferredHolder<Attribute, Attribute> ROLL_STRENGTH = holder(MovementAttribute.ROLL_STRENGTH);
    public static final DeferredHolder<Attribute, Attribute> ROLL_DURATION = holder(MovementAttribute.ROLL_DURATION);
    public static final DeferredHolder<Attribute, Attribute> ROLL_COOLDOWN = holder(MovementAttribute.ROLL_COOLDOWN);
    public static final DeferredHolder<Attribute, Attribute> AIR_JUMP_STRENGTH = holder(MovementAttribute.AIR_JUMP_STRENGTH);
    public static final DeferredHolder<Attribute, Attribute> JUMP_COUNT = holder(MovementAttribute.JUMP_COUNT);
    public static final DeferredHolder<Attribute, Attribute> AIR_MANEUVER_DURATION = holder(MovementAttribute.AIR_MANEUVER_DURATION);
    public static final DeferredHolder<Attribute, Attribute> SLIDE_SPEED_MULTIPLIER = holder(MovementAttribute.SLIDE_SPEED_MULTIPLIER);
    public static final DeferredHolder<Attribute, Attribute> SLIDE_FRICTION = holder(MovementAttribute.SLIDE_FRICTION);
    public static final DeferredHolder<Attribute, Attribute> GLIDE_GRAVITY_SCALE = holder(MovementAttribute.GLIDE_GRAVITY_SCALE);
    public static final DeferredHolder<Attribute, Attribute> GLIDE_ASCENT_GRAVITY_SCALE = holder(MovementAttribute.GLIDE_ASCENT_GRAVITY_SCALE);
    public static final DeferredHolder<Attribute, Attribute> GLIDE_CONTROL_MULTIPLIER = holder(MovementAttribute.GLIDE_CONTROL_MULTIPLIER);
    public static final DeferredHolder<Attribute, Attribute> WALL_DASH_STRENGTH = holder(MovementAttribute.WALL_DASH_STRENGTH);
    public static final DeferredHolder<Attribute, Attribute> WALL_DASH_INTERVAL = holder(MovementAttribute.WALL_DASH_INTERVAL);
    public static final DeferredHolder<Attribute, Attribute> FLYING_KICK_DAMAGE = holder(MovementAttribute.FLYING_KICK_DAMAGE);
    public static final DeferredHolder<Attribute, Attribute> FLYING_KICK_KNOCKBACK_MULTIPLIER = holder(MovementAttribute.FLYING_KICK_KNOCKBACK_MULTIPLIER);

    private NAttributes() {
    }

    public static DeferredHolder<Attribute, Attribute> holder(MovementAttribute definition) {
        return HOLDERS.get(definition);
    }

    public static MovementValues read(Player player) {
        return new MovementValues(definition -> player.getAttributeValue(holder(definition)));
    }

    public static void register(IEventBus bus) {
        ATTRIBUTES.register(bus);
        bus.addListener(NAttributes::addPlayerAttributes);
    }

    private static void addPlayerAttributes(EntityAttributeModificationEvent event) {
        HOLDERS.values().forEach(attribute -> event.add(EntityType.PLAYER, attribute));
    }
}
