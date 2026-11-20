package io.github.rkaushikdev.jeepdolly.enforcement;

import java.time.LocalDate;

/**
 * Where a carrier stands on money it owes the office, and when that stops it.
 *
 * <p>A carrier with an unpaid citation still gets permits. That surprises people and it is
 * deliberate: a ticket written on Tuesday is not settled by Thursday whatever anybody intends, and
 * an office that stopped writing permits over an unpaid ticket would stop the state's freight over
 * its own paperwork.
 *
 * <p>What stops a carrier is a citation that is past due: the contest window has closed, the grace
 * period after it has run, and the money has still not arrived. A contested citation is never past
 * due, however old, and the amount does not matter. A hold is lifted by paying, the same day.
 */
public final class Standing {

    /** Days after the contest window closes before an unpaid citation holds. */
    public static final int GRACE_DAYS = 30;

    private Standing() {}

    /** When an unpaid citation stops being late and starts being a hold, with the usual grace. */
    public static LocalDate dueDay(LocalDate written, int contestDays) {
        return dueDay(written, contestDays, GRACE_DAYS);
    }

    /** When an unpaid citation stops being late and starts being a hold. */
    public static LocalDate dueDay(LocalDate written, int contestDays, int grace) {
        return written.plusDays((long) contestDays + grace);
    }
}
