package io.github.rkaushikdev.jeepdolly.cli;

import io.github.rkaushikdev.jeepdolly.Dollars;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.reports.IssuanceReport;
import io.github.rkaushikdev.jeepdolly.reports.RevenueReport;
import io.github.rkaushikdev.jeepdolly.reports.RoadCalendar;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.Optional;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Report commands, which print exactly what the paper report prints.
 *
 * <p>Each of these is a thin call onto a report in the reports package, and the lines it prints are
 * the lines the report produced. The command does not reformat, does not add a total, and does not
 * sort. If a report is wrong on paper it is wrong in the report, which is where the test that holds
 * it lives.
 *
 * <p>The evening report is run from cron at seven, redirected to a file, and printed in the morning.
 * That is why it writes to standard output with nothing on standard error: a cron job that writes a
 * heading to stderr sends a mail every night and gets filtered within a fortnight.
 */
final class ReportCommands {

    private ReportCommands() {}

    @Command(
            name = "report",
            description = "The reports the office lives on.",
            subcommands = {Issuance.class, Revenue.class, ForCarrier.class, Roads.class, Standing.class})
    static final class Report {}

    @Command(name = "issuance", description = "What was written and what it took.")
    static final class Issuance extends Action {

        @Option(names = "--day")
        private Optional<LocalDate> day = Optional.empty();

        @Option(names = "--to", description = "The last day of a span.")
        private Optional<LocalDate> until = Optional.empty();

        @Override
        public Integer call() {
            LocalDate start = day.orElseGet(LocalDate::now);
            try (UnitOfWork work = work()) {
                EntityManager db = work.entityManager();
                IssuanceReport.Issuance found = until.map(end -> IssuanceReport.issuance(db, start, end))
                        .orElseGet(() -> IssuanceReport.issuance(db, start));
                echo(found.lines());
            }
            return Main.DONE;
        }
    }

    @Command(name = "revenue", description = "Where the money came from.")
    static final class Revenue extends Action {

        @Option(names = "--from", required = true)
        private LocalDate start;

        @Option(names = "--to", required = true)
        private LocalDate until;

        @Override
        public Integer call() {
            try (UnitOfWork work = work()) {
                echo(RevenueReport.revenue(work.entityManager(), start, until).lines());
            }
            return Main.DONE;
        }
    }

    @Command(name = "carrier", description = "What one carrier was charged, for its own reconciliation.")
    static final class ForCarrier extends Action {

        @Parameters(paramLabel = "NAME")
        private String name;

        @Option(names = "--from", required = true)
        private LocalDate start;

        @Option(names = "--to", required = true)
        private LocalDate until;

        @Override
        public Integer call() {
            RevenueReport.Revenue found;
            try (UnitOfWork work = work()) {
                found = RevenueReport.revenue(work.entityManager(), start, until);
            }
            say(name, found.byCarrier().getOrDefault(name, Dollars.ZERO));
            return Main.DONE;
        }
    }

    @Command(name = "roads", description = "What changes on the road over the next fortnight.")
    static final class Roads extends Action {

        @Option(names = "--day")
        private Optional<LocalDate> day = Optional.empty();

        @Option(names = "--days", defaultValue = "" + RoadCalendar.FORTNIGHT)
        private int days;

        @Override
        public Integer call() {
            try (UnitOfWork work = work()) {
                echo(RoadCalendar.changes(work.entityManager(), day.orElseGet(LocalDate::now), days));
            }
            return Main.DONE;
        }
    }

    @Command(name = "standing", description = "Everything in force on one day, for the fax to the state police.")
    static final class Standing extends Action {

        @Option(names = "--day")
        private Optional<LocalDate> day = Optional.empty();

        @Override
        public Integer call() {
            try (UnitOfWork work = work()) {
                for (RoadCalendar.Standing one :
                        RoadCalendar.standingOn(work.entityManager(), day.orElseGet(LocalDate::now))) {
                    echo(one.toString());
                }
            }
            return Main.DONE;
        }
    }
}
