package io.github.rkaushikdev.jeepdolly.escorts;

import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.domain.Envelope;
import io.github.rkaushikdev.jeepdolly.domain.HighwayClass;
import io.github.rkaushikdev.jeepdolly.escorts.EscortRule.Post;
import io.github.rkaushikdev.jeepdolly.structures.StructureFinding;
import io.github.rkaushikdev.jeepdolly.structures.StructureReading;
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
 * <p>Past eighteen feet wide on a road with oncoming traffic one car ahead is not enough: the load
 * has the whole road, so the first car warns the oncoming traffic and the second, closer in, puts
 * it on the shoulder and holds it there while the load goes by.
 *
 * <p>A long front overhang wants a car in front for the same reason width does. A beam that
 * projects ten feet past the bumper swings across the centre line on every curve before the cab
 * does, and the oncoming driver sees the cab.
 *
 * <p>The tallest loads, and every superload, also want the route surveyed before the move. Past
 * sixteen feet the clearance records are not to be trusted on their own, because nobody measures a
 * sign bridge after it is resurfaced under, and a superload is a load the office has decided is
 * worth a week's notice whatever its height.
 *
 * <p>A bridge the load crosses under conditions wants cars whatever the load measures. The
 * condition is that the load is alone on the structure, and the only way to make that true on a
 * public road is to stop the traffic: a car behind holds the following traffic back, and on a road
 * with oncoming traffic a car in front holds that too. The reason names the structure, because
 * the pilot driver has to know where to stop.
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
            EscortRule.undivided("width", Inches.of(18), Post.FRONT, 2),
            EscortRule.anyRoad("length", Inches.of(100), Post.REAR),
            EscortRule.undivided("length", Inches.of(125), Post.FRONT),
            EscortRule.anyRoad("rear_overhang", Inches.of(10), Post.REAR),
            EscortRule.undivided("front_overhang", Inches.of(10), Post.FRONT),
            EscortRule.anyRoad("height", Inches.of(14, 6), Post.POLE),
            EscortRule.anyRoad("height", Inches.of(16), Post.SURVEY)));

    public EscortRules {
        rows = List.copyOf(rows);
    }

    /** Read the table for an ordinary load on these classes of road. */
    public Escorts required(Envelope envelope, List<HighwayClass> classes) {
        return required(envelope, classes, false);
    }

    /** Read the table for this load on these classes of road. A superload always has a trooper. */
    public Escorts required(Envelope envelope, List<HighwayClass> classes, boolean superload) {
        return required(envelope, classes, superload, new StructureReading(List.of()));
    }

    /**
     * Read the table for this load on these classes of road, and hold the traffic at every bridge
     * the structure reading lets it cross only under conditions.
     */
    public Escorts required(
            Envelope envelope, List<HighwayClass> classes, boolean superload, StructureReading structures) {
        boolean dividedOnly = HighwayClass.dividedThroughout(classes);
        int front = 0;
        int rear = 0;
        boolean police = false;
        boolean pole = false;
        boolean survey = superload;
        List<String> reasons = new ArrayList<>();
        for (EscortRule row : rows) {
            if (!row.fires(envelope, dividedOnly)) {
                continue;
            }
            switch (row.post()) {
                case FRONT -> front = Math.max(front, row.cars());
                case REAR -> rear = Math.max(rear, row.cars());
                case POLE -> {
                    front = Math.max(front, 1);
                    pole = true;
                }
                case POLICE -> police = true;
                case SURVEY -> survey = true;
            }
            reasons.add(row.reason());
        }
        for (StructureFinding crossing : structures.conditional()) {
            rear = 1;
            if (!dividedOnly) {
                front = 1;
            }
            reasons.add("hold traffic at " + crossing.structure().name());
        }
        if (superload) {
            police = true;
            reasons.add("superload");
        }
        return new Escorts(front, rear, police, pole, survey, reasons);
    }
}
