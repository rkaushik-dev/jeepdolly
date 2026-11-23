package io.github.rkaushikdev.jeepdolly.escorts;

import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.domain.Envelope;
import io.github.rkaushikdev.jeepdolly.domain.HighwayClass;
import io.github.rkaushikdev.jeepdolly.escorts.EscortRule.Post;
import java.util.ArrayList;
import java.util.List;

/**
 * The escort table: how many pilot cars, on which end, and when a trooper comes too.
 *
 * <p>The escort table is the part of a permit haulers argue about, because escorts cost more than
 * the permit does. It is also the part that keeps people alive, so the office does not negotiate it
 * at the counter. Three things drive it and they interact, which is why this is a table and not
 * three independent rules.
 *
 * <p>Width drives most of it. Past twelve feet a load needs somebody ahead of it on an undivided
 * road, because the oncoming traffic is the danger. Past fourteen it needs somebody behind as well.
 * Past sixteen it needs a trooper, because at that width the load is using both lanes. Length and
 * overhang drive the rear: a hundred foot combination needs a rear escort whatever it is wide, and
 * a long rear overhang needs one because the overhang is what a following car runs under. Height
 * drives a pole car, which goes in front on every class of road, including the interstate, which is
 * the exception people forget.
 *
 * <p>The road class changes the answer twice. On a divided highway the front escort is not needed
 * for width alone, because there is no oncoming traffic to warn. And on the interstate a trooper is
 * ordered through a different office, so the permit says one is required and does not pretend to
 * have arranged it.
 *
 * <p>A long front overhang wants a car in front for the same reason width does. A beam that
 * projects ten feet past the bumper swings across the centre line on every curve before the cab
 * does, and the oncoming driver sees the cab.
 *
 * <p>The thresholds are published and change by order, so the table is a list of {@link
 * EscortRule} lines rather than constants in the arithmetic, read in the order it is printed. The
 * reasons come back in that order, one for each line the load is past, and two lines putting a car
 * in the same place still put one car there.
 *
 * @param rows the lines of the table, in the order it is printed
 */
public record EscortRules(List<EscortRule> rows) {

    /** The table as published. */
    public static final EscortRules STANDARD = new EscortRules(List.of(
            EscortRule.undivided("width", Inches.of(12), Post.FRONT),
            EscortRule.anyRoad("width", Inches.of(14), Post.REAR),
            EscortRule.anyRoad("width", Inches.of(16), Post.POLICE),
            EscortRule.anyRoad("length", Inches.of(100), Post.REAR),
            EscortRule.undivided("length", Inches.of(125), Post.FRONT),
            EscortRule.anyRoad("rear_overhang", Inches.of(10), Post.REAR),
            EscortRule.undivided("front_overhang", Inches.of(10), Post.FRONT),
            EscortRule.anyRoad("height", Inches.of(14, 6), Post.POLE)));

    public EscortRules {
        rows = List.copyOf(rows);
    }

    /** Read the table for an ordinary load on these classes of road. */
    public Escorts required(Envelope envelope, List<HighwayClass> classes) {
        return required(envelope, classes, false);
    }

    /** Read the table for this load on these classes of road. A superload always has a trooper. */
    public Escorts required(Envelope envelope, List<HighwayClass> classes, boolean superload) {
        boolean dividedOnly = !classes.isEmpty() && classes.stream().allMatch(HighwayClass::federal);
        int front = 0;
        int rear = 0;
        boolean police = false;
        boolean pole = false;
        List<String> reasons = new ArrayList<>();
        for (EscortRule row : rows) {
            if (!row.fires(envelope, dividedOnly)) {
                continue;
            }
            switch (row.post()) {
                case FRONT -> front = 1;
                case REAR -> rear = 1;
                case POLE -> {
                    front = 1;
                    pole = true;
                }
                case POLICE -> police = true;
            }
            reasons.add(row.reason());
        }
        if (superload) {
            police = true;
            reasons.add("superload");
        }
        return new Escorts(front, rear, police, pole, reasons);
    }
}
