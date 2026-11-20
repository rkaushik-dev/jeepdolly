package io.github.rkaushikdev.jeepdolly.utilities;

import java.time.LocalDate;

/**
 * One crossing a load needs raised, and who has to be rung about it.
 *
 * <p>The hauler is the one who rings, so the condition carries a name and a telephone number.
 *
 * @param noticeBy the last working day the request can reach the owner
 * @param inTime whether the application was filed early enough for that
 */
public record Lift(Crossing crossing, LineOwner owner, LocalDate noticeBy, boolean inTime) {

    /** Whether the wire can be raised and the owner told in time. */
    public boolean possible() {
        return crossing.liftable() && inTime;
    }

    /** {@code US201-2 at Fairfield village: ring Central Maine Power on 207 555 0100 by 2025-04-02}. */
    @Override
    public String toString() {
        return crossing.segment() + " at " + crossing.place() + ": ring " + owner.name() + " on " + owner.telephone()
                + " by " + noticeBy;
    }
}
