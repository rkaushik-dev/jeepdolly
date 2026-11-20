package io.github.rkaushikdev.jeepdolly.utilities;

import io.github.rkaushikdev.jeepdolly.Conflict;
import java.time.LocalDate;

/**
 * Whoever owns a wire, and how much warning they want before a lift.
 *
 * <p>A crew is not free tomorrow. Every owner publishes how many working days it wants, and the
 * office reads that against the day the application was filed, not the day the permit is written.
 * Working days, not days: a five day notice filed on a Thursday reaches the crew the following
 * Thursday, and an office that counted calendar days would promise a lift on a Tuesday that nobody
 * was coming to.
 *
 * @param noticeDays the working days of warning the owner wants
 */
public record LineOwner(String ident, String name, String telephone, int noticeDays, String note) {

    public LineOwner {
        if (noticeDays < 0) {
            throw new Conflict(ident + " cannot want negative notice");
        }
    }

    /** An owner with no note on the card. */
    public LineOwner(String ident, String name, String telephone, int noticeDays) {
        this(ident, name, telephone, noticeDays, "");
    }

    /** The last working day a request can reach them for a move on {@code travel}. */
    public LocalDate noticeBy(LocalDate travel) {
        LocalDate standing = travel;
        int left = noticeDays;
        while (left > 0) {
            standing = standing.minusDays(1);
            if (!Lines.WEEKEND.contains(standing.getDayOfWeek())) {
                left -= 1;
            }
        }
        return standing;
    }

    /** Whether a request filed on {@code filed} reaches them in time for a move on {@code travel}. */
    public boolean inTime(LocalDate filed, LocalDate travel) {
        return !filed.isAfter(noticeBy(travel));
    }

    /** As the counter reads it: {@code CMP  Central Maine Power  207 555 0100  5 days notice}. */
    @Override
    public String toString() {
        return ident + "  " + name + "  " + telephone + "  " + noticeDays + " days notice";
    }
}
