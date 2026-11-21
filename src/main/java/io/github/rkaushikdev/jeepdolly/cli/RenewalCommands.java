package io.github.rkaushikdev.jeepdolly.cli;

import io.github.rkaushikdev.jeepdolly.api.Deps;
import io.github.rkaushikdev.jeepdolly.permits.Permit;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.services.Renewals;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Renewal commands, which is where the letters actually come from.
 *
 * <p>The letters go out as a mail merge. Somebody runs this on a Monday, pastes the output into a
 * spreadsheet, and the spreadsheet feeds a word processor. That is why the letter command prints one
 * line per carrier with the numbers run together in a field rather than one line per permit: the
 * merge wants a row per envelope.
 *
 * <p>The due list is the other shape and prints one line per permit, because that one goes on the
 * wall and gets ticked off.
 *
 * <p>Renewing from the command line exists for the same reason every other command does, and it is
 * used more than the others: the renewal fortnight is in February, the counter is busy, and the
 * supervisor works the list down from a terminal in the back office.
 */
final class RenewalCommands {

    /** What separates the permit numbers in a merge row. */
    static final String JOIN = ";";

    private static final String WITHIN_HELP = "Days ahead to look.";

    private RenewalCommands() {}

    @Command(
            name = "renewal",
            description = "Annual permits running out, and the letters about them.",
            subcommands = {Due.class, Letters.class, Renew.class})
    static final class Renewal {}

    @Command(name = "due", description = "Every annual running out inside the window, soonest first.")
    static final class Due extends Action {

        @Option(names = "--day")
        private Optional<LocalDate> day = Optional.empty();

        @Option(names = "--within", defaultValue = "" + Renewals.AHEAD_DAYS, description = WITHIN_HELP)
        private int within;

        @Override
        public Integer call() {
            LocalDate when = day.orElseGet(LocalDate::now);
            List<Renewals.Due> found;
            try (UnitOfWork work = work()) {
                found = Renewals.dueBy(work, when, within);
                for (Renewals.Due one : found) {
                    say(one.number(), one.expires(), one.daysLeft(when), one.carrier());
                }
            }
            if (found.isEmpty()) {
                say("nothing is due in that window");
            }
            return Main.DONE;
        }
    }

    @Command(name = "letters", description = "One row per envelope, or the letters themselves.")
    static final class Letters extends Action {

        @Option(names = "--day")
        private Optional<LocalDate> day = Optional.empty();

        @Option(names = "--within", defaultValue = "" + Renewals.AHEAD_DAYS, description = WITHIN_HELP)
        private int within;

        @Option(names = "--long", description = "Print the letter rather than a row.")
        private boolean longForm;

        @Override
        public Integer call() {
            LocalDate when = day.orElseGet(LocalDate::now);
            List<Renewals.Letter> found;
            try (UnitOfWork work = work()) {
                found = Renewals.letters(work, when, within);
            }
            if (found.isEmpty()) {
                say("nobody is due a letter");
                return Main.DONE;
            }
            for (Renewals.Letter one : found) {
                if (longForm) {
                    echo(one.lines());
                    continue;
                }
                String numbers = one.due().stream().map(Renewals.Due::number).collect(Collectors.joining(JOIN));
                say(one.usdot(), one.carrier(), one.count(), one.soonest(), numbers);
            }
            return Main.DONE;
        }
    }

    @Command(name = "renew", description = "Renew a carrier's annuals in one transaction, or none of them.")
    static final class Renew extends Action {

        @Parameters(index = "0", paramLabel = "USDOT")
        private int usdot;

        @Parameters(index = "1..*", arity = "1..*", paramLabel = "NUMBERS")
        private List<String> numbers;

        @Option(names = "--on")
        private Optional<LocalDate> on = Optional.empty();

        @Override
        public Integer call() {
            LocalDate when = on.orElseGet(LocalDate::now);
            Renewals.Renewed found;
            try (UnitOfWork work = work()) {
                found = Renewals.renew(work, Deps.officeFrom(work, when), usdot, numbers, when);
                work.commit();
                for (Permit made : found.issued()) {
                    say("renewed", made.number(), made.end());
                }
            }
            say(found.count(), "renewed for", usdot);
            return Main.DONE;
        }
    }
}
