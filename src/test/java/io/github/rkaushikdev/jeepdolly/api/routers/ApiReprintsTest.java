package io.github.rkaushikdev.jeepdolly.api.routers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.jeepdolly.Conflict;
import io.github.rkaushikdev.jeepdolly.OfficeRules;
import io.github.rkaushikdev.jeepdolly.Settings;
import io.github.rkaushikdev.jeepdolly.api.Bodies;
import io.github.rkaushikdev.jeepdolly.api.Http;
import io.github.rkaushikdev.jeepdolly.api.Role;
import io.github.rkaushikdev.jeepdolly.api.Running;
import io.github.rkaushikdev.jeepdolly.api.TestService;
import io.github.rkaushikdev.jeepdolly.permits.Application;
import io.github.rkaushikdev.jeepdolly.permits.Reason;
import io.github.rkaushikdev.jeepdolly.permits.Reprint;
import io.github.rkaushikdev.jeepdolly.persistence.Database;
import io.github.rkaushikdev.jeepdolly.persistence.ReferenceCache;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.persistence.models.AuditRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.ReprintRow;
import io.github.rkaushikdev.jeepdolly.services.Audit;
import io.github.rkaushikdev.jeepdolly.support.Applications;
import io.github.rkaushikdev.jeepdolly.support.Clients;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import io.github.rkaushikdev.jeepdolly.support.Permits;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** Duplicate copies over the interface, and the rung of the ladder they want. */
@WebMvcTest
@Import(TestService.class)
class ApiReprintsTest {

    private static final String INSIDE = "2025-04-09";
    private static final String AFTER = "2025-05-01";
    private static final String NUMBER = "25-P-004471";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ReferenceCache cache;

    private Database database;
    private Map<Role, Http> clients;
    private Http client;
    private Http supervisor;

    /** The excavator move's permit on file, and a client for every role. */
    @BeforeEach
    void anIssuedPermit() {
        database = Databases.seeded();
        cache.reset();
        try (UnitOfWork work = database.unitOfWork()) {
            Application application = Applications.anApplication();
            work.applications().add(application);
            work.commit();
            work.permits().add(Permits.aPermit(application));
            work.commit();
        }
        clients = Clients.clientsFor(new Http(mvc), database);
        client = clients.get(Role.CLERK);
        supervisor = clients.get(Role.SUPERVISOR);
    }

    private static Http.Answer ask(Http client) {
        return ask(client, "lost", "", INSIDE, NUMBER);
    }

    private static Http.Answer ask(Http client, String reason, String note, String on, String number) {
        Map<String, Object> body = Bodies.map("reason", reason, "asked_by", "D Bickford", "note", note);
        return client.postJson("/permits/" + number + "/copies?on=" + on, body);
    }

    private static Http.Answer askBecause(Http client, String reason) {
        return ask(client, reason, "", INSIDE, NUMBER);
    }

    private static List<Integer> copies(JsonNode found) {
        List<Integer> made = new ArrayList<>();
        found.forEach(one -> made.add(one.get("copy_number").asInt()));
        return made;
    }

    @Test
    void aFirstCopyIsCopyTwo() {
        Http.Answer answer = ask(client);
        assertThat(answer.status()).isEqualTo(201);
        assertThat(answer.json().get("copy_number").asInt()).isEqualTo(2);
    }

    @Test
    void theSheetComesBackWithTheBannerOnIt() {
        String written = ask(client).json().get("sheet").asText();
        assertThat(written).contains("*** DUPLICATE - COPY 2 ***");
        assertThat(written.split("\\*\\*\\* DUPLICATE - COPY 2 \\*\\*\\*", -1)).hasSize(3);
    }

    @Test
    void theSheetIsTheSamePermitWithTheSameConditions() {
        String written = ask(client).json().get("sheet").asText();
        assertThat(written).contains("PERMIT 25-P-004471");
        assertThat(written).contains("CONDITIONS");
    }

    @Test
    void copiesCarryOnTheSequence() {
        ask(client);
        assertThat(ask(client).json().get("copy_number").asInt()).isEqualTo(3);
    }

    @Test
    void theRegisterListsEveryCopy() {
        askBecause(client, "lost");
        askBecause(client, "damaged");
        JsonNode found = client.get("/permits/" + NUMBER + "/copies").json();
        assertThat(copies(found)).containsExactly(2, 3);
        List<String> reasons = new ArrayList<>();
        found.forEach(one -> reasons.add(one.get("reason").asText()));
        assertThat(reasons).containsExactly("lost", "damaged");
    }

    @Test
    void aPermitNobodyCopiedHasAnEmptyRegister() {
        assertThat(client.get("/permits/" + NUMBER + "/copies").json().size()).isZero();
    }

    @Test
    void aPermitNobodyHasIsA404() {
        assertThat(client.get("/permits/25-P-999999/copies").status()).isEqualTo(404);
        assertThat(ask(client, "lost", "", INSIDE, "25-P-999999").status()).isEqualTo(404);
    }

    @Test
    void aReasonThisOfficeDoesNotRecordIsRefused() {
        Http.Answer answer = askBecause(client, "the dog ate it");
        assertThat(answer.status()).isEqualTo(422);
        assertThat(answer.json().get("message").asText()).contains("the dog ate it");
    }

    @Test
    void anotherReasonWantsASentence() {
        assertThat(askBecause(client, "other").status()).isEqualTo(422);
        assertThat(ask(client, "other", "wanted one for the file", INSIDE, NUMBER)
                        .status())
                .isEqualTo(201);
    }

    @Test
    void aCopyOfAnExpiredPermitIsRefused() {
        Http.Answer answer = ask(client, "lost", "", AFTER, NUMBER);
        assertThat(answer.status()).isEqualTo(409);
        assertThat(answer.json().get("message").asText()).contains("expired on");
    }

    @Test
    void aCopyOfAVoidPermitIsRefused() {
        supervisor.post("/permits/" + NUMBER + "/void?reason=load cancelled&on=" + INSIDE);
        assertThat(ask(supervisor).status()).isEqualTo(409);
    }

    @Test
    void aClerkMayPrintUpToTheFreeLimit() {
        for (int one = 0; one < Reprint.FREELY - 1; one++) {
            assertThat(ask(client).status()).isEqualTo(201);
        }
        List<Integer> printed =
                copies(client.get("/permits/" + NUMBER + "/copies").json());
        assertThat(printed.get(printed.size() - 1)).isEqualTo(Reprint.FREELY);
    }

    @Test
    void aClerkIsRefusedPastIt() {
        for (int one = 0; one < Reprint.FREELY - 1; one++) {
            ask(client);
        }
        Http.Answer answer = ask(client);
        assertThat(answer.status()).isEqualTo(409);
        assertThat(answer.json().get("message").asText()).contains("wants a supervisor");
    }

    @Test
    void theSameRequestFromASupervisorGoesThrough() {
        for (int one = 0; one < Reprint.FREELY - 1; one++) {
            assertThat(ask(client).status()).isEqualTo(201);
        }
        assertThat(ask(client).status()).isEqualTo(409);
        Http.Answer answer = ask(supervisor);
        assertThat(answer.status()).isEqualTo(201);
        assertThat(answer.json().get("copy_number").asInt()).isEqualTo(Reprint.FREELY + 1);
        assertThat(answer.json().get("needed_a_supervisor").asBoolean()).isTrue();
    }

    @Test
    void theFreeLimitCanBeAskedFor() {
        assertThat(Http.asMap(client.get("/permits/copies/free-limit").json()))
                .isEqualTo(Map.of("freely", Reprint.FREELY));
    }

    @Test
    void aCopyLandsOnTheTrailWithItsNumberAndReason() {
        askBecause(client, "damaged");
        try (UnitOfWork work = database.unitOfWork()) {
            List<AuditRow> found = Audit.forDocument(work.entityManager(), "permit", NUMBER);
            AuditRow last = found.get(found.size() - 1);
            assertThat(last.getAction()).isEqualTo("permit.reprinted");
            assertThat(last.getDetail()).isEqualTo("copy 2, damaged");
        }
    }

    @Test
    void theCopyIsRecordedAgainstWhoeverPrintedIt() {
        JsonNode found = ask(client).json();
        assertThat(found.get("asked_by").asText()).isEqualTo("D Bickford");
        assertThat(found.get("issued_by").asText()).isNotEmpty();
    }

    @Test
    void aReaderCannotPrintACopy() {
        assertThat(ask(clients.get(Role.READER)).status()).isEqualTo(403);
    }

    @Test
    void aReaderCanReadTheRegister() {
        ask(client);
        assertThat(clients.get(Role.READER)
                        .get("/permits/" + NUMBER + "/copies")
                        .json()
                        .size())
                .isEqualTo(1);
    }

    @Test
    void thePermitItselfIsUntouchedByACopy() {
        JsonNode before = client.get("/permits/" + NUMBER).json();
        ask(client);
        assertThat(client.get("/permits/" + NUMBER).json()).isEqualTo(before);
    }

    @Test
    void anOfficeThatAllowsMoreCopiesLetsAClerkPrintThem() {
        Settings settings = Settings.defaults()
                .withDatabase(Databases.URL)
                .withRules(OfficeRules.defaults().withFreeCopies(5));
        try (Running service = Running.start(settings, database)) {
            Http clerk = Clients.keyed(service.http(), database, Role.CLERK);
            assertThat(Http.asMap(clerk.get("/permits/copies/free-limit").json()))
                    .isEqualTo(Map.of("freely", 5));
            for (int one = 0; one < 4; one++) {
                assertThat(ask(clerk).status()).isEqualTo(201);
            }
            assertThat(ask(clerk).status()).isEqualTo(409);
        }
    }

    /** The register on the database, without the interface in the way. */
    @Nested
    class TheStore {

        private UnitOfWork work;

        @BeforeEach
        void aUnitOfWork() {
            work = database.unitOfWork();
        }

        @AfterEach
        void closeIt() {
            work.close();
        }

        private Reprint aReprint(int copy) {
            return new Reprint(NUMBER, copy, LocalDate.of(2025, 4, 9), Reason.LOST, "D Bickford", "M Cyr");
        }

        @Test
        void aCopyGoesInAndComesBackTheSameCopy() {
            Reprint one = aReprint(2);
            work.reprints().add(one);
            work.flush();
            assertThat(work.reprints().forPermit(NUMBER).get(0).toDomain()).isEqualTo(one);
        }

        @Test
        void theCopyNumbersTakenComeBackInOrder() {
            work.reprints().add(aReprint(3));
            work.reprints().add(aReprint(2));
            work.flush();
            assertThat(work.reprints().copiesTaken(NUMBER)).containsExactly(2, 3);
        }

        @Test
        void theSameCopyNumberIsRefusedBeforeTheDatabaseSeesIt() {
            work.reprints().add(aReprint(2));
            work.flush();
            assertThatThrownBy(() -> work.reprints().add(aReprint(2))).isInstanceOf(Conflict.class);
        }

        @Test
        void aWindowOfCopiesComesBackForTheYearlyQuestion() {
            work.reprints().add(aReprint(2));
            work.flush();
            assertThat(work.reprints().between(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)))
                    .hasSize(1);
            assertThat(work.reprints().between(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)))
                    .isEmpty();
        }

        @Test
        void theRegisterCountsWhatItHolds() {
            work.reprints().add(aReprint(2));
            work.flush();
            assertThat(work.reprints().count()).isEqualTo(1);
        }

        @Test
        void theRowSaysWhatItIsWhenSomethingPrintsIt() {
            work.reprints().add(aReprint(2));
            work.flush();
            ReprintRow row = work.entityManager().find(ReprintRow.class, new ReprintRow.Key(NUMBER, 2));
            assertThat(row).hasToString("<ReprintRow 25-P-004471 copy 2>");
        }
    }
}
