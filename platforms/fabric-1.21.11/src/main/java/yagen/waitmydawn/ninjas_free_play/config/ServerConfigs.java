package yagen.waitmydawn.ninjas_free_play.config;

import java.nio.file.Path;
import yagen.waitmydawn.ninjas_free_play.core.MovementAttribute;

import java.util.EnumMap;
import java.util.Map;

public class ServerConfigs {
    private static final FabricConfig BUILDER = new FabricConfig();
    private static final FabricConfig SPEC;
    private static final FabricConfig.Value<Integer> DEFAULT_SCHEMA;
    private static final Map<MovementAttribute, FabricConfig.Value<Double>> DEFAULTS = new EnumMap<>(MovementAttribute.class);

    static {
        BUILDER.push("MovementAttributeDefaults");
        DEFAULT_SCHEMA = BUILDER.comment("Default-value migration schema; only unchanged legacy defaults are migrated.")
                .defineInRange("default_schema", 0, 0, 6);
        for (MovementAttribute definition : MovementAttribute.values()) {
            BUILDER.comment("Default player attribute base; unit: " + definition.unit() + ".");
            DEFAULTS.put(definition, BUILDER.defineNumber(definition.id(), definition.defaultValue(), definition.minimum(), definition.maximum(), definition.integralDefault()));
        }
        BUILDER.pop();
        SPEC = BUILDER;
    }

    public static void load(Path path) {
        SPEC.load(path);
        migrateDefaults();
        SPEC.save();
    }

    public static double defaultBase(MovementAttribute definition) {
        return DEFAULTS.get(definition).get().doubleValue();
    }

    private static void migrateDefaults() {
        int schema = DEFAULT_SCHEMA.get();
        if (schema >= 6) return;
        for (MovementAttribute definition : MovementAttribute.values()) {
            var value = DEFAULTS.get(definition);
            double before = value.get().doubleValue(), after = definition.migrateConfiguredDefault(before, schema);
            if (Double.compare(before, after) != 0) DEFAULTS.get(definition).set(after);
        }
        DEFAULT_SCHEMA.set(6);
    }
}
