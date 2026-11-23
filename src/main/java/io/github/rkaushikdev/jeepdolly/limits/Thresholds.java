package io.github.rkaushikdev.jeepdolly.limits;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.domain.Envelope;
import io.github.rkaushikdev.jeepdolly.domain.SizeLine;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The numbers that make a load a superload, published and dated.
 *
 * <p>They are data rather than constants in the ladder, so a stricter set moves the line without
 * touching the rules.
 *
 * <p>The axle group threshold is published by the size of the group, the way the statutory table
 * is, because one number for every group was too much for a single and too little for a quad. Sixty
 * thousand pounds on one axle is a superload by any reading; sixty thousand spread over four is a
 * well built trailer. A group of a size the table does not name is read as the largest named group
 * below it plus {@link #beyondTable} for each axle past that one.
 *
 * @param axleGroups the heaviest a group of each named size may be before the load is a superload
 * @param beyondTable what each axle past the largest named group adds to its threshold
 */
public record Thresholds(
        Pounds gross, Map<Integer, Pounds> axleGroups, Pounds beyondTable, Inches width, Inches height, Inches length) {

    /** The thresholds the office works to. */
    public static final Thresholds STANDARD = new Thresholds(
            new Pounds(150000),
            Map.of(1, new Pounds(30000), 2, new Pounds(60000), 3, new Pounds(70000), 4, new Pounds(80000)),
            new Pounds(10000),
            Inches.of(16),
            Inches.of(16),
            Inches.of(150));

    public Thresholds {
        axleGroups = Collections.unmodifiableMap(new TreeMap<>(axleGroups));
        if (!axleGroups.containsKey(1)) {
            throw new Impossible("the axle group thresholds start at the single");
        }
    }

    /** Thresholds that hold every group to one number, whatever its size. */
    public Thresholds(Pounds gross, Pounds axleGroup, Inches width, Inches height, Inches length) {
        this(gross, Map.of(1, axleGroup), Pounds.ZERO, width, height, length);
    }

    /**
     * The heaviest a group of this many axles may be before the load is a superload.
     *
     * @throws Impossible for a group of no axles
     */
    public Pounds axleGroup(int count) {
        if (count < 1) {
            throw new Impossible("a group holds at least one axle");
        }
        Map.Entry<Integer, Pounds> named = new TreeMap<>(axleGroups).floorEntry(count);
        return named.getValue().plus(beyondTable.times(count - named.getKey()));
    }

    /** Which thresholds this load is past, in the order they are printed. */
    public List<String> crossedBy(Envelope envelope, WeightReading reading) {
        List<String> found = new ArrayList<>();
        if (reading.gross().compareTo(gross) > 0) {
            found.add("gross");
        }
        if (reading.groups().stream()
                .anyMatch(one -> one.weight().compareTo(axleGroup(one.group().count())) > 0)) {
            found.add("axle group");
        }
        for (SizeLine line : sizeLines()) {
            if (line.past(envelope)) {
                found.add(line.measure());
            }
        }
        return List.copyOf(found);
    }

    /** The size thresholds as lines, in the order they are printed. */
    public List<SizeLine> sizeLines() {
        return List.of(new SizeLine("width", width), new SizeLine("height", height), new SizeLine("length", length));
    }

    /**
     * The size threshold for one measure of the envelope, by the name the form gives it.
     *
     * @throws Impossible for a measure the thresholds do not publish, such as an overhang
     */
    public Inches size(String measure) {
        return sizeLines().stream()
                .filter(line -> line.measure().equals(measure))
                .findFirst()
                .map(SizeLine::over)
                .orElseThrow(() -> new Impossible("no superload threshold is published for " + measure));
    }
}
