package io.github.rkaushikdev.jeepdolly.cli;

import static io.github.rkaushikdev.jeepdolly.support.Consoles.call;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.jeepdolly.permits.Application;
import io.github.rkaushikdev.jeepdolly.persistence.Database;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.persistence.models.AuditRow;
import io.github.rkaushikdev.jeepdolly.services.Audit;
import io.github.rkaushikdev.jeepdolly.support.Applications;
import io.github.rkaushikdev.jeepdolly.support.Consoles;
import io.github.rkaushikdev.jeepdolly.support.Consoles.Answer;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import io.github.rkaushikdev.jeepdolly.support.Permits;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine.Command;

/** The permit commands, over a database with a permit in it. */
class CliPermitsTest {

    private static final String START = "2025-04-08";

    private Database database;
    private Console console;

    @BeforeEach
    void aConsoleOverASeededDatabase() {
        database = Databases.seeded();
        console = Consoles.over(database);
    }

    /** The excavator move, applied for and issued, and the number it went out under. */
    private String issued() {
        try (UnitOfWork work = database.unitOfWork()) {
            Application application = Applications.anApplication();
            work.applications().add(application);
            work.commit();
            work.permits().add(Permits.aPermit(application));
            work.commit();
        }
        return "25-P-004471";
    }

    private Answer voided(String number) {
        return call(console, "permits", "void", number, "--reason", "load cancelled", "--by", "D Bickford");
    }

    @Test
    void theGroupIsRegisteredUnderItsOwnName() {
        assertThat(PermitCommands.Permits.class.getAnnotation(Command.class).name())
                .isEqualTo("permits");
        assertThat(call(console, "--help").output()).contains("permits");
    }

    @Test
    void whatIsOnTheRoadIsOnePermitToALine() {
        issued();
        Answer answer = call(console, "permits", "on-the-road", "--day", START);
        assertThat(answer.exitCode()).isZero();
        assertThat(answer.shown())
                .containsExactly("25-P-004471  Bickford Heavy Haul  94,000 lb  waterville  pittsfield");
    }

    @Test
    void aDayWithNothingOutPrintsNothingAtAll() {
        issued();
        Answer answer = call(console, "permits", "on-the-road", "--day", "2025-05-01");
        assertThat(answer.output()).isEmpty();
        assertThat(answer.exitCode()).isZero();
    }

    @Test
    void theExpiringListLooksTheNumberOfDaysItWasAskedTo() {
        issued();
        Answer soon = call(console, "permits", "expiring", "--day", START, "--within", "3");
        assertThat(soon.output()).contains("25-P-004471");
        Answer later = call(console, "permits", "expiring", "--day", "2025-04-20", "--within", "1");
        assertThat(later.output()).isEmpty();
    }

    @Test
    void showingAPermitPrintsItInTheOrderItIsReadOut() {
        issued();
        List<String> lines = call(console, "permits", "show", "25-P-004471").shown();
        assertThat(lines.get(0)).startsWith("25-P-004471  Bickford Heavy Haul");
        assertThat(lines.get(3)).contains("94,000 lb gross");
        assertThat(lines).anyMatch(one -> one.startsWith("R-01"));
        assertThat(lines.get(lines.size() - 1)).isEqualTo("total  $46.12");
    }

    @Test
    void aNumberNobodyIssuedIsARefusalAndNotATraceback() {
        Answer answer = call(console, "permits", "show", "25-P-999999");
        assertThat(answer.exitCode()).isEqualTo(1);
        assertThat(answer.exception()).isEmpty();
        assertThat(answer.err()).startsWith("not_on_file: ");
    }

    @Test
    void voidingFromTheConsoleTakesItOffTheRoad() {
        String number = issued();
        Answer answer = voided(number);
        assertThat(answer.exitCode()).isZero();
        assertThat(answer.output().strip()).isEqualTo("25-P-004471  void  load cancelled");
        assertThat(call(console, "permits", "on-the-road", "--day", START).output())
                .isEmpty();
    }

    @Test
    void voidingFromTheConsoleLandsOnTheTrail() {
        String number = issued();
        voided(number);
        List<AuditRow> found;
        try (UnitOfWork work = database.unitOfWork()) {
            found = Audit.forDocument(work.entityManager(), "permit", "25-P-004471");
        }
        assertThat(found).extracting(AuditRow::getActor).containsExactly("D Bickford");
        assertThat(found.get(0).getDetail()).isEqualTo("load cancelled");
    }

    @Test
    void voidingWithoutSayingWhoIsAUsageError() {
        String number = issued();
        Answer answer = call(console, "permits", "void", number, "--reason", "no");
        assertThat(answer.exitCode()).isEqualTo(2);
    }

    @Test
    void whatACarrierHoldsIsAskedByNumber() {
        issued();
        Answer answer = call(console, "permits", "carrier", "1204471");
        assertThat(answer.output()).contains("25-P-004471");
        assertThat(call(console, "permits", "carrier", "2299001").output()).isEmpty();
    }
}
