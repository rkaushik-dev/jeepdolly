package io.github.rkaushikdev.jeepdolly.services;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Written;
import io.github.rkaushikdev.jeepdolly.conditions.Condition;
import io.github.rkaushikdev.jeepdolly.permits.Application;
import io.github.rkaushikdev.jeepdolly.permits.Permit;
import io.github.rkaushikdev.jeepdolly.permits.Printing;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The letters, which are the only thing a hauler outside the office reads.
 *
 * <p>The refusal letter is the important one. It says every reason, in the order the assessment
 * found them, and it never says anything else. A letter that gives one reason gets a hauler who
 * fixes that one thing and comes back, and the office refuses him again for the second reason, and
 * now it is the office being difficult rather than the load being wrong.
 *
 * <p>The letters do not decide anything. Each one takes what the office already worked out and lays
 * it out, so a change to a rule cannot be hidden in a letter, and a change to a letter cannot change
 * what was decided.
 */
public final class Notices {

    /** Short enough to survive a fax. */
    public static final int LINE_LENGTH = 72;

    /** Who every letter is from. */
    public static final String OFFICE = "Motor Carrier Permit Office";

    /** The number every letter says to ring. */
    public static final String TELEPHONE = "207 555 0100";

    /** No letter is narrower than this. */
    static final int NARROWEST = 20;

    private Notices() {}

    /** Break a sentence at spaces to the letter's width. */
    public static List<String> wrap(String sentence) {
        return wrap(sentence, LINE_LENGTH);
    }

    /**
     * Break a sentence at spaces, because a fax has a right margin. A word longer than the line is
     * left whole rather than cut.
     *
     * @throws Impossible for a width narrower than anybody writes a letter
     */
    public static List<String> wrap(String sentence, int width) {
        if (width < NARROWEST) {
            throw new Impossible("a letter is wider than that");
        }
        return Printing.wrapTo(sentence, width);
    }

    /**
     * Every reason the office found, in the order it found them.
     *
     * @throws Impossible when there are no reasons to give
     */
    public static Notice refusalLetter(Application application, List<String> reasons, LocalDate day) {
        if (reasons.isEmpty()) {
            throw new Impossible("a refusal letter says why");
        }
        List<String> body = new ArrayList<>(wrap("The application " + application.reference() + " to move from "
                + Written.title(application.origin()) + " to " + Written.title(application.destination())
                + " between " + application.start() + " and " + application.end()
                + " cannot be permitted as submitted. The reasons are set out below."));
        body.add("");
        int number = 1;
        for (String reason : reasons) {
            List<String> written = wrap(reason, LINE_LENGTH - 4);
            for (int index = 0; index < written.size(); index++) {
                body.add(
                        index == 0
                                ? String.format(Locale.ROOT, "%2d. %s", number, written.get(index))
                                : "    " + written.get(index));
            }
            number++;
        }
        body.add("");
        body.addAll(wrap("A revised application may be submitted at any time. Nothing in "
                + "this letter is a decision about any other application."));
        return new Notice(application.applicant().name(), "application " + application.reference(), day, body);
    }

    /** What goes out with the permit itself: the face and every condition. */
    public static Notice issueLetter(Permit permit, LocalDate day) {
        List<String> body =
                new ArrayList<>(wrap("Permit " + permit.number() + " is enclosed and is valid as written."));
        body.add("");
        body.addAll(permit.face().lines().toList());
        body.add("");
        body.add("Conditions:");
        for (Condition condition : permit.conditions()) {
            List<String> written = wrap(condition.text(), LINE_LENGTH - 4);
            for (int index = 0; index < written.size(); index++) {
                body.add((index == 0 ? "  - " : "    ") + written.get(index));
            }
        }
        return new Notice(permit.carrierName(), "permit " + permit.number(), day, body);
    }

    /**
     * The Friday telephone call, in writing.
     *
     * @throws Impossible when no permit is named
     */
    public static Notice expiryNotice(List<Permit> permits, String to, LocalDate day) {
        if (permits.isEmpty()) {
            throw new Impossible("an expiry notice names at least one permit");
        }
        List<String> body = new ArrayList<>(wrap("The following permits expire within the next few days."));
        body.add("");
        for (Permit permit : permits) {
            body.add("  " + permit.number() + "  expires " + permit.end());
        }
        body.add("");
        body.addAll(wrap("A permit cannot be extended. A move that will not be finished by "
                + "the last day needs a new permit."));
        return new Notice(to, "permits expiring", day, body);
    }
}
