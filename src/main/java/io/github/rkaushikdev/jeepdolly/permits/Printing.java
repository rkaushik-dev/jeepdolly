package io.github.rkaushikdev.jeepdolly.permits;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.conditions.Condition;
import io.github.rkaushikdev.jeepdolly.fees.FeeLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The printed permit, which is the only artefact anybody outside the office sees.
 *
 * <p>It goes on one sheet of paper, folded once, and lives on the dash of a truck for a week. So it
 * is eighty columns wide, it has no colour, and the things an enforcement officer looks for are in
 * the first six lines: the number, the carrier, the dates, the size and weight, and where it may go.
 *
 * <p>The conditions come after that, numbered, in the order the office prints them. Numbering
 * matters: an officer at the roadside says condition four, and the hauler has to be able to find
 * condition four.
 *
 * <p>The permit is printed from the permit record alone. It does not read the carrier table, the
 * road log or the fee schedule, because the paper has to say what was decided on the day it was
 * written, and everything else has moved on.
 *
 * <p>A duplicate is the same sheet with a copy number on it. The copy number is passed in rather
 * than read off the permit, because the permit does not know how many times it has been printed and
 * should not: the reprint register knows, and it is a list beside the permit rather than a field on
 * it.
 */
public final class Printing {

    /** The paper is eighty columns wide and nothing on it is wider. */
    public static final int WIDTH = 80;

    /** The heading of every sheet. */
    public static final String OFFICE = "MOTOR CARRIER PERMIT OFFICE";

    private Printing() {}

    /** A rule of equals signs across the full width. */
    public static String rule() {
        return rule('=');
    }

    /** A rule of one character across the full width. */
    public static String rule(char character) {
        return String.valueOf(character).repeat(WIDTH);
    }

    /** The text in the middle of the line, with nothing after it; text too wide is left alone. */
    public static String centred(String text) {
        if (text.length() >= WIDTH) {
            return text;
        }
        int spare = WIDTH - text.length();
        return " ".repeat(spare / 2) + text;
    }

    /** Two fields on one line, the second one right against the margin. */
    public static String twoUp(String left, String right) {
        if (left.length() + right.length() + 2 > WIDTH) {
            return left + "  " + right;
        }
        return left + " ".repeat(WIDTH - left.length() - right.length()) + right;
    }

    /** Text broken into lines that fit the paper four columns in. */
    public static List<String> wrapped(String text) {
        return wrapped(text, 4);
    }

    /** Text broken into lines that fit the paper {@code indent} columns in. */
    public static List<String> wrapped(String text, int indent) {
        return wrapTo(text, WIDTH - indent);
    }

    /**
     * Break text at spaces into lines no wider than {@code width}, never mid word. A word longer
     * than the line is left whole on a line of its own rather than cut. The letters break their
     * sentences the same way.
     */
    public static List<String> wrapTo(String text, int width) {
        List<String> lines = new ArrayList<>();
        String standing = "";
        for (String word : words(text)) {
            if (standing.isEmpty()) {
                standing = word;
            } else if (standing.length() + 1 + word.length() <= width) {
                standing = standing + " " + word;
            } else {
                lines.add(standing);
                standing = word;
            }
        }
        if (!standing.isEmpty()) {
            lines.add(standing);
        }
        return List.copyOf(lines);
    }

    private static List<String> words(String text) {
        String trimmed = text.strip();
        return trimmed.isEmpty() ? List.of() : List.of(trimmed.split("\\s+"));
    }

    /** The original sheet, as lines, ready for a line printer. */
    public static List<String> printed(Permit permit) {
        return printed(permit, 1);
    }

    /**
     * The whole sheet, as lines, ready for a line printer.
     *
     * <p>A copy number above one puts a banner under the office name and another across the foot.
     * Two banners on one sheet is deliberate: the top one is what a clerk sees on the pile and the
     * bottom one is what survives when somebody folds the sheet to put it behind a windscreen.
     *
     * @throws Impossible when the permit carries no conditions
     */
    public static List<String> printed(Permit permit, int copy) {
        if (permit.conditions().isEmpty()) {
            throw new Impossible(permit.number() + " has no conditions and cannot be printed");
        }
        List<String> lines = new ArrayList<>();
        lines.add(rule());
        lines.add(centred(OFFICE));
        lines.add(centred("OVERSIZE AND OVERWEIGHT PERMIT"));
        if (copy > 1) {
            lines.add(centred(banner(copy)));
        }
        lines.add(rule());
        lines.add(twoUp("PERMIT " + permit.number(), "ISSUED " + permit.issued()));
        lines.add(twoUp(
                permit.carrierName() + " (" + permit.usdot() + ")", "VALID " + permit.start() + " TO " + permit.end()));
        lines.add(rule('-'));
        lines.add(twoUp("GROSS " + permit.gross(), "DISTANCE " + permit.distance()));
        lines.add("SIZE  " + permit.envelope());
        lines.add(rule('-'));
        lines.add("ROUTE");
        for (String one : wrapped(permit.narrative())) {
            lines.add("    " + one);
        }
        lines.add(rule('-'));
        lines.add("CONDITIONS");
        int number = 1;
        for (Condition condition : permit.conditions()) {
            List<String> body = wrapped(condition.text(), 8);
            lines.add(String.format(Locale.ROOT, "  %2d. %s", number, body.get(0)));
            for (String one : body.subList(1, body.size())) {
                lines.add("      " + one);
            }
            number++;
        }
        lines.add(rule('-'));
        lines.add("FEES");
        for (FeeLine line : permit.quote().lines()) {
            lines.add(twoUp("    " + line.description(), line.amount().toString()));
        }
        lines.add(twoUp("    TOTAL", permit.quote().total().toString()));
        lines.add(rule());
        if (copy > 1) {
            lines.add(centred(banner(copy)));
            lines.add(rule());
        }
        if (permit.state() != PermitState.ISSUED) {
            lines.add(centred("*** " + permit.state().value().toUpperCase(Locale.ROOT) + " ***"));
            lines.add(rule());
        }
        return List.copyOf(lines);
    }

    private static String banner(int copy) {
        return "*** DUPLICATE - COPY " + copy + " ***";
    }

    /** The original printed permit as one piece of text, ending in a newline. */
    public static String sheet(Permit permit) {
        return sheet(permit, 1);
    }

    /** The printed permit as one piece of text, ending in a newline so it can be piped. */
    public static String sheet(Permit permit, int copy) {
        return String.join("\n", printed(permit, copy)) + "\n";
    }

    /** The longest line of the original sheet, which has to fit the paper. */
    public static int widest(Permit permit) {
        return widest(permit, 1);
    }

    /** The longest line, which has to fit the paper. */
    public static int widest(Permit permit, int copy) {
        return printed(permit, copy).stream().mapToInt(String::length).max().orElse(0);
    }
}
