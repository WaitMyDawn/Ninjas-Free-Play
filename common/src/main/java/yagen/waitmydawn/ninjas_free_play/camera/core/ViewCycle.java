package yagen.waitmydawn.ninjas_free_play.camera.core;

import java.util.List;

/**
 * The serialized bit order is stable independently of enum declaration order.
 */
public record ViewCycle(String mask) {
    public static final List<CameraView> ORDER = List.of(CameraView.FIRST_PERSON, CameraView.THIRD_PERSON_BACK,
            CameraView.SHOULDER_AUTO, CameraView.SHOULDER_FIXED, CameraView.THIRD_PERSON_FRONT);
    public static final ViewCycle ALL = new ViewCycle("11111");

    public ViewCycle {
        if (!valid(mask))
            throw new IllegalArgumentException("Expected five binary digits with at least one enabled view");
    }

    public static boolean valid(String value) {
        if (value == null || value.length() != ORDER.size()) return false;
        boolean any = false;
        for (int i = 0; i < value.length(); i++) {
            char bit = value.charAt(i);
            if (bit != '0' && bit != '1') return false;
            any |= bit == '1';
        }
        return any;
    }

    public boolean includes(CameraView view, boolean nfpAvailable) {
        return mask.charAt(ORDER.indexOf(view)) == '1'
                && (nfpAvailable || view != CameraView.SHOULDER_AUTO && view != CameraView.SHOULDER_FIXED);
    }

    public CameraView next(CameraView current, boolean nfpAvailable) {
        int start = ORDER.indexOf(current);
        for (int step = 1; step <= ORDER.size(); step++) {
            var candidate = ORDER.get((start + step) % ORDER.size());
            if (includes(candidate, nfpAvailable)) return candidate;
        }
        // A saved NFP-only cycle remains intact while its camera context is unavailable.
        return CameraView.FIRST_PERSON;
    }

    public CameraView reconcile(CameraView current, boolean nfpAvailable) {
        return includes(current, nfpAvailable) ? current : next(current, nfpAvailable);
    }
}
