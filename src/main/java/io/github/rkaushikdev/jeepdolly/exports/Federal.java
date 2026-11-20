package io.github.rkaushikdev.jeepdolly.exports;

import io.github.rkaushikdev.jeepdolly.Dollars;
import io.github.rkaushikdev.jeepdolly.Unreadable;
import io.github.rkaushikdev.jeepdolly.permits.PermitState;
import io.github.rkaushikdev.jeepdolly.persistence.models.FeeLineRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.PermitRow;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The monthly return to the federal office, which is a fixed width file.
 *
 * <p>Every state sends one and the layout has not changed since 1994: eighty character records,
 * blank padded, in three kinds. One header, one detail record per permit issued in the month, and
 * one trailer carrying the counts and the totals. The receiving system reads it against a fixed
 * column layout and rejects the whole file on one bad column, so the rules here are about columns
 * rather than about permits.
 *
 * <p>Every field is a fixed slice and every record is exactly eighty characters. A value too long
 * for its field is truncated on the right, not wrapped and not rejected, because the federal office
 * would rather have a shortened carrier name than no return. Numbers are right justified and zero
 * filled; text is left justified and blank filled; money is whole dollars with no point and no
 * sign; dates are eight digits, year first.
 *
 * <p>The trailer is what gets checked: the record count and the sums of the money and weight
 * fields, which the receiving system recomputes. Both totals are built by adding up the detail
 * records actually written rather than by asking the database a second time. A voided permit is in
 * the return: it was issued, the money was taken, and the federal count is of permits issued rather
 * than of loads that moved.
 */
public final class Federal {

    /** Every record in the file is this wide, blank padded. */
    public static final int WIDTH = 80;

    /** What the receiving system calls this office. */
    public static final String STATE_CODE = "23";

    /** The layout version, which has not moved since 1994 and is sent anyway. */
    public static final String LAYOUT = "0003";

    private static final DateTimeFormatter EIGHT_DIGITS = DateTimeFormatter.ofPattern("uuuuMMdd", Locale.ROOT);

    private Federal() {}

    /**
     * Left justified, blank filled, truncated on the right.
     *
     * @throws IllegalArgumentException for a field with no width
     */
    public static String text(String value, int width) {
        if (width <= 0) {
            throw new IllegalArgumentException("a field has a width");
        }
        String cut = value.length() > width ? value.substring(0, width) : value;
        return cut + " ".repeat(width - cut.length());
    }

    /**
     * Right justified, zero filled, truncated on the left.
     *
     * <p>Truncating a number on the left is wrong in every sense except the one that matters: the
     * alternative is a record of the wrong length, and a file with one of those in it is rejected
     * in full. The widths are generous and it has never happened.
     *
     * @throws IllegalArgumentException for a field with no width, or a negative value
     */
    public static String number(long value, int width) {
        if (width <= 0) {
            throw new IllegalArgumentException("a field has a width");
        }
        if (value < 0) {
            throw new IllegalArgumentException("the federal layout carries no sign");
        }
        String digits = Long.toString(value);
        String padded = "0".repeat(Math.max(0, width - digits.length())) + digits;
        return padded.substring(padded.length() - width);
    }

    /** Eight digits, year first. */
    public static String day(LocalDate value) {
        return EIGHT_DIGITS.format(value);
    }

    /** Whole dollars, no point, no sign, eight columns. */
    public static String money(Dollars value) {
        return number(value.wholeDollars(), 8);
    }

    /** One permit as one eighty character record. */
    public record Detail(
            String number,
            LocalDate issued,
            int usdot,
            String carrier,
            String kind,
            int grossPounds,
            int miles,
            Dollars fee) {

        public String render() {
            return "D"
                    + text(number, 12)
                    + day(issued)
                    + Federal.number(usdot, 9)
                    + text(carrier, 24)
                    + text(kind, 1)
                    + Federal.number(grossPounds, 7)
                    + Federal.number(miles, 5)
                    + money(fee)
                    + " ".repeat(5);
        }
    }

    /** A whole month's return: header, detail and trailer. */
    public record Return(LocalDate start, LocalDate end, List<Detail> details) {

        public Return {
            details = List.copyOf(details);
        }

        public int count() {
            return details.size();
        }

        /** The fees of the details written, added up. */
        public Dollars fees() {
            return Dollars.total(details.stream().map(Detail::fee).toList());
        }

        /** The gross weights of the details written, added up. */
        public long pounds() {
            return details.stream().mapToLong(Detail::grossPounds).sum();
        }

        public String header() {
            return "H"
                    + STATE_CODE
                    + LAYOUT
                    + day(start)
                    + day(end)
                    + text("OVERSIZE OVERWEIGHT PERMIT RETURN", 40)
                    + " ".repeat(17);
        }

        public String trailer() {
            return "T" + number(count(), 7) + money(fees()) + number(pounds(), 12) + " ".repeat(52);
        }

        /** Every record, header first and trailer last. */
        public List<String> lines() {
            List<String> found = new ArrayList<>();
            found.add(header());
            details.forEach(one -> found.add(one.render()));
            found.add(trailer());
            return List.copyOf(found);
        }

        /** The file, each record on its own line, ending in a newline so it can be piped. */
        public String render() {
            return String.join("\n", lines()) + "\n";
        }

        @Override
        public String toString() {
            return start + " to " + end + ", " + count() + " permits";
        }
    }

    /**
     * One permit row as the federal office wants it.
     *
     * <p>The kind is one letter and it is the permit series, which is not the same thing as the
     * classification. The federal office asked for the series in 1994 and has never asked for
     * anything else. The mileage is whole miles, rounded down.
     */
    public static Detail detailOf(PermitRow row) {
        return new Detail(
                row.getNumber(),
                row.getIssued(),
                row.getUsdot(),
                row.getCarrierName(),
                row.getSeries(),
                row.getGross().value(),
                Math.floorDiv(row.getDistance().hundredths(), 100),
                Dollars.total(row.getFees().stream().map(FeeLineRow::getAmount).toList()));
    }

    /** Every permit issued in the window, voided ones included, in number order. */
    public static List<PermitRow> issuedBetween(EntityManager db, LocalDate start, LocalDate end) {
        return db.createQuery(
                        "select p from PermitRow p where p.issued >= :start and p.issued <= :end order by p.number",
                        PermitRow.class)
                .setParameter("start", start)
                .setParameter("end", end)
                .getResultList();
    }

    /** The whole return for a window, in permit number order. */
    public static Return build(EntityManager db, LocalDate start, LocalDate end) {
        return new Return(
                start,
                end,
                issuedBetween(db, start, end).stream().map(Federal::detailOf).toList());
    }

    /**
     * How many of the window's permits have since been replaced.
     *
     * <p>Not in the file. It goes in the covering letter, because the federal office rings up
     * about it every year and the answer used to be worked out by hand each time.
     */
    public static int supersededCount(EntityManager db, LocalDate start, LocalDate end) {
        return (int) issuedBetween(db, start, end).stream()
                .filter(one -> one.getState() == PermitState.SUPERSEDED)
                .count();
    }

    /**
     * One thing wrong with a file.
     *
     * @param record the line it is on, counting from one; zero for the file as a whole
     */
    public record Problem(int record, String trouble) {

        @Override
        public String toString() {
            return "record " + record + ": " + trouble;
        }
    }

    /**
     * Read a written return back and find what the federal office would find.
     *
     * <p>Everything it checks has actually been wrong at least once: a record of the wrong length
     * after somebody edited the file by hand, a trailer written before the last detail was added,
     * and a file assembled out of two months. It reads the file rather than the database on
     * purpose: a verification that asked the database again would agree with itself and prove
     * nothing. The totals are only recomputed once every record has the right shape.
     */
    public static List<Problem> verify(List<String> lines) {
        if (lines.isEmpty()) {
            return List.of(new Problem(0, "the file is empty"));
        }
        List<Problem> found = new ArrayList<>();
        for (int position = 1; position <= lines.size(); position++) {
            String line = lines.get(position - 1);
            if (line.length() != WIDTH) {
                found.add(new Problem(position, "is " + line.length() + " columns and not " + WIDTH));
            }
        }
        if (lines.get(0).charAt(0) != 'H') {
            found.add(new Problem(1, "is not a header"));
        }
        if (lines.get(lines.size() - 1).charAt(0) != 'T') {
            found.add(new Problem(lines.size(), "is not a trailer"));
        }
        List<String> body = lines.subList(1, Math.max(1, lines.size() - 1));
        for (int index = 0; index < body.size(); index++) {
            if (body.get(index).charAt(0) != 'D') {
                found.add(new Problem(index + 2, "is not a detail record"));
            }
        }
        if (!found.isEmpty()) {
            return List.copyOf(found);
        }
        return totals(lines, body);
    }

    /** Recompute the trailer the way the receiving system does. */
    private static List<Problem> totals(List<String> lines, List<String> body) {
        String trailer = lines.get(lines.size() - 1);
        int last = lines.size();
        List<Problem> found = new ArrayList<>();
        long count = Long.parseLong(trailer.substring(1, 8));
        if (count != body.size()) {
            found.add(new Problem(last, "says " + count + " details and the file has " + body.size()));
        }
        long fees = body.stream()
                .mapToLong(one -> Long.parseLong(one.substring(67, 75)))
                .sum();
        long stated = Long.parseLong(trailer.substring(8, 16));
        if (stated != fees) {
            found.add(new Problem(last, "says " + stated + " in fees and the details add to " + fees));
        }
        long pounds = body.stream()
                .mapToLong(one -> Long.parseLong(one.substring(55, 62)))
                .sum();
        long weighed = Long.parseLong(trailer.substring(16, 28));
        if (weighed != pounds) {
            found.add(new Problem(last, "says " + weighed + " pounds and the details add to " + pounds));
        }
        return List.copyOf(found);
    }

    /** The first and last day a return covers, both included. */
    public record Period(LocalDate start, LocalDate end) {}

    /**
     * The window a file says it covers, read off its header.
     *
     * @throws Unreadable when the file has no header
     */
    public static Period windowOf(List<String> lines) {
        if (lines.isEmpty() || lines.get(0).isEmpty() || lines.get(0).charAt(0) != 'H') {
            throw new Unreadable("this file has no header to read a window from");
        }
        String head = lines.get(0);
        return new Period(
                LocalDate.parse(head.substring(7, 11) + "-" + head.substring(11, 13) + "-" + head.substring(13, 15)),
                LocalDate.parse(head.substring(15, 19) + "-" + head.substring(19, 21) + "-" + head.substring(21, 23)));
    }
}
