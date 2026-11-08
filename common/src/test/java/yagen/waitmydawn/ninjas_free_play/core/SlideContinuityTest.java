package yagen.waitmydawn.ninjas_free_play.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SlideContinuityTest {
    private static final MovementValues VALUES = new MovementValues(MovementAttribute::defaultValue);
    private static MovementInput input(long sequence, boolean crouch) {
        return new MovementInput(sequence, crouch, false, 0, 0, false, false, 0, 1, false);
    }
    private static ParkourFrame frame(double speed) {
        return new ParkourFrame(speed, 0, speed, 0, 0, WallContact.NONE);
    }

    @Test void aSlideSurvivesAnExternalLiftAndKeepsItsInstanceThroughLanding() {
        var session = new MovementSession();
        assertTrue(session.accept(input(0, true), true, true, true, true, 0, VALUES, frame(.3)).slideStarted());
        long instance = session.snapshot(0).parkour().instance();
        for (int tick = 1; tick < 9; tick++) {
            session.update(true, false, true, tick, VALUES, frame(tick == 2 ? 0 : .3));
            assertEquals(ParkourMode.SLIDE, session.parkour().mode());
            assertEquals(instance, session.snapshot(tick).parkour().instance());
        }
        session.update(true, true, true, 9, VALUES, frame(.25));
        assertEquals(instance, session.snapshot(9).parkour().instance());
        session.accept(input(1, false), true, true, true, true, 10, VALUES, frame(.25));
        assertEquals(ParkourMode.NONE, session.parkour().mode());
    }

    @Test void aGroundedSlideStillEndsWhenItsPhysicalSpeedHasActuallyDecayed() {
        var session = new MovementSession();
        session.accept(input(0, true), true, true, true, true, 0, VALUES, frame(.3));
        session.update(true, true, true, 1, VALUES, frame(.1));
        assertEquals(ParkourMode.NONE, session.parkour().mode());
    }

    @Test void aKickCanFinishItsClipBeforeLandingWithoutLosingItsSlideIntent() {
        var session = new MovementSession();
        session.accept(input(0, true), true, false, true, true, 0, VALUES, frame(.4));
        for (int tick = 1; tick <= 28; tick++) {
            if (tick == 20) session.accept(input(1, true), true, false, true, true, tick, VALUES, frame(.4));
            session.update(true, false, true, tick, VALUES, frame(.4));
        }
        assertEquals(ParkourMode.NONE, session.parkour().mode());
        assertTrue(session.snapshot(28).parkour().landingSlide());
        var observer = new MovementSession();
        observer.restore(session.snapshot(28), 100);
        observer.update(true, true, true, 101, VALUES, frame(.4));
        assertEquals(ParkourMode.SLIDE, observer.parkour().mode());
        assertFalse(observer.snapshot(101).parkour().landingSlide());
        observer.accept(input(2, false), true, true, true, true, 102, VALUES, frame(.4));
        assertEquals(ParkourMode.NONE, observer.parkour().mode());
    }

    @Test void landingNeedsEntrySpeedRatherThanTheLowerContinuationThreshold() {
        for (double speed : new double[]{0, .15, .2, .21585, .6}) {
            var session = new MovementSession();
            session.accept(input(0, true), true, false, true, true, 0, VALUES, frame(.4));
            session.update(true, true, true, 1, VALUES, frame(speed));
            assertEquals(speed + 1e-4 >= MovementValues.SLIDE_ENTER_BLOCKS_PER_TICK ? ParkourMode.SLIDE : ParkourMode.NONE,
                    session.parkour().mode());
        }
    }

    @Test void releasingCrouchOrReplacingTheKickClearsItsLandingIntent() {
        var session = new MovementSession();
        session.accept(input(0, true), true, false, true, true, 0, VALUES, frame(.4));
        session.accept(input(1, false), true, false, true, true, 1, VALUES, frame(.4));
        session.update(true, true, true, 2, VALUES, frame(.4));
        assertEquals(ParkourMode.NONE, session.parkour().mode());
        assertFalse(session.snapshot(2).parkour().landingSlide());
        var roll = new MovementSession();
        roll.accept(input(0, true), true, false, true, true, 0, VALUES, frame(.4));
        roll.accept(new MovementInput(1, true, false, 0, 0, true, false, 0, 1),
                true, false, true, true, 1, VALUES, frame(.4));
        assertFalse(roll.snapshot(1).parkour().landingSlide());
    }
}
