package io.github.rkaushikdev.jeepdolly.api.routers;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.api.Deps;
import io.github.rkaushikdev.jeepdolly.api.Http;
import io.github.rkaushikdev.jeepdolly.api.Role;
import io.github.rkaushikdev.jeepdolly.api.TestService;
import io.github.rkaushikdev.jeepdolly.cli.Console;
import io.github.rkaushikdev.jeepdolly.domain.Envelope;
import io.github.rkaushikdev.jeepdolly.permits.Annual;
import io.github.rkaushikdev.jeepdolly.permits.Application;
import io.github.rkaushikdev.jeepdolly.permits.Numbering;
import io.github.rkaushikdev.jeepdolly.permits.Office;
import io.github.rkaushikdev.jeepdolly.permits.Permit;
import io.github.rkaushikdev.jeepdolly.persistence.Database;
import io.github.rkaushikdev.jeepdolly.persistence.ReferenceCache;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.support.Applications;
import io.github.rkaushikdev.jeepdolly.support.Clients;
import io.github.rkaushikdev.jeepdolly.support.Consoles;
import io.github.rkaushikdev.jeepdolly.support.Consoles.Answer;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** The renewal run over the interface and over the command line. */
@WebMvcTest
@Import(TestService.class)
public class ApiRenewalsTest {

    /** A day the annuals issued in January are three weeks from running out. */
    public static final String NEAR = "2025-12-15";

    private static final LocalDate ISSUED = LocalDate.of(2025, 1, 6);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ReferenceCache cache;

    private Database database;
    private Map<Role, Http> clients;
    private Http client;

    @BeforeEach
    void aClerkOverASeededDatabase() {
        database = Databases.seeded();
        cache.reset();
        clients = Clients.clientsFor(new Http(mvc), database);
        client = clients.get(Role.CLERK);
    }

    /** Bickford's annual for the excavator move, issued in January, and its number. */
    public static String anAnnual(Database database) {
        return anAnnual(database, "25-04471", 1204471);
    }

    /** One carrier's annual against an application of its own, issued in January, and its number. */
    public static String anAnnual(Database database, String reference, int usdot) {
        try (UnitOfWork work = database.unitOfWork()) {
            Application application =
                    Applications.application(reference).usdot(usdot).build();
            work.applications().add(application);
            work.commit();
            Office office = Deps.officeFrom(work, ISSUED);
            Permit made = Annual.issue(
                    new Numbering(work.counter()),
                    office.library(),
                    office.schedules().on(ISSUED),
                    reference,
                    usdot,
                    office.carriers().get(usdot).name(),
                    Envelope.read("11'", "13'6", "70'"),
                    new Pounds(99000),
                    office.commodities().get(application.commodity()),
                    ISSUED);
            work.permits().add(made);
            work.commit();
            return made.number().toString();
        }
    }

    private static List<String> numbers(JsonNode found) {
        List<String> made = new ArrayList<>();
        found.forEach(one -> made.add(one.get("number").asText()));
        return made;
    }

    private static Map<String, Object> renewing(String... numbers) {
        return Map.of("numbers", List.of(numbers));
    }

    @Test
    void nothingIsDueOnAQuietDay() {
        assertThat(client.get("/renewals?on=2025-06-01").json().size()).isZero();
    }

    @Test
    void anAnnualRunningOutIsDueWithTheDaysLeft() {
        String annual = anAnnual(database);
        JsonNode found = client.get("/renewals?on=" + NEAR).json();
        assertThat(numbers(found)).containsExactly(annual);
        assertThat(found.get(0).get("days_left").asInt()).isEqualTo(21);
        assertThat(found.get(0).get("carrier").asText()).isEqualTo("Bickford Heavy Haul");
    }

    @Test
    void theWindowCanBeWidened() {
        anAnnual(database);
        assertThat(client.get("/renewals?on=2025-06-01&within=365").json().size())
                .isPositive();
    }

    @Test
    void aWindowWiderThanAYearIsRefused() {
        assertThat(client.get("/renewals?on=2025-06-01&within=400").status()).isEqualTo(422);
    }

    @Test
    void theLettersAreOnePerCarrier() {
        anAnnual(database, "25-04471", 1204471);
        anAnnual(database, "25-04472", 1204471);
        JsonNode found = client.get("/renewals/letters?on=" + NEAR).json();
        assertThat(found.size()).isEqualTo(1);
        assertThat(found.get(0).get("count").asInt()).isEqualTo(2);
        assertThat(found.get(0).get("numbers").size()).isEqualTo(2);
    }

    @Test
    void theLetterCarriesTheLinesItPrints() {
        anAnnual(database);
        JsonNode found = client.get("/renewals/letters?on=" + NEAR).json();
        assertThat(found.get(0).get("lines").get(0).asText()).isEqualTo("Bickford Heavy Haul (1204471)");
    }

    @Test
    void renewingIssuesANewPermit() {
        String annual = anAnnual(database);
        Http.Answer answer = client.postJson("/renewals/1204471?on=" + NEAR, renewing(annual));
        assertThat(answer.status()).isEqualTo(201);
        JsonNode found = answer.json();
        assertThat(found.get("replaced").get(0).asText()).isEqualTo(annual);
        assertThat(found.get("replaced").size()).isEqualTo(1);
        assertThat(found.get("issued").get(0).get("number").asText()).isNotEqualTo(annual);
    }

    @Test
    void renewingNothingIsRefusedByTheSchema() {
        assertThat(client.postJson("/renewals/1204471?on=" + NEAR, renewing()).status())
                .isEqualTo(422);
    }

    @Test
    void aPermitBelongingToSomebodyElseIsRefused() {
        String mine = anAnnual(database, "25-04471", 1204471);
        String theirs = anAnnual(database, "25-04480", 2299001);
        Http.Answer answer = client.postJson("/renewals/1204471?on=" + NEAR, renewing(mine, theirs));
        assertThat(answer.status()).isEqualTo(409);
        assertThat(answer.json().get("message").asText()).contains("does not belong");
    }

    @Test
    void aRefusedBatchIssuesNothingAtAll() {
        String mine = anAnnual(database, "25-04471", 1204471);
        String theirs = anAnnual(database, "25-04480", 2299001);
        int before = client.get("/renewals?on=" + NEAR).json().size();
        client.postJson("/renewals/1204471?on=" + NEAR, renewing(mine, theirs));
        assertThat(client.get("/renewals?on=" + NEAR).json().size()).isEqualTo(before);
    }

    @Test
    void aPermitNobodyHasIsA404() {
        Http.Answer answer = client.postJson("/renewals/1204471?on=" + NEAR, renewing("25-A-999999"));
        assertThat(answer.status()).isEqualTo(404);
    }

    @Test
    void aReaderCannotRenew() {
        String annual = anAnnual(database);
        Http.Answer answer = clients.get(Role.READER).postJson("/renewals/1204471?on=" + NEAR, renewing(annual));
        assertThat(answer.status()).isEqualTo(403);
    }

    /** The same run from a terminal in the back office. */
    @Nested
    class TheCommands {

        private Console console;

        @BeforeEach
        void aConsoleOverTheSameDatabase() {
            console = Consoles.over(database);
        }

        @Test
        void theDueListPrintsOnePermitToALine() {
            String number = anAnnual(database);
            Answer answer = Consoles.call(console, "renewal", "due", "--day", NEAR);
            assertThat(answer.exitCode()).isZero();
            assertThat(answer.shown()).containsExactly(number + "  2026-01-05  21  Bickford Heavy Haul");
        }

        @Test
        void aQuietWindowSaysSo() {
            Answer answer = Consoles.call(console, "renewal", "due", "--day", "2025-06-01");
            assertThat(answer.output().strip()).isEqualTo("nothing is due in that window");
        }

        @Test
        void theMergeRowRunsTheNumbersTogether() {
            String first = anAnnual(database, "25-04471", 1204471);
            String second = anAnnual(database, "25-04472", 1204471);
            Answer answer = Consoles.call(console, "renewal", "letters", "--day", NEAR);
            assertThat(answer.shown())
                    .containsExactly("1204471  Bickford Heavy Haul  2  2026-01-05  " + first + ";" + second);
        }

        @Test
        void theLongFormPrintsTheLetterItself() {
            anAnnual(database);
            Answer answer = Consoles.call(console, "renewal", "letters", "--day", NEAR, "--long");
            assertThat(answer.shown().get(0)).isEqualTo("Bickford Heavy Haul (1204471)");
        }

        @Test
        void nobodyDueSaysSo() {
            Answer answer = Consoles.call(console, "renewal", "letters", "--day", "2025-06-01");
            assertThat(answer.output().strip()).isEqualTo("nobody is due a letter");
        }

        @Test
        void renewingFromTheCommandLineIssuesAndReports() {
            String number = anAnnual(database);
            Answer answer = Consoles.call(console, "renewal", "renew", "1204471", number, "--on", NEAR);
            assertThat(answer.exitCode()).isZero();
            List<String> printed = answer.shown();
            assertThat(printed.get(0)).startsWith("renewed  25-A-");
            assertThat(printed.get(printed.size() - 1)).isEqualTo("1  renewed for  1204471");
        }

        @Test
        void aRefusedBatchIsARefusalRatherThanATraceback() {
            anAnnual(database);
            Answer answer = Consoles.call(console, "renewal", "renew", "1204471", "25-A-999999", "--on", NEAR);
            assertThat(answer.exitCode()).isNotZero();
        }
    }
}
