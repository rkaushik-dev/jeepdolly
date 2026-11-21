package io.github.rkaushikdev.jeepdolly.cli;

import static io.github.rkaushikdev.jeepdolly.support.Consoles.call;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.jeepdolly.domain.RestrictionKind;
import io.github.rkaushikdev.jeepdolly.permits.Application;
import io.github.rkaushikdev.jeepdolly.persistence.Database;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.persistence.models.RestrictionRow;
import io.github.rkaushikdev.jeepdolly.reports.IssuanceReport;
import io.github.rkaushikdev.jeepdolly.support.Applications;
import io.github.rkaushikdev.jeepdolly.support.Consoles;
import io.github.rkaushikdev.jeepdolly.support.Consoles.Answer;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import io.github.rkaushikdev.jeepdolly.support.Permits;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The report commands, which print what the paper report prints. */
class CliReportsTest {

    private static final String APRIL = "2025-04-07";

    private Database database;
    private Console console;

    @BeforeEach
    void aConsoleOverASeededDatabase() {
        database = Databases.seeded();
        console = Consoles.over(database);
    }

    /** The excavator move, applied for and issued on the seventh of April. */
    private void issued() {
        try (UnitOfWork work = database.unitOfWork()) {
            Application application = Applications.anApplication();
            work.applications().add(application);
            work.commit();
            work.permits().add(Permits.aPermit(application));
            work.commit();
        }
    }

    private void restriction(String ident, String segment, String note, Consumer<RestrictionRow> more) {
        RestrictionRow row = new RestrictionRow(ident, segment, RestrictionKind.CLOSURE, note);
        more.accept(row);
        try (UnitOfWork work = database.unitOfWork()) {
            work.add(row);
            work.commit();
        }
    }

    @Test
    void theEveningReportPrintsTheLinesTheReportProduced() {
        issued();
        Answer answer = call(console, "report", "issuance", "--day", APRIL);
        List<String> expected;
        try (UnitOfWork work = database.unitOfWork()) {
            expected = IssuanceReport.issuance(work.entityManager(), LocalDate.of(2025, 4, 7))
                    .lines();
        }
        assertThat(answer.exitCode()).isZero();
        assertThat(answer.shown()).isEqualTo(expected);
    }

    @Test
    void theEveningReportWritesNothingOnTheErrorStream() {
        issued();
        Answer answer = call(console, "report", "issuance", "--day", APRIL);
        assertThat(answer.err()).isEmpty();
        assertThat(answer.out()).contains("permits issued");
    }

    @Test
    void aSpanIsAskedForWithASecondDay() {
        issued();
        Answer answer = call(console, "report", "issuance", "--day", APRIL, "--to", "2025-04-30");
        assertThat(answer.shown().get(0)).isEqualTo("permits issued 2025-04-07 to 2025-04-30");
    }

    @Test
    void theRevenueReportNeedsBothEndsOfTheSpan() {
        issued();
        assertThat(call(console, "report", "revenue", "--from", APRIL).exitCode())
                .isEqualTo(2);
        Answer answer = call(console, "report", "revenue", "--from", APRIL, "--to", "2025-04-30");
        assertThat(answer.exitCode()).isZero();
        assertThat(answer.output()).contains("base fee");
    }

    @Test
    void aCarrierCanBeHandedOneLine() {
        issued();
        Answer answer =
                call(console, "report", "carrier", "Bickford Heavy Haul", "--from", APRIL, "--to", "2025-04-30");
        assertThat(answer.output().strip()).isEqualTo("Bickford Heavy Haul  $46.12");
    }

    @Test
    void aCarrierThatWasChargedNothingStillGetsALine() {
        issued();
        Answer answer = call(console, "report", "carrier", "Nobody", "--from", APRIL, "--to", "2025-04-30");
        assertThat(answer.output().strip()).isEqualTo("Nobody  $0.00");
    }

    @Test
    void theRoadReportSaysWhatChanges() {
        restriction("R-1", "US2-1", "culvert work", row -> {
            row.setEffective(LocalDate.of(2025, 4, 9));
            row.setExpires(LocalDate.of(2025, 4, 11));
        });
        Answer answer = call(console, "report", "roads", "--day", APRIL, "--days", "7");
        assertThat(answer.output()).contains("closes");
        assertThat(answer.output()).contains("opens");
    }

    @Test
    void aQuietFortnightSaysSo() {
        List<String> lines = call(console, "report", "roads", "--day", APRIL).shown();
        assertThat(lines.get(lines.size() - 1)).isEqualTo("nothing changes");
    }

    @Test
    void theStandingListPrintsOneRestrictionToALine() {
        restriction("R-2", "SA148-1", "washout", row -> {});
        Answer answer = call(console, "report", "standing", "--day", APRIL);
        assertThat(answer.output().strip()).startsWith("SA148-1  closure");
    }
}
