package io.github.rkaushikdev.jeepdolly.travel;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.domain.Envelope;
import io.github.rkaushikdev.jeepdolly.domain.SizeLine;

/**
 * One line of the when table: a measure past a threshold holds the load to daylight, or parks it
 * in a wind.
 *
 * <p>A line may be more generous on a trip that is divided highway the whole way. A wide load is
 * held to daylight because of what it does to the oncoming lane, and on the interstate there is no
 * oncoming lane, so the width line there reads fourteen feet rather than twelve.
 *
 * @param line the measure and the size the line is drawn at on any road
 * @param overDivided the size the line is drawn at on a trip that is divided highway throughout
 * @param windMph the wind the load parks at, for a wind line; nothing for a daylight line
 */
public record WhenRule(SizeLine line, Inches overDivided, Hold hold, int windMph) {

    /** What a line of the when table does to a load past it. */
    public enum Hold {
        DAYLIGHT,
        WIND
    }

    public WhenRule {
        if (overDivided.compareTo(line.over()) < 0) {
            throw new Impossible("a divided highway is not held to a tighter line than the other roads");
        }
        if ((hold == Hold.WIND) != (windMph > 0)) {
            throw new Impossible("a wind line names the wind it parks at, and only a wind line does");
        }
    }

    /** A line that holds a load past it to the daylight window on any road. */
    public static WhenRule daylight(String measure, Inches over) {
        return daylight(measure, over, over);
    }

    /** A line that holds a load past it to the daylight window, more generous on divided highway. */
    public static WhenRule daylight(String measure, Inches over, Inches overDivided) {
        return new WhenRule(new SizeLine(measure, over), overDivided, Hold.DAYLIGHT, 0);
    }

    /** A line that parks a load past it when the wind reaches {@code mph}. */
    public static WhenRule wind(String measure, Inches over, int mph) {
        return new WhenRule(new SizeLine(measure, over), over, Hold.WIND, mph);
    }

    /** The line as it is drawn on a trip that is, or is not, divided highway throughout. */
    public SizeLine drawn(boolean dividedOnly) {
        return dividedOnly ? line.at(overDivided) : line;
    }

    /** Whether this load, on a trip that is or is not divided highway throughout, is past the line. */
    public boolean fires(Envelope envelope, boolean dividedOnly) {
        return drawn(dividedOnly).past(envelope);
    }

    /** The reason as the limits list it, at the size that applied: {@code width over 12 ft}. */
    public String reason(boolean dividedOnly) {
        return drawn(dividedOnly).reason();
    }
}
