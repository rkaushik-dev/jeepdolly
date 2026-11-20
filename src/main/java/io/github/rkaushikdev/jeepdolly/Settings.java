package io.github.rkaushikdev.jeepdolly;

import io.github.rkaushikdev.jeepdolly.enforcement.Standing;
import io.github.rkaushikdev.jeepdolly.permits.Reprint;
import io.github.rkaushikdev.jeepdolly.persistence.Database;
import io.github.rkaushikdev.jeepdolly.reports.LineSurvey;
import io.github.rkaushikdev.jeepdolly.services.Renewals;
import java.util.Locale;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/**
 * Everything the service is allowed to be told, read once, at the edge, with defaults that are safe.
 *
 * <p>Everything configurable is here and nothing anywhere else reads the environment. That is worth
 * enforcing rather than intending: a service that reads an environment variable in the middle of a
 * request is a service whose behaviour cannot be reproduced from its arguments. So the reading takes
 * its lookup as an argument ({@link #from}), and a test hands it a map.
 *
 * <p>The defaults are the ones that are safe to be wrong. The database defaults to a file in the
 * working directory rather than a server, so a mistyped environment cannot point a developer at
 * production. Nothing here is a secret: when there is a password it arrives inside the database
 * URL, and {@link #safeUrl()} takes it out for the log line.
 *
 * @param databaseUrl the JDBC URL of the database
 * @param minimumMiles the shortest distance a permit is priced on
 * @param reviewDays how many days the office promises for an engineering study
 * @param echoSql whether every statement is written to the log
 * @param rules the numbers the office changes without shipping anything
 */
public record Settings(
        String databaseUrl, String officeName, int minimumMiles, int reviewDays, boolean echoSql, OfficeRules rules) {

    /** What every setting's environment variable starts with. */
    public static final String PREFIX = "JEEPDOLLY_";

    /** A file in the working directory. */
    public static final String DEFAULT_URL = Database.DEFAULT_URL;

    public static final String DEFAULT_OFFICE = "Motor Carrier Permit Office";

    /** The shortest distance a permit is priced on, as the published schedule says. */
    public static final int DEFAULT_MINIMUM_MILES = 10;

    public static final int DEFAULT_REVIEW_DAYS = 14;

    private static final Pattern PASSWORD = Pattern.compile("//([^:/@]+):([^@]+)@");

    public Settings {
        if (databaseUrl.isBlank()) {
            throw new Impossible("a database url is not optional");
        }
        if (minimumMiles < 0) {
            throw new Impossible("a minimum mileage is not negative");
        }
        if (reviewDays < 1) {
            throw new Impossible("a review turnaround is at least a day");
        }
    }

    /** The settings when nothing is said at all. */
    public static Settings defaults() {
        return new Settings(
                DEFAULT_URL, DEFAULT_OFFICE, DEFAULT_MINIMUM_MILES, DEFAULT_REVIEW_DAYS, false, OfficeRules.defaults());
    }

    /** Build the settings once, at the edge, from the process environment. */
    public static Settings fromEnvironment() {
        return from(System::getenv);
    }

    /** Build the settings from a map standing in for the environment. */
    public static Settings fromEnvironment(Map<String, String> environment) {
        return from(environment::get);
    }

    /**
     * Build the settings from a lookup of environment variable names, which answers null for a
     * variable that is not set.
     *
     * @throws Impossible when a number or a flag is written in a way that cannot be read
     */
    public static Settings from(UnaryOperator<String> environment) {
        Reader reader = new Reader(environment);
        return new Settings(
                reader.text("DATABASE_URL", DEFAULT_URL),
                reader.text("OFFICE_NAME", DEFAULT_OFFICE),
                reader.number("MINIMUM_MILES", DEFAULT_MINIMUM_MILES),
                reader.number("REVIEW_DAYS", DEFAULT_REVIEW_DAYS),
                reader.flag("ECHO_SQL", false),
                new OfficeRules(
                        reader.number("FREE_COPIES", Reprint.FREELY),
                        reader.number("RENEWAL_DAYS", Renewals.AHEAD_DAYS),
                        reader.number("CITATION_GRACE", Standing.GRACE_DAYS),
                        reader.number("LINE_SURVEY_YEARS", LineSurvey.CYCLE_YEARS)));
    }

    public Settings withDatabase(String url) {
        return new Settings(url, officeName, minimumMiles, reviewDays, echoSql, rules);
    }

    public Settings withOfficeName(String name) {
        return new Settings(databaseUrl, name, minimumMiles, reviewDays, echoSql, rules);
    }

    public Settings withMinimumMiles(int miles) {
        return new Settings(databaseUrl, officeName, miles, reviewDays, echoSql, rules);
    }

    public Settings withReviewDays(int days) {
        return new Settings(databaseUrl, officeName, minimumMiles, days, echoSql, rules);
    }

    public Settings withEchoSql(boolean echo) {
        return new Settings(databaseUrl, officeName, minimumMiles, reviewDays, echo, rules);
    }

    public Settings withRules(OfficeRules office) {
        return new Settings(databaseUrl, officeName, minimumMiles, reviewDays, echoSql, office);
    }

    /** The URL with any password taken out, for the log line. */
    public String safeUrl() {
        return PASSWORD.matcher(databaseUrl).replaceAll("//$1:***@");
    }

    /** Whether the database lives in memory and vanishes when the process ends. */
    public boolean inMemory() {
        return databaseUrl.contains(":mem:");
    }

    /** The office and the database it is on, with no password in it. */
    @Override
    public String toString() {
        return officeName + " on " + safeUrl();
    }

    /** Reads one setting at a time, falling back to its default when the variable is not set. */
    private record Reader(UnaryOperator<String> environment) {

        String text(String name, String fallback) {
            String written = environment.apply(PREFIX + name);
            return written == null ? fallback : written;
        }

        int number(String name, int fallback) {
            String written = text(name, String.valueOf(fallback));
            String digits = written.startsWith("-") ? written.replaceFirst("^-+", "") : written;
            if (!Written.isDigits(digits)) {
                throw new Impossible(PREFIX + name + " is a whole number, not " + Written.quoted(written));
            }
            return Integer.parseInt(written);
        }

        boolean flag(String name, boolean fallback) {
            String written = text(name, fallback ? "yes" : "no").strip().toLowerCase(Locale.ROOT);
            return switch (written) {
                case "yes", "true", "on", "1" -> true;
                case "no", "false", "off", "0" -> false;
                default -> throw new Impossible(PREFIX + name + " is yes or no, not " + Written.quoted(written));
            };
        }
    }
}
