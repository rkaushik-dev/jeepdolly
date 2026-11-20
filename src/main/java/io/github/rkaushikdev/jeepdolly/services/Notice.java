package io.github.rkaushikdev.jeepdolly.services;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * One letter, ready to fax.
 *
 * <p>A notice is plain text with a fixed shape: who it is to, what it is about, the body, and a line
 * saying who to ring. It is faxed as often as it is emailed, which is why there is no markup in it.
 *
 * @param about what the letter is about, as the subject line reads
 * @param day the day the letter is dated
 * @param body the lines between the subject and the telephone line
 */
public record Notice(String to, String about, LocalDate day, List<String> body) {

    public Notice {
        body = List.copyOf(body);
    }

    /** Every line of the letter: the office, the date, who to, what about, the body, who to ring. */
    public List<String> lines() {
        List<String> found =
                new ArrayList<>(List.of(Notices.OFFICE, day.toString(), "", "To: " + to, "Re: " + about, ""));
        found.addAll(body);
        found.add("");
        found.add("Questions to the permit office on " + Notices.TELEPHONE + ".");
        return List.copyOf(found);
    }

    /** The letter as one piece of text. */
    public String text() {
        return String.join("\n", lines());
    }

    /** The longest line, which has to survive a fax. */
    public int widest() {
        return lines().stream().mapToInt(String::length).max().orElse(0);
    }

    /** As the out tray lists it: {@code permit 25-P-004471 to Bickford Heavy Haul}. */
    @Override
    public String toString() {
        return about + " to " + to;
    }
}
