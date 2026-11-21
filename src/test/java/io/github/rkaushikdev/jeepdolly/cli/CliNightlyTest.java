package io.github.rkaushikdev.jeepdolly.cli;

import static io.github.rkaushikdev.jeepdolly.support.Consoles.call;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.jeepdolly.Logs;
import io.github.rkaushikdev.jeepdolly.permits.ApplicationState;
import io.github.rkaushikdev.jeepdolly.persistence.Database;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.support.Applications;
import io.github.rkaushikdev.jeepdolly.support.Consoles;
import io.github.rkaushikdev.jeepdolly.support.Consoles.Answer;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

/** The nightly command, and the difference one commit makes. */
class CliNightlyTest {

    private static final String STALE = "2025-04-01";
    private static final String LATER = "2025-04-20";

    private Database database;
    private Console console;

    @BeforeEach
    void aConsoleOverASeededDatabase() {
        database = Databases.seeded();
        console = Consoles.over(database);
    }

    @AfterEach
    void theLogGoesBackToStandardError() {
        Logs.configure(Level.WARN, null);
    }

    /** A draft filed on the first of April and never sent, which the sweep withdraws a fortnight on. */
    private void stale() {
        LocalDate when = LocalDate.of(2025, 4, 1);
        try (UnitOfWork work = database.unitOfWork()) {
            work.applications()
                    .add(Applications.application()
                            .filed(when)
                            .start(when)
                            .end(when)
                            .build());
            work.commit();
        }
    }

    private ApplicationState stateOf() {
        try (UnitOfWork work = database.unitOfWork()) {
            return work.applications().row(Applications.REFERENCE).getState();
        }
    }

    @Test
    void aQuietNightPrintsAHeadingAndAFooting() {
        Answer answer = call(console, "nightly", "--day", STALE);
        assertThat(answer.exitCode()).isZero();
        List<String> lines = answer.shown();
        assertThat(lines.get(0)).isEqualTo("nightly 2025-04-01");
        assertThat(lines.get(lines.size() - 1)).endsWith("0 cards  0 held");
    }

    @Test
    void theSweepWritesWhenItIsNotADryRun() {
        stale();
        Answer answer = call(console, "nightly", "--day", LATER);
        assertThat(answer.output()).contains("withdrawn  25-04471");
        assertThat(stateOf()).isEqualTo(ApplicationState.WITHDRAWN);
    }

    @Test
    void aDryRunWalksTheSamePassesAndChangesNothing() {
        stale();
        Answer answer = call(console, "nightly", "--day", LATER, "--dry-run");
        assertThat(answer.output()).contains("withdrawn  25-04471");
        assertThat(stateOf()).isEqualTo(ApplicationState.DRAFT);
    }

    @Test
    void aDryRunAndARealRunPrintTheSameThing() {
        stale();
        String dry = call(console, "nightly", "--day", LATER, "--dry-run").output();
        String wet = call(console, "nightly", "--day", LATER).output();
        assertThat(dry).isEqualTo(wet);
    }

    @Test
    void aNightWithNothingToDoIsStillExitCodeZero() {
        assertThat(call(console, "nightly", "--day", STALE).exitCode()).isZero();
    }

    @Test
    void theSweepLeavesOneLineOnTheLog() {
        stale();
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        Logs.configure(Level.INFO, stream);
        call(console, "nightly", "--day", LATER);
        String written = stream.toString(StandardCharsets.UTF_8);
        assertThat(written).contains("nightly.done day=2025-04-20");
        assertThat(written).contains("withdrawn=1");
        assertThat(written).contains("dry_run=False");
    }

    @Test
    void theDryRunSaysSoOnTheLog() {
        stale();
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        Logs.configure(Level.INFO, stream);
        call(console, "nightly", "--day", LATER, "--dry-run");
        assertThat(stream.toString(StandardCharsets.UTF_8)).contains("dry_run=True");
    }

    @Test
    void theExpiryHorizonIsAnArgument() {
        stale();
        Answer answer = call(console, "nightly", "--day", STALE, "--within", "30");
        assertThat(answer.exitCode()).isZero();
        assertThat(answer.output()).contains("0 expiring");
    }

    @Test
    void aDayThatIsNotADayIsAUsageError() {
        assertThat(call(console, "nightly", "--day", "last tuesday").exitCode()).isEqualTo(2);
    }
}
