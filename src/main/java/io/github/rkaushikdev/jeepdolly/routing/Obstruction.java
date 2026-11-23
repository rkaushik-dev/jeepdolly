package io.github.rkaushikdev.jeepdolly.routing;

/**
 * One thing in the way of a load, on one segment.
 *
 * <p>An obstruction is either fatal or it is a condition. Fatal means no permit over this route,
 * today, for this load: a closure, a clearance the load will not fit under, a structure posted
 * below the gross, a town way in the middle of the trip, a corner it will not get round. A
 * condition means the trip can happen if something is done: a curfew it travels outside, a
 * seasonal posting that lifts on a date, a corner taken with a man in the road holding the
 * traffic.
 *
 * @param kind what sort of trouble, as the worksheet names it: {@code closure}, {@code clearance},
 *     {@code close clearance}, {@code width}, {@code close width}, {@code posting}, {@code posted
 *     season}, {@code curfew}, {@code jurisdiction}, {@code corner} or {@code tight corner}
 * @param needed what the load needs, as the letter prints it
 * @param allowed what the road allows, as the letter prints it
 * @param place where it is when that is a place rather than a piece of road, or empty
 */
public record Obstruction(
        String segment, String kind, String note, String needed, String allowed, boolean fatal, String place) {

    /** A fatal obstruction on a piece of road. */
    public Obstruction(String segment, String kind, String note, String needed, String allowed) {
        this(segment, kind, note, needed, allowed, true, "");
    }

    /** An obstruction on a piece of road, fatal or a condition. */
    public Obstruction(String segment, String kind, String note, String needed, String allowed, boolean fatal) {
        this(segment, kind, note, needed, allowed, fatal, "");
    }

    /** As the worksheet prints it: {@code US2-1 clearance: bridge, needs 14 ft, allows 13 ft (no permit)}. */
    @Override
    public String toString() {
        String weight = fatal ? "no permit" : "condition";
        return segment + " " + kind + ": " + note + ", needs " + needed + ", allows " + allowed + " (" + weight + ")";
    }
}
