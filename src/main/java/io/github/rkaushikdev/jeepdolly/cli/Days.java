package io.github.rkaushikdev.jeepdolly.cli;

import io.github.rkaushikdev.jeepdolly.Written;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

/**
 * A day on the command line, written year, month and day with hyphens between: {@code 2025-04-08}.
 *
 * <p>The year is four digits. The month and the day may drop their leading zero, and the day may
 * stand a space in place of it, so {@code 2025-4-8} and {@code 2025-04- 8} are the same day as
 * {@code 2025-04-08}. Nothing else is read: no time, no slashes, no words.
 */
final class Days implements ITypeConverter<LocalDate> {

    /** The shape the operator is told to use when a day cannot be read. */
    public static final String FORMAT = "%Y-%m-%d";

    private static final Pattern DAY = Pattern.compile(
            "(\\d\\d\\d\\d)-(1[0-2]|0[1-9]|[1-9])-(3[01]|[12]\\d|0[1-9]|[1-9]| [1-9])",
            Pattern.UNICODE_CHARACTER_CLASS);

    @Override
    public LocalDate convert(String text) {
        return read(text);
    }

    /**
     * Read a day.
     *
     * @throws TypeConversionException when the text is not a day in the calendar written that way
     */
    public static LocalDate read(String text) {
        Matcher found = DAY.matcher(text);
        if (found.matches()) {
            int year = Integer.parseInt(found.group(1));
            int month = Integer.parseInt(found.group(2));
            int day = Integer.parseInt(found.group(3).strip());
            if (year >= 1 && day <= YearMonth.of(year, month).lengthOfMonth()) {
                return LocalDate.of(year, month, day);
            }
        }
        throw new TypeConversionException(Written.quoted(text) + " does not match the format '" + FORMAT + "'.");
    }
}
