package io.github.rkaushikdev.jeepdolly;

import java.time.LocalDate;

/**
 * One signed seasonal order, for one region, over one span of days.
 *
 * <p>The date a thaw order starts is a judgement made on a Thursday afternoon looking at a frost
 * gauge, which is why it is stored as a signed order with a number on it and not computed from the
 * calendar. Orders are regional: the county line is not a frost line but it is where the order
 * stops, because that is how the orders are written.
 *
 * @param share whole percent of the statutory limit that applies while the order is in force. A
 *     thaw order is below a hundred and a frozen order is above it, and an order that does neither
 *     is a mistake in the typing.
 */
public record SeasonOrder(String number, String region, SeasonKind kind, LocalDate start, LocalDate end, int share) {

    public SeasonOrder {
        if (end.isBefore(start)) {
            throw new Impossible("order " + number + " ends before it starts", Facts.of("order", number));
        }
        if (share < 1 || share > 200) {
            throw new Impossible("order " + number + " has an unreadable share " + share);
        }
        if (kind == SeasonKind.THAW && share >= 100) {
            throw new Impossible("thaw order " + number + " does not reduce anything");
        }
        if (kind == SeasonKind.FROZEN && share <= 100) {
            throw new Impossible("frozen order " + number + " does not allow anything");
        }
    }

    /** Whether the order is in force on a day, both end days included. */
    public boolean covers(LocalDate day) {
        return !day.isBefore(start) && !day.isAfter(end);
    }

    public boolean overlaps(SeasonOrder other) {
        return !start.isAfter(other.end) && !other.start.isAfter(end);
    }

    /** The limit while this order is in force, truncated to whole pounds. */
    public Pounds appliedTo(Pounds limit) {
        return new Pounds(Math.floorDiv(limit.value() * share, 100));
    }

    /** As the clerk reads it off the board: {@code 25-04 north thaw 65%}. */
    @Override
    public String toString() {
        return number + " " + region + " " + kind + " " + share + "%";
    }
}
