package io.github.rkaushikdev.jeepdolly.utilities;

import io.github.rkaushikdev.jeepdolly.Inches;

/**
 * One crossing nothing can be done about, for this load on this route.
 *
 * <p>Either the wire cannot be raised high enough, or it can and the notice is too short. The
 * refusal for a late notice says so in days rather than in dates, because that is the sentence the
 * hauler will repeat to his customer.
 *
 * @param wanted the clearance the load wants, its height plus the daylight
 * @param late whether the wire could be raised but the owner cannot be told in time
 */
public record BlockedLine(Crossing crossing, LineOwner owner, Inches wanted, boolean late) {

    /** A wire that cannot be raised far enough. */
    public BlockedLine(Crossing crossing, LineOwner owner, Inches wanted) {
        this(crossing, owner, wanted, false);
    }

    /** The refusal as the letter writes it. */
    @Override
    public String toString() {
        String where = crossing.segment() + " at " + crossing.place();
        if (late) {
            return where + " wants " + owner.noticeDays() + " working days notice to " + owner.name();
        }
        return where + " hangs at " + crossing.height() + " and this load wants " + wanted;
    }
}
