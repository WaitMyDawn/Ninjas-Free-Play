package yagen.waitmydawn.ninjas_free_play.config;

import net.minecraftforge.common.ForgeConfigSpec;
import yagen.waitmydawn.ninjas_free_play.core.MovementAttribute;

import java.util.EnumMap;
import java.util.Map;

public class ServerConfigs {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.IntValue DEFAULT_SCHEMA;
    private static final Map<MovementAttribute, ForgeConfigSpec.ConfigValue<? extends Number>> DEFAULTS = new EnumMap<>(MovementAttribute.class);

    static {
        BUILDER.push("MovementAttributeDefaults");
        DEFAULT_SCHEMA = BUILDER.comment("Default-value migration schema; only unchanged legacy defaults are migrated.")
                .defineInRange("default_schema", 0, 0, 6);
        for (MovementAttribute definition : MovementAttribute.values()) {
            BUILDER.comment("Default player attribute base; unit: " + definition.unit() + ".");
            DEFAULTS.put(definition, definition.integralDefault()
                    ? BUILDER.defineInRange(definition.id(), (int) definition.defaultValue(), (int) definition.minimum(), (int) definition.maximum())
                    : BUILDER.defineInRange(definition.id(), definition.defaultValue(), definition.minimum(), definition.maximum()));
        }
        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    public static double defaultBase(MovementAttribute definition) {
        return DEFAULTS.get(definition).get().doubleValue();
    }

    public static void migrateDefaults() {
        int schema = DEFAULT_SCHEMA.get();
        if (schema >= 6) return;
        for (MovementAttribute definition : MovementAttribute.values()) {
            var value = DEFAULTS.get(definition);
            double before = value.get().doubleValue(), after = definition.migrateConfiguredDefault(before, schema);
            if (Double.compare(before, after) != 0 && value instanceof ForgeConfigSpec.DoubleValue number)
                number.set(after);
        }
        DEFAULT_SCHEMA.set(6);
        SPEC.save();
    }
}
