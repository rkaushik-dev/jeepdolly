package io.github.rkaushikdev.jeepdolly.routing;

import io.github.rkaushikdev.jeepdolly.Miles;
import io.github.rkaushikdev.jeepdolly.domain.HighwayClass;

/**
 * Chargeable miles on one class of road.
 *
 * @param measured the miles driven on the class, off the road log
 * @param charged the measured miles rounded up to the whole mile on their own
 */
public record ClassMiles(HighwayClass highwayClass, Miles measured, int charged) {

    /** As the worksheet prints it: {@code state aid: 9.40 mi measured, 10 charged}. */
    @Override
    public String toString() {
        return highwayClass + ": " + measured + " measured, " + charged + " charged";
    }
}
