package io.github.rkaushikdev.jeepdolly.cli;

import io.github.rkaushikdev.jeepdolly.Written;
import io.github.rkaushikdev.jeepdolly.conditions.Condition;
import io.github.rkaushikdev.jeepdolly.fees.FeeLine;
import io.github.rkaushikdev.jeepdolly.permits.Permit;
import io.github.rkaushikdev.jeepdolly.permits.PermitState;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.persistence.models.PermitRow;
import io.github.rkaushikdev.jeepdolly.services.Audit;
import java.time.LocalDate;
import java.util.Optional;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Permit commands, for the mornings when the screen is down.
 *
 * <p>Every command here has an equivalent route on the interface and that is not duplication to be
 * tidied away. The counter uses the screen. The traffic office at six in the morning uses this, over
 * a telephone line, because the screen is served from a building that is not open yet.
 *
 * <p>So these are not a thin wrapper over an http client. They go at the database directly through
 * the same unit of work, which means they work when the web process is down, which is the point of
 * them.
 *
 * <p>Printing is one record to a line. The show command is the exception and it prints a block,
 * because a permit read out over the telephone is read in the order it prints on the paper.
 */
final class PermitCommands {

    private PermitCommands() {}

    @Command(
            name = "permits",
            description = "Permits: what is out, what is expiring, and what to kill.",
            subcommands = {OnTheRoad.class, Expiring.class, Show.class, MakeVoid.class, ForCarrier.class})
    static final class Permits {}

    @Command(name = "on-the-road", description = "Every permit valid for travel on a day.")
    static final class OnTheRoad extends Action {

        @Option(names = "--day")
        private Optional<LocalDate> day = Optional.empty();

        @Override
        public Integer call() {
            LocalDate when = day.orElseGet(LocalDate::now);
            try (UnitOfWork work = work()) {
                for (PermitRow row : work.permits().onTheRoad(when)) {
                    say(row.getNumber(), row.getCarrierName(), row.getGross(), row.getOrigin(), row.getDestination());
                }
            }
            return Main.DONE;
        }
    }

    @Command(name = "expiring", description = "What the office rings about on a Friday afternoon.")
    static final class Expiring extends Action {

        @Option(names = "--day")
        private Optional<LocalDate> day = Optional.empty();

        @Option(names = "--within", defaultValue = "2", description = "Days ahead to look.")
        private int within;

        @Override
        public Integer call() {
            LocalDate when = day.orElseGet(LocalDate::now);
            try (UnitOfWork work = work()) {
                for (PermitRow row : work.permits().expiring(when, when.plusDays(within))) {
                    say(row.getNumber(), row.getValidTo(), row.getCarrierName(), row.getDestination());
                }
            }
            return Main.DONE;
        }
    }

    @Command(name = "show", description = "One permit, in the order it prints on the paper.")
    static final class Show extends Action {

        @Parameters(paramLabel = "NUMBER")
        private String number;

        @Override
        public Integer call() {
            try (UnitOfWork work = work()) {
                Permit permit = work.permits().get(number);
                echo(Written.lines(permit.face()));
                echo(permit.narrative());
                for (Condition condition : permit.conditions()) {
                    say(condition.code(), condition.text());
                }
                for (FeeLine line : permit.quote().lines()) {
                    say(line.description(), line.amount());
                }
                say("total", permit.quote().total());
            }
            return Main.DONE;
        }
    }

    @Command(name = "void", description = "Kill a permit, with the reason on the record.")
    static final class MakeVoid extends Action {

        @Parameters(paramLabel = "NUMBER")
        private String number;

        @Option(names = "--reason", required = true)
        private String reason;

        @Option(names = "--by", required = true, description = "Who is voiding it.")
        private String by;

        @Option(names = "--day")
        private Optional<LocalDate> day = Optional.empty();

        @Override
        public Integer call() {
            LocalDate when = day.orElseGet(LocalDate::now);
            try (UnitOfWork work = work()) {
                Permit permit = work.permits().get(number);
                permit.makeVoid(when, reason);
                work.permits().saveState(permit);
                Audit.record(work.entityManager(), "permit.voided", by, "permit", number, reason);
                work.commit();
            }
            say(number, "void", reason);
            return Main.DONE;
        }
    }

    @Command(name = "carrier", description = "What one carrier has out, newest first.")
    static final class ForCarrier extends Action {

        @Parameters(paramLabel = "USDOT")
        private int usdot;

        @Override
        public Integer call() {
            try (UnitOfWork work = work()) {
                for (PermitRow row : work.permits().forCarrier(usdot)) {
                    String state = row.getState() == PermitState.ISSUED
                            ? ""
                            : row.getState().value();
                    say(row.getNumber(), row.getIssued(), row.getValidTo(), state);
                }
            }
            return Main.DONE;
        }
    }
}
