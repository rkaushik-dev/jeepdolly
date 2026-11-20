package io.github.rkaushikdev.jeepdolly.limits;

import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.domain.Envelope;
import java.util.ArrayList;
import java.util.List;

/**
 * The numbers that make a load a superload, published and dated.
 *
 * <p>They are data rather than constants in the ladder, so a stricter set moves the line without
 * touching the rules.
 *
 * @param axleGroup the heaviest any one group may be before the load is a superload
 */
public record Thresholds(Pounds gross, Pounds axleGroup, Inches width, Inches height, Inches length) {

    /** The thresholds the office works to. */
    public static final Thresholds STANDARD =
            new Thresholds(new Pounds(150000), new Pounds(60000), Inches.of(16), Inches.of(16), Inches.of(150));

    /** Which thresholds this load is past, in the order they are printed. */
    public List<String> crossedBy(Envelope envelope, WeightReading reading) {
        List<String> found = new ArrayList<>();
        if (reading.gross().compareTo(gross) > 0) {
            found.add("gross");
        }
        if (reading.groups().stream().anyMatch(group -> group.weight().compareTo(axleGroup) > 0)) {
            found.add("axle group");
        }
        if (envelope.width().compareTo(width) > 0) {
            found.add("width");
        }
        if (envelope.height().compareTo(height) > 0) {
            found.add("height");
        }
        if (envelope.length().compareTo(length) > 0) {
            found.add("length");
        }
        return List.copyOf(found);
    }
}
