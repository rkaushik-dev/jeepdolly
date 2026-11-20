package io.github.rkaushikdev.jeepdolly.escorts;

import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.domain.Envelope;
import io.github.rkaushikdev.jeepdolly.domain.HighwayClass;
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
 * <p>The thresholds are published and change by order, so they are a value and not constants in
 * the arithmetic.
 */
public record EscortRules(
        Inches frontWidth,
        Inches rearWidth,
        Inches policeWidth,
        Inches rearLength,
        Inches bothLength,
        Inches poleHeight,
        Inches rearOverhang) {

    /** The table as published. */
    public static final EscortRules STANDARD = new EscortRules(
            Inches.of(12),
            Inches.of(14),
            Inches.of(16),
            Inches.of(100),
            Inches.of(125),
            Inches.of(14, 6),
            Inches.of(10));

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

        if (envelope.width().compareTo(frontWidth) > 0 && !dividedOnly) {
            front = 1;
            reasons.add("width over " + frontWidth);
        }
        if (envelope.width().compareTo(rearWidth) > 0) {
            rear = Math.max(rear, 1);
            reasons.add("width over " + rearWidth);
        }
        if (envelope.width().compareTo(policeWidth) > 0) {
            police = true;
            reasons.add("width over " + policeWidth);
        }
        if (envelope.length().compareTo(rearLength) > 0) {
            rear = Math.max(rear, 1);
            reasons.add("length over " + rearLength);
        }
        if (envelope.length().compareTo(bothLength) > 0 && !dividedOnly) {
            front = Math.max(front, 1);
            reasons.add("length over " + bothLength);
        }
        if (envelope.rearOverhang().compareTo(rearOverhang) > 0) {
            rear = Math.max(rear, 1);
            reasons.add("rear overhang over " + rearOverhang);
        }
        if (envelope.height().compareTo(poleHeight) > 0) {
            front = Math.max(front, 1);
            pole = true;
            reasons.add("height over " + poleHeight);
        }
        if (superload) {
            police = true;
            reasons.add("superload");
        }
        return new Escorts(front, rear, police, pole, reasons);
    }
}
