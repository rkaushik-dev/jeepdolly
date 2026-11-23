package io.github.rkaushikdev.jeepdolly.domain;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Inches;

/**
 * One measure of the envelope against one published size: the line a table draws.
 *
 * <p>Every table the office publishes about size is a list of these with something hung on each:
 * width over twelve feet puts a car in front, height over fourteen six holds the load to daylight,
 * width over sixteen makes it a superload. The line itself is the same in all of them. A load is
 * past it when the measure is over the size, never at it, and the reason it prints is the measure
 * and the size in the permit's own words.
 *
 * @param measure the measure of the envelope the line reads, as {@link Envelope#MEASURES} names it
 * @param over the size a load has to be past, not at, to be past the line
 */
public record SizeLine(String measure, Inches over) {

    public SizeLine {
        if (!Envelope.MEASURES.contains(measure)) {
            throw new Impossible("an envelope has no " + measure);
        }
        if (over.value() <= 0) {
            throw new Impossible("a line is drawn at a size past nothing, not at " + over);
        }
    }

    /** Whether this load is past the line. */
    public boolean past(Envelope envelope) {
        return envelope.measure(measure).compareTo(over) > 0;
    }

    /** The same measure drawn at another size. */
    public SizeLine at(Inches size) {
        return new SizeLine(measure, size);
    }

    /** The reason as every table prints it: {@code rear overhang over 10 ft}. */
    public String reason() {
        return measure.replace('_', ' ') + " over " + over;
    }

    @Override
    public String toString() {
        return reason();
    }
}
