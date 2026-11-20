package io.github.rkaushikdev.jeepdolly.api.routers;

import static io.github.rkaushikdev.jeepdolly.api.Bodies.FILED;
import static io.github.rkaushikdev.jeepdolly.api.Bodies.application;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.jeepdolly.api.Bodies;
import io.github.rkaushikdev.jeepdolly.api.Http;
import io.github.rkaushikdev.jeepdolly.api.Role;
import io.github.rkaushikdev.jeepdolly.api.TestService;
import io.github.rkaushikdev.jeepdolly.permits.ApplicationState;
import io.github.rkaushikdev.jeepdolly.persistence.Database;
import io.github.rkaushikdev.jeepdolly.persistence.ReferenceCache;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.persistence.models.AuditRow;
import io.github.rkaushikdev.jeepdolly.services.Audit;
import io.github.rkaushikdev.jeepdolly.support.Clients;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** The window, where a hauler gets a permit or a reason in one request. */
@WebMvcTest
@Import(TestService.class)
class ApiWalkinTest {

    private static final String WALK_IN = "/permits/walk-in?on=" + FILED;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ReferenceCache cache;

    private Database database;
    private Http client;

    @BeforeEach
    void aSupervisorOverASeededDatabase() {
        database = Databases.seeded();
        cache.reset();
        client = Clients.keyed(new Http(mvc), database);
    }

    private static List<String> codes(JsonNode conditions) {
        List<String> made = new ArrayList<>();
        conditions.forEach(one -> made.add(one.get("code").asText()));
        return made;
    }

    @Test
    void aWalkInGetsAPermitInOneRequest() {
        Http.Answer answer = client.postJson(WALK_IN, application());
        assertThat(answer.status()).isEqualTo(201);
        JsonNode found = answer.json();
        assertThat(found.get("number").asText()).isEqualTo("25-P-000001");
        assertThat(found.get("conditions").size()).isPositive();
        assertThat(found.get("fees").get("total").asText()).startsWith("$");
    }

    @Test
    void theApplicationBehindItIsOnFileAndIssued() {
        client.postJson(WALK_IN, application());
        JsonNode found = client.get("/applications/25-F-000001").json();
        assertThat(found.get("state").asText()).isEqualTo("issued");
        try (UnitOfWork work = database.unitOfWork()) {
            List<AuditRow> trail = Audit.forDocument(work.entityManager(), "application", "25-F-000001");
            assertThat(trail)
                    .extracting(AuditRow::getAction)
                    .containsExactly("application.filed", "application.submitted");
        }
    }

    @Test
    void aWalkInThatCannotBePermittedSaysEveryReason() {
        Http.Answer answer = client.postJson(WALK_IN, Bodies.gravel());
        assertThat(answer.status()).isEqualTo(409);
        assertThat(answer.json().get("facts").get("reasons").size()).isPositive();
        assertThat(answer.json().get("facts").get("reference").asText()).isEqualTo("25-F-000001");
    }

    @Test
    void aRefusedWalkInLeavesTheApplicationRefusedAndNoPermit() {
        client.postJson(WALK_IN, Bodies.gravel());
        assertThat(client.get("/applications/25-F-000001").json().get("state").asText())
                .isEqualTo("refused");
        try (UnitOfWork work = database.unitOfWork()) {
            assertThat(work.permits().onTheRoad(LocalDate.of(2025, 4, 8))).isEmpty();
        }
    }

    @Test
    void theNextWalkInStillGetsTheFirstPermitNumber() {
        client.postJson(WALK_IN, Bodies.gravel());
        Http.Answer answer = client.postJson(WALK_IN, application());
        assertThat(answer.json().get("number").asText()).isEqualTo("25-P-000001");
    }

    @Test
    void aWalkInNeedsAClerk() {
        Http reader = Clients.keyed(new Http(mvc), database, Role.READER);
        assertThat(reader.postJson(WALK_IN, application()).status()).isEqualTo(403);
    }

    @Test
    void anApplicationCanStillBeRefusedOnItsOwn() {
        String reference = Bodies.filed(client, application());
        Http.Answer answer = client.post("/applications/" + reference + "/refuse?reason=the bridge is posted");
        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.json().get("state").asText()).isEqualTo(ApplicationState.REFUSED.value());
    }

    @Test
    void theTwoWaysInProduceTheSamePermit() {
        JsonNode walk = client.postJson(WALK_IN, application()).json();
        String reference = Bodies.submitted(client, application());
        JsonNode slow =
                client.post("/permits?reference=" + reference + "&on=" + FILED).json();
        assertThat(walk.get("fees").get("total").asText())
                .isEqualTo(slow.get("fees").get("total").asText());
        assertThat(codes(walk.get("conditions"))).isEqualTo(codes(slow.get("conditions")));
    }
}
