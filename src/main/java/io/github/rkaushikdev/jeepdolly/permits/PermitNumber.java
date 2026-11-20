package io.github.rkaushikdev.jeepdolly.permits;

import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Unreadable;
import io.github.rkaushikdev.jeepdolly.Written;
import java.util.Comparator;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One issued number, which never changes and is never reused.
 *
 * <p>A permit number is quoted on the phone, written on a receipt, read off a fax by an enforcement
 * officer at a weigh station, and reconciled against the bank at the end of the month. It is short
 * enough to say out loud: two digits of year, one letter of series, six digits of sequence. It
 * carries the year, because the sequence starts again in January, and the series, because the
 * office wants to know which document it is holding before it looks anything up.
 *
 * <p>Numbers sort by year, then by series letter, then by sequence.
 */
public record PermitNumber(int year, Series series, int sequence) implements Comparable<PermitNumber> {

    /** The sequence starts here so that a six digit number is always six digits. */
    public static final int FIRST_SEQUENCE = 1;

    /** Nothing this office issues will reach this in a year. */
    public static final int LAST_SEQUENCE = 999999;

    private static final Pattern NUMBER = Pattern.compile("^(?<year>\\d{2})-(?<series>[A-Z])-(?<sequence>\\d{6})$");

    private static final Comparator<PermitNumber> ORDER = Comparator.comparingInt(PermitNumber::year)
            .thenComparing(one -> one.series().value())
            .thenComparingInt(PermitNumber::sequence);

    public PermitNumber {
        if (year < 2000 || year > 2099) {
            throw new Impossible(year + " is outside the century this office numbers in");
        }
        if (sequence < FIRST_SEQUENCE || sequence > LAST_SEQUENCE) {
            throw new Impossible(sequence + " is not a sequence number");
        }
    }

    /**
     * Read a number back from what was written on the fax: {@code 25-P-004471}.
     *
     * @throws Unreadable when the text is not a number in that shape
     * @throws IllegalArgumentException when the letter is not one of the office's series
     */
    public static PermitNumber parse(String text) {
        Matcher found = NUMBER.matcher(text.strip());
        if (!found.matches()) {
            throw new Unreadable(
                    "cannot read " + Written.quoted(text) + " as a permit number", Facts.of("written", text));
        }
        return new PermitNumber(
                2000 + Integer.parseInt(found.group("year")),
                Series.of(found.group("series")),
                Integer.parseInt(found.group("sequence")));
    }

    @Override
    public int compareTo(PermitNumber other) {
        return ORDER.compare(this, other);
    }

    /** Short enough to read over the telephone: {@code 25-P-004471}. */
    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%02d-%s-%06d", year % 100, series, sequence);
    }

    /** The number as it would be written in code, for a debugging line. */
    public String repr() {
        return "PermitNumber(" + this + ")";
    }
}
