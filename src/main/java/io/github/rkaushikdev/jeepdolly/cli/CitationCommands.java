package io.github.rkaushikdev.jeepdolly.cli;

import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.Unreadable;
import io.github.rkaushikdev.jeepdolly.Written;
import io.github.rkaushikdev.jeepdolly.enforcement.Citation;
import io.github.rkaushikdev.jeepdolly.enforcement.Offence;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.persistence.models.CitationRow;
import io.github.rkaushikdev.jeepdolly.reports.EnforcementReport;
import io.github.rkaushikdev.jeepdolly.services.Enforcement;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Citation commands, for the two people who never use the screen.
 *
 * <p>The clerk who types the post in uses the screen. The supervisor who writes the letters at the
 * end of the quarter does not, because the letters are a mail merge and a mail merge wants lines.
 *
 * <p>The chase list is the other one. It is printed on a Monday morning and worked down a
 * telephone, and it wants the carrier's name and the amount and nothing else on the line.
 *
 * <p>Filing from here exists for the mornings when the web process is down, which is the same
 * reason every other command group exists. It takes the number off the ticket rather than
 * allocating one, exactly as the route does.
 */
final class CitationCommands {

    /** Who a command line filing is recorded as having been done by. */
    static final String BY_HAND = "command line";

    private CitationCommands() {}

    /** One offence by the words it prints, refusing anything else. */
    static Offence offence(String written) {
        try {
            return Offence.of(written);
        } catch (IllegalArgumentException unknown) {
            throw new Unreadable(
                    Written.quoted(written) + " is not an offence this office prices", Facts.of("offence", written));
        }
    }

    @Command(
            name = "citations",
            description = "Citations: what is owed, what was written, and who to write to.",
            subcommands = {Owing.class, ForCarrier.class, Show.class, Quarter.class, Watch.class, File.class, Pay.class
            })
    static final class Citations {}

    @Command(name = "owing", description = "The chase list, oldest first, worked down a telephone.")
    static final class Owing extends Action {

        @Override
        public Integer call() {
            List<CitationRow> found;
            try (UnitOfWork work = work()) {
                found = work.citations().owing();
                for (CitationRow row : found) {
                    say(row.getNumber(), row.getWritten(), row.getCarrierName(), row.getAmount(), row.getState());
                }
            }
            if (found.isEmpty()) {
                say("nothing is owed");
            }
            return Main.DONE;
        }
    }

    @Command(name = "carrier", description = "Everything against one carrier, newest first.")
    static final class ForCarrier extends Action {

        @Parameters(paramLabel = "USDOT")
        private int usdot;

        @Override
        public Integer call() {
            try (UnitOfWork work = work()) {
                for (CitationRow row : work.citations().forCarrier(usdot)) {
                    say(row.getNumber(), row.getWritten(), row.getOffence(), row.getAmount(), row.getState());
                }
            }
            return Main.DONE;
        }
    }

    @Command(name = "show", description = "One citation, in the order it is read out over the telephone.")
    static final class Show extends Action {

        @Parameters(paramLabel = "NUMBER")
        private String number;

        @Override
        public Integer call() {
            try (UnitOfWork work = work()) {
                echo(work.citations().get(number).lines());
            }
            return Main.DONE;
        }
    }

    @Command(name = "report", description = "The quarter: counts by offence, the money, and who to write to.")
    static final class Quarter extends Action {

        @Option(names = "--from", required = true)
        private LocalDate start;

        @Option(names = "--to", required = true)
        private LocalDate end;

        @Override
        public Integer call() {
            try (UnitOfWork work = work()) {
                echo(EnforcementReport.report(work.entityManager(), start, end));
            }
            return Main.DONE;
        }
    }

    @Command(name = "watch", description = "The carriers that get a letter before they get a suspension.")
    static final class Watch extends Action {

        @Option(names = "--from", required = true)
        private LocalDate start;

        @Option(names = "--to", required = true)
        private LocalDate end;

        @Option(
                names = "--at",
                defaultValue = "" + EnforcementReport.WATCH_AT,
                description = "How many citations puts a carrier on the list.")
        private int at;

        @Override
        public Integer call() {
            List<EnforcementReport.Watched> found;
            try (UnitOfWork work = work()) {
                found = EnforcementReport.watchList(work.entityManager(), start, end, at);
                for (EnforcementReport.Watched one : found) {
                    say(one.usdot(), one.name(), one.count(), one.owing());
                }
            }
            if (found.isEmpty()) {
                say("nobody is over the line");
            }
            return Main.DONE;
        }
    }

    @Command(name = "file", description = "Put a ticket on file, priced against the carrier's year.")
    static final class File extends Action {

        @Parameters(index = "0", paramLabel = "NUMBER")
        private String number;

        @Parameters(index = "1", paramLabel = "USDOT")
        private int usdot;

        @Parameters(index = "2", paramLabel = "OFFENCE")
        private String offence;

        @Option(names = "--officer", required = true)
        private String officer;

        @Option(names = "--place", required = true)
        private String place;

        @Option(names = "--written", required = true)
        private LocalDate written;

        @Option(names = "--filed")
        private Optional<LocalDate> filed = Optional.empty();

        @Option(names = "--over", description = "Pounds over, for an overweight ticket.")
        private Optional<String> over = Optional.empty();

        @Option(names = "--permit")
        private Optional<String> permit = Optional.empty();

        @Override
        public Integer call() {
            Citation one;
            try (UnitOfWork work = work()) {
                one = Enforcement.fileCitation(
                        work,
                        number,
                        usdot,
                        officer,
                        offence(offence),
                        place,
                        written,
                        filed.orElse(written),
                        BY_HAND,
                        over.map(Pounds::parse),
                        permit,
                        "");
                work.commit();
            }
            say("filed", one.number(), one.carrierName(), one.offence(), one.amount());
            return Main.DONE;
        }
    }

    @Command(name = "pay", description = "Money arrived at the till and this is the paper catching up.")
    static final class Pay extends Action {

        @Parameters(paramLabel = "NUMBER")
        private String number;

        @Option(names = "--on")
        private Optional<LocalDate> on = Optional.empty();

        @Option(names = "--reference", defaultValue = "")
        private String reference;

        @Override
        public Integer call() {
            Citation one;
            try (UnitOfWork work = work()) {
                one = Enforcement.pay(work, number, on.orElseGet(LocalDate::now), BY_HAND, reference);
                work.commit();
            }
            say("paid", one.number(), one.amount());
            return Main.DONE;
        }
    }
}
