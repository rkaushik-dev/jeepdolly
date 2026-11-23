package io.github.rkaushikdev.jeepdolly.domain;

import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.Unreadable;
import io.github.rkaushikdev.jeepdolly.Written;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A tire as the sidewall names it, and the width of rubber the tire rule is allowed to count.
 *
 * <p>Haulers do not measure their tires. They copy the size off the sidewall, so the application
 * says {@code 11R22.5} or {@code 425/65R22.5} far more often than it says eleven inches. The first is
 * an inch size and the width is the number before the R. The second is metric and the width is the
 * section width in millimetres, the number before the slash.
 *
 * <p>Either way the width is read down to the whole inch. The tire rule multiplies the width by a
 * rate per inch, and an inch the office rounds up is a thousand pounds the pavement was never asked
 * about. A 425 is sixteen and three quarter inches of rubber and the permit counts sixteen.
 *
 * @param designation the size as the sidewall prints it, upper cased
 * @param width the section width the tire rule counts, in whole inches
 */
public record TireSize(String designation, Inches width) {

    /** Millimetres in an inch, which is exact by definition. */
    private static final BigDecimal MILLIMETRES_PER_INCH = new BigDecimal("25.4");

    /** A metric size: section width, aspect ratio, and the rim: {@code 445/50R22.5}. */
    private static final Pattern METRIC =
            Pattern.compile("^(?<section>\\d{3})\\s*/\\s*(?<aspect>\\d{2})\\s*R?\\s*(?<rim>\\d{2}(?:\\.5)?)$");

    /** An inch size, radial or bias: {@code 11R22.5}, {@code 11.00-20}, {@code 15R22.5}. */
    private static final Pattern INCH =
            Pattern.compile("^(?<section>\\d{1,2}(?:\\.\\d{1,2})?)\\s*(?:R|-)\\s*(?<rim>\\d{2}(?:\\.5)?)$");

    /**
     * Read a sidewall size.
     *
     * @throws Unreadable when the text is not a tire size in either shape
     */
    public static TireSize parse(String text) {
        return read(text)
                .orElseThrow(() -> new Unreadable(
                        "cannot read " + Written.quoted(text) + " as a tire size", Facts.of("written", text)));
    }

    /**
     * The width of rubber written on an application: a sidewall size, or a plain size in any shape
     * {@link Inches#parse} reads.
     *
     * @throws Unreadable when it is neither
     */
    public static Inches widthOf(String text) {
        return read(text).map(TireSize::width).orElseGet(() -> Inches.parse(text));
    }

    private static Optional<TireSize> read(String text) {
        String cleaned = text.strip().toUpperCase(Locale.ROOT);
        Matcher metric = METRIC.matcher(cleaned);
        if (metric.matches()) {
            BigDecimal inches =
                    new BigDecimal(metric.group("section")).divide(MILLIMETRES_PER_INCH, 0, RoundingMode.FLOOR);
            return Optional.of(new TireSize(cleaned, new Inches(inches.intValueExact())));
        }
        Matcher inch = INCH.matcher(cleaned);
        if (inch.matches()) {
            BigDecimal inches = new BigDecimal(inch.group("section")).setScale(0, RoundingMode.FLOOR);
            return Optional.of(new TireSize(cleaned, new Inches(inches.intValueExact())));
        }
        return Optional.empty();
    }

    /** The size as the sidewall prints it. */
    @Override
    public String toString() {
        return designation;
    }
}
