package yagen.waitmydawn.ninjas_free_play.core;

public final class SlidePrediction {
    private SlidePrediction() {}

    public static boolean needsVelocityCorrection(double predicted, double authoritative) {
        if (!Double.isFinite(predicted) || predicted < 0 || !Double.isFinite(authoritative) || authoritative < 0)
            throw new IllegalArgumentException("Invalid slide velocity");
        // Displacement and input packets are sampled on different simulation boundaries.
        return Math.abs(predicted - authoritative) > Math.max(.02, authoritative * .1);
    }
}
