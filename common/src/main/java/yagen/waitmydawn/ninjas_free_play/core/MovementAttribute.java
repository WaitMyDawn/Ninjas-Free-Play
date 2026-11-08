package yagen.waitmydawn.ninjas_free_play.core;

/**
 * Attribute units and bounds are shared by registration, configuration, and movement consumers.
 */
public enum MovementAttribute {
    BULLET_JUMP_STRENGTH("bullet_jump_strength", 18, 0, 100, "blocks/second"),
    BULLET_JUMP_LIFT("bullet_jump_lift", 8.4, 0, 100, "additional upward blocks/second at bullet-jump activation; zero disables"),
    CROUCH_HEIGHT_RATIO("crouch_height_ratio", 0.5, 0.25, 1, "fraction of standing height"),
    CROUCH_SPEED_MULTIPLIER("crouch_speed_multiplier", 0.55, 0.05, 1, "fraction of ordinary movement input"),
    ROLL_HEIGHT_RATIO("roll_height_ratio", 0.5, 0.25, 1, "fraction of standing height during a forward roll; side and backward flips retain standing collision height"),
    ROLL_STRENGTH("roll_strength", 12, 0, 100, "target horizontal blocks/second during rolling"),
    ROLL_DURATION("roll_duration", 0.6, 0.2, 2, "seconds per roll"),
    ROLL_COOLDOWN("roll_cooldown", 0.8, 0.2, 5, "seconds between roll activations"),
    AIR_JUMP_STRENGTH("air_jump_strength", 8.4, 0, 100, "blocks/second"),
    JUMP_COUNT("jump_count", 2, 1, 64, "total jumps per airborne sequence; fractional attribute values round down", true),
    AIR_MANEUVER_DURATION("air_maneuver_duration", 4, 0, 120, "seconds shared by aim glide and wall latch"),
    SLIDE_FRICTION("slide_friction", 0.03, 0, 1, "fraction of horizontal speed lost per simulation tick"),
    SLIDE_SPEED_MULTIPLIER("slide_speed_multiplier", 1.5, 0, 8, "horizontal entry multiplier capped by the continuous movement reference; one preserves speed"),
    GLIDE_GRAVITY_SCALE("glide_gravity_scale", 0.2, 0, 4, "descending aim-glide gravity multiplier"),
    GLIDE_ASCENT_GRAVITY_SCALE("glide_ascent_gravity_scale", 1.3578712276749088, 0, 4, "ascending aim-glide gravity multiplier; ordinary default jump rises approximately one block"),
    GLIDE_CONTROL_MULTIPLIER("glide_control_multiplier", 0.35, 0, 1, "fraction of ordinary air-control input; carried horizontal speed is retained"),
    WALL_DASH_STRENGTH("wall_dash_strength", 14, 0, 100, "blocks/second"),
    WALL_DASH_INTERVAL("wall_dash_interval", 0.35, 0.15, 2, "seconds between accepted wall jumps"),
    FLYING_KICK_DAMAGE("flying_kick_damage", 2, 0, 1000, "damage per target per flying kick"),
    FLYING_KICK_KNOCKBACK_MULTIPLIER("flying_kick_knockback_multiplier", 1.0, 0, 8, "fraction of the attacker's impact velocity added to the target before knockback resistance");

    private final String id;
    private final double defaultValue;
    private final double minimum;
    private final double maximum;
    private final String unit;
    private final boolean integralDefault;

    MovementAttribute(String id, double defaultValue, double minimum, double maximum, String unit) {
        this(id, defaultValue, minimum, maximum, unit, false);
    }

    MovementAttribute(String id, double defaultValue, double minimum, double maximum, String unit, boolean integralDefault) {
        this.id = id;
        this.defaultValue = defaultValue;
        this.minimum = minimum;
        this.maximum = maximum;
        this.unit = unit;
        this.integralDefault = integralDefault;
    }

    public String id() {
        return id;
    }

    public double defaultValue() {
        return defaultValue;
    }

    public double minimum() {
        return minimum;
    }

    public double maximum() {
        return maximum;
    }

    public String unit() {
        return unit;
    }

    public boolean integralDefault() {
        return integralDefault;
    }

    public double migrateConfiguredDefault(double current, int schema) {
        if (schema < 6 && this == FLYING_KICK_KNOCKBACK_MULTIPLIER && Double.compare(current, .5) == 0)
            return defaultValue;
        if (schema == 3 && this == SLIDE_FRICTION && Double.compare(current, .015) == 0) return defaultValue;
        if (schema >= 3) return current;
        if (this == SLIDE_FRICTION && (Double.compare(current, .01) == 0 || schema < 2 && Double.compare(current, .04) == 0))
            return defaultValue;
        if (schema >= 2) return current;
        if (schema == 0 && this == AIR_MANEUVER_DURATION && Double.compare(current, 3) == 0) return defaultValue;
        double legacy = switch (this) {
            case SLIDE_FRICTION -> 0.04;
            case GLIDE_GRAVITY_SCALE -> 0.020765292747931523;
            default -> Double.NaN;
        };
        return Double.isFinite(legacy) && Double.compare(current, legacy) == 0 ? defaultValue : current;
    }

    public double effective(double value) {
        return Double.isFinite(value) ? Math.max(minimum, Math.min(maximum, value)) : defaultValue;
    }

    public double configuredBase(double currentBase, double previousDefault, double configuredDefault) {
        if (!Double.isFinite(configuredDefault) || configuredDefault < minimum || configuredDefault > maximum) {
            throw new IllegalArgumentException("Invalid configured base for " + id);
        }
        // Explicit per-player base overrides take precedence over subsequent changes to the server default.
        return Double.compare(currentBase, previousDefault) == 0 ? configuredDefault : currentBase;
    }
}
