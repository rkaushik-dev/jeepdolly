package io.github.rkaushikdev.jeepdolly.cli;

import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.Logs;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.services.Nightly;
import java.time.LocalDate;
import java.util.Optional;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * The nightly command, which is the only one cron runs.
 *
 * <p>It commits, which none of the other commands do without being told to, and it says so in the
 * help, because a command run unattended that silently changes records is a command somebody will
 * run by hand to see what it does.
 *
 * <p>There is a dry run for exactly that reason. It walks the same four passes and rolls back at the
 * end, so the office can see what tonight would do before it does it. That is not a separate code
 * path: the difference is one commit.
 *
 * <p>The exit code is zero whether or not the sweep found anything. A night with nothing to do is a
 * normal night, and a cron job that exits non zero on a quiet night sends a mail every night and gets
 * filtered within a fortnight.
 */
final class NightlyCommands {

    private NightlyCommands() {}

    @Command(name = "nightly", description = "Run the sweep. This one writes, unless it is a dry run.")
    static final class Night extends Action {

        @Option(names = "--day", description = "The night to run, for a replay.")
        private Optional<LocalDate> day = Optional.empty();

        @Option(names = "--within", defaultValue = "2", description = "Days ahead to look for expiries.")
        private int within;

        @Option(names = "--dry-run", description = "Walk the passes and roll back.")
        private boolean dryRun;

        @Override
        public Integer call() {
            LocalDate when = day.orElseGet(LocalDate::now);
            Nightly.Sweep found;
            try (UnitOfWork work = work()) {
                found = Nightly.sweep(work.entityManager(), when, within);
                if (dryRun) {
                    work.rollback();
                } else {
                    work.commit();
                }
            }
            echo(found.lines());
            Logs.event(
                    "nightly.done",
                    Facts.of(
                            "day",
                            when,
                            "expiring",
                            found.expiring().size(),
                            "withdrawn",
                            found.withdrawn().size(),
                            "slow",
                            found.slowStudies().size(),
                            "lapsing",
                            found.lapsing().size(),
                            // The office's log readers grep for the flag spelled with a capital.
                            "dry_run",
                            dryRun ? "True" : "False"));
            return Main.DONE;
        }
    }
}
