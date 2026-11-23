package io.github.rkaushikdev.jeepdolly.travel;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.domain.Envelope;

/**
 * One line of the when table: a measure past a threshold holds the load to daylight, or parks it
 * in a wind.
 *
 * @param measure the measure of the envelope the line reads, as {@link Envelope#MEASURES} names it
 * @param over the line fires for a load past this, not at it
 * @param windMph the wind the load parks at, for a wind line; nothing for a daylight line
 */
public record WhenRule(String measure, Inches over, Hold hold, int windMph) {

    /** What a line of the when table does to a load past it. */
    public enum Hold {
        DAYLIGHT,
        WIND
    }

    public WhenRule {
        if (!Envelope.MEASURES.contains(measure)) {
            throw new Impossible("an envelope has no " + measure);
        }
        if (over.value() <= 0) {
            throw new Impossible("a when threshold is a size past nothing");
        }
        if ((hold == Hold.WIND) != (windMph > 0)) {
            throw new Impossible("a wind line names the wind it parks at, and only a wind line does");
        }
    }

    /** A line that holds a load past it to the daylight window. */
    public static WhenRule daylight(String measure, Inches over) {
        return new WhenRule(measure, over, Hold.DAYLIGHT, 0);
    }

    /** A line that parks a load past it when the wind reaches {@code mph}. */
    public static WhenRule wind(String measure, Inches over, int mph) {
        return new WhenRule(measure, over, Hold.WIND, mph);
    }

    /** Whether this load is past the line. */
    public boolean fires(Envelope envelope) {
        return envelope.measure(measure).compareTo(over) > 0;
    }

    /** The reason as the limits list it: {@code width over 12 ft}. */
    public String reason() {
        return measure.replace('_', ' ') + " over " + over;
    }
}
