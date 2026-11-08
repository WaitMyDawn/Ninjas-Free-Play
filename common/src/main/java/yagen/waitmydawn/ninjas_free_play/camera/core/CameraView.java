package yagen.waitmydawn.ninjas_free_play.camera.core;

/**
 * Logical views remain separate from platform enums that only support the three vanilla choices.
 */
public enum CameraView {
    FIRST_PERSON, THIRD_PERSON_BACK, SHOULDER_AUTO, SHOULDER_FIXED, THIRD_PERSON_FRONT;

    public CameraView next(boolean shoulderEnabled) {
        return ViewCycle.ALL.next(this, shoulderEnabled);
    }
}
