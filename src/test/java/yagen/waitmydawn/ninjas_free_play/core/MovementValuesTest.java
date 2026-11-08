package yagen.waitmydawn.ninjas_free_play.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MovementValuesTest {
    @Test void legacyKickMultiplierMigratesWithoutOverwritingCustomDefaultsOrPlayerBases() {
        var attribute=MovementAttribute.FLYING_KICK_KNOCKBACK_MULTIPLIER;
        assertEquals(1,attribute.defaultValue());
        for(int schema=0;schema<6;schema++) {
            assertEquals(1,attribute.migrateConfiguredDefault(.5,schema));
            for(double custom:new double[]{0,.25,.75,1,2}) assertEquals(custom,attribute.migrateConfiguredDefault(custom,schema));
        }
        assertEquals(.5,attribute.migrateConfiguredDefault(.5,6));
        assertEquals(1,attribute.configuredBase(.5,.5,1));
        assertEquals(.75,attribute.configuredBase(.75,.5,1));
    }
    private static MovementValues defaults() { return new MovementValues(MovementAttribute::defaultValue); }

    @Test
    void unitsAndIntegerCountHaveExplicitConversions() {
        assertEquals(0.21585, MovementValues.SLIDE_ENTER_BLOCKS_PER_TICK, 1e-12);
        assertEquals(0.9, defaults().bulletJumpPerTick(), 1e-12);
        assertEquals(0.42, defaults().airJumpPerTick(), 1e-12);
        var fractional = new MovementValues(a -> a == MovementAttribute.JUMP_COUNT ? 3.9 : a.defaultValue());
        assertEquals(3, fractional.jumpCount());
        assertEquals(0.97, defaults().slideRetention(), 1e-12);
    }

    @Test
    void defaultsReconcileOnlyBasesStillOwnedByConfiguration() {
        var strength = MovementAttribute.BULLET_JUMP_STRENGTH;
        assertEquals(24, strength.configuredBase(18, 18, 24));
        assertEquals(30, strength.configuredBase(30, 18, 24));
        assertEquals(20, strength.configuredBase(24, 24, 20));
        assertThrows(IllegalArgumentException.class, () -> strength.configuredBase(18, 18, Double.NaN));
    }

    @Test
    void effectiveValuesAreFiniteAndRespectDeclaredBounds() {
        for (MovementAttribute definition : MovementAttribute.values()) {
            assertEquals(definition.defaultValue(), definition.effective(Double.NaN));
            assertEquals(definition.minimum(), definition.effective(-1000));
            assertEquals(definition.maximum(), definition.effective(1000));
        }
    }

    @Test
    void groundAndAirJumpsShareTheSameTotalCapacity() {
        AirResources resources = new AirResources();
        assertTrue(resources.consumeJump(defaults()));
        assertTrue(resources.consumeJump(defaults()));
        assertFalse(resources.consumeJump(defaults()));
        resources.landed();
        assertEquals(0, resources.jumpsUsed());
        assertTrue(resources.consumeJump(defaults()));
    }

    @Test
    void assistedModesConsumeOnePoolAndCannotResetItBySwitching() {
        AirResources resources = new AirResources();
        assertEquals(1.25, resources.consumeAssistedSeconds(defaults(), 1.25), 1e-12);
        assertEquals(2.75, resources.remainingAssistedSeconds(defaults()), 1e-12);
        assertEquals(2.75, resources.consumeAssistedSeconds(defaults(), 10), 1e-12);
        assertEquals(0, resources.consumeAssistedSeconds(defaults(), 0.05));
        resources.landed();
        assertEquals(4, resources.remainingAssistedSeconds(defaults()));
    }

    @Test
    void capacityChangesRetainSpentTimeAndJumpCount() {
        AirResources resources = new AirResources();
        resources.consumeJump(defaults());
        resources.consumeJump(defaults());
        resources.consumeAssistedSeconds(defaults(), 2);
        var reduced = new MovementValues(a -> a == MovementAttribute.AIR_MANEUVER_DURATION || a == MovementAttribute.JUMP_COUNT ? 1 : a.defaultValue());
        assertFalse(resources.consumeJump(reduced));
        assertEquals(0, resources.remainingAssistedSeconds(reduced));
        assertEquals(2, resources.remainingAssistedSeconds(defaults()));
        assertThrows(IllegalArgumentException.class, () -> resources.consumeAssistedSeconds(defaults(), -0.1));
    }

    @Test
    void fractionalTickStepsCannotLeaveAnExtraUsableTick() {
        AirResources resources = new AirResources();
        for (int tick = 0; tick < 80; tick++) resources.consumeAssistedSeconds(defaults(), 0.05);
        assertEquals(0, resources.remainingAssistedSeconds(defaults()));
        assertEquals(0, resources.consumeAssistedSeconds(defaults(), 0.05));
    }

    @Test void legacyDefaultMigrationUpdatesOnlyMatchingDefaultsOnce() {
        assertEquals(4,MovementAttribute.AIR_MANEUVER_DURATION.migrateConfiguredDefault(3,0));
        assertEquals(MovementAttribute.GLIDE_GRAVITY_SCALE.defaultValue(),MovementAttribute.GLIDE_GRAVITY_SCALE.migrateConfiguredDefault(.2,0));
        assertEquals(2.5,MovementAttribute.AIR_MANEUVER_DURATION.migrateConfiguredDefault(2.5,0));
        assertEquals(.15,MovementAttribute.GLIDE_GRAVITY_SCALE.migrateConfiguredDefault(.15,0));
        assertEquals(3,MovementAttribute.AIR_MANEUVER_DURATION.migrateConfiguredDefault(3,1));
        assertEquals(.2,MovementAttribute.GLIDE_GRAVITY_SCALE.migrateConfiguredDefault(.2,1));
        assertEquals(.2,MovementAttribute.GLIDE_GRAVITY_SCALE.migrateConfiguredDefault(.020765292747931523,1));
        assertEquals(.03,MovementAttribute.SLIDE_FRICTION.migrateConfiguredDefault(.04,1));
        assertEquals(.03,MovementAttribute.SLIDE_FRICTION.migrateConfiguredDefault(.01,2));
        assertEquals(.03,MovementAttribute.SLIDE_FRICTION.migrateConfiguredDefault(.015,3));
        assertEquals(.015,MovementAttribute.SLIDE_FRICTION.migrateConfiguredDefault(.015,4));
        assertEquals(.015,MovementAttribute.SLIDE_FRICTION.migrateConfiguredDefault(.015,2));
        assertEquals(.012,MovementAttribute.SLIDE_FRICTION.migrateConfiguredDefault(.012,3));
        assertEquals(.01,MovementAttribute.SLIDE_FRICTION.migrateConfiguredDefault(.01,3));
        assertEquals(.012,MovementAttribute.SLIDE_FRICTION.migrateConfiguredDefault(.012,2));
        assertEquals(1.5,defaults().get(MovementAttribute.SLIDE_SPEED_MULTIPLIER),1e-9);
        assertEquals(.008,MovementAttribute.SLIDE_FRICTION.migrateConfiguredDefault(.008,1));
        assertEquals(.04,MovementAttribute.SLIDE_FRICTION.migrateConfiguredDefault(.04,2));
        assertEquals(.020765292747931523,MovementAttribute.GLIDE_GRAVITY_SCALE.migrateConfiguredDefault(.020765292747931523,2));
    }
}
