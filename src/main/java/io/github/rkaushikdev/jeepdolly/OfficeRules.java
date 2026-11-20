package io.github.rkaushikdev.jeepdolly;

import io.github.rkaushikdev.jeepdolly.enforcement.Standing;
import io.github.rkaushikdev.jeepdolly.permits.Reprint;
import io.github.rkaushikdev.jeepdolly.reports.LineSurvey;
import io.github.rkaushikdev.jeepdolly.services.Renewals;
import java.util.List;

/**
 * The numbers the office changes without shipping anything.
 *
 * <p>How many copies a clerk may print, how far ahead the renewal run looks, how long a carrier has
 * before an unpaid citation holds them, how often an overhead line is walked. Each one defaults to
 * the constant in the code that owns the rule, so a site that sets nothing behaves exactly as the
 * code reads. Nothing here changes what a rule means, only where the line is drawn.
 */
public record OfficeRules(int freeCopies, int renewalDays, int citationGrace, int lineSurveyYears) {

    public OfficeRules {
        if (freeCopies < 1) {
            throw new Impossible("an office prints at least one copy without a signature");
        }
        if (renewalDays < 1) {
            throw new Impossible("a renewal run looks at least a day ahead");
        }
        if (citationGrace < 0) {
            throw new Impossible("a grace period is not negative");
        }
        if (lineSurveyYears < 1) {
            throw new Impossible("a survey cycle is at least a year");
        }
    }

    /** The rules as the code that owns each one sets it. */
    public static OfficeRules defaults() {
        return new OfficeRules(Reprint.FREELY, Renewals.AHEAD_DAYS, Standing.GRACE_DAYS, LineSurvey.CYCLE_YEARS);
    }

    public OfficeRules withFreeCopies(int value) {
        return new OfficeRules(value, renewalDays, citationGrace, lineSurveyYears);
    }

    public OfficeRules withRenewalDays(int value) {
        return new OfficeRules(freeCopies, value, citationGrace, lineSurveyYears);
    }

    public OfficeRules withCitationGrace(int value) {
        return new OfficeRules(freeCopies, renewalDays, value, lineSurveyYears);
    }

    public OfficeRules withLineSurveyYears(int value) {
        return new OfficeRules(freeCopies, renewalDays, citationGrace, value);
    }

    /** The rules as they print on the settings page. */
    public List<String> lines() {
        return List.of(
                "free copies       " + freeCopies,
                "renewal window    " + renewalDays + " days",
                "citation grace    " + citationGrace + " days",
                "line survey       " + lineSurveyYears + " years");
    }

    /** The short form: {@code 3 copies, 30 day renewal window}. */
    @Override
    public String toString() {
        return freeCopies + " copies, " + renewalDays + " day renewal window";
    }
}
