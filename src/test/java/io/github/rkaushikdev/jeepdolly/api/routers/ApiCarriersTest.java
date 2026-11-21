package io.github.rkaushikdev.jeepdolly.api.routers;

import static io.github.rkaushikdev.jeepdolly.api.Bodies.map;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.jeepdolly.api.Bodies;
import io.github.rkaushikdev.jeepdolly.api.Http;
import io.github.rkaushikdev.jeepdolly.api.Role;
import io.github.rkaushikdev.jeepdolly.api.TestService;
import io.github.rkaushikdev.jeepdolly.persistence.Database;
import io.github.rkaushikdev.jeepdolly.persistence.ReferenceCache;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.persistence.models.AuditRow;
import io.github.rkaushikdev.jeepdolly.services.Audit;
import io.github.rkaushikdev.jeepdolly.support.Clients;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** The carrier routes, and what a suspension does to the permits it holds. */
@WebMvcTest
@Import(TestService.class)
class ApiCarriersTest {

    private static final String START = "2025-04-08";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ReferenceCache cache;

    private Database database;
    private Map<Role, Http> clients;
    private Http client;

    @BeforeEach
    void aClientForEveryRoleOverASeededDatabase() {
        database = Databases.seeded();
        cache.reset();
        clients = Clients.clientsFor(new Http(mvc), database);
        client = clients.get(Role.SUPERVISOR);
    }

    private List<AuditRow> trail(String kind, String reference) {
        try (UnitOfWork work = database.unitOfWork()) {
            return Audit.forDocument(work.entityManager(), kind, reference);
        }
    }

    private static List<String> actions(List<AuditRow> found) {
        return found.stream().map(AuditRow::getAction).toList();
    }

    private static List<Integer> usdots(JsonNode found) {
        assertThat(found.isArray()).as("a list").isTrue();
        List<Integer> made = new ArrayList<>();
        found.forEach(one -> made.add(one.get("usdot").intValue()));
        return made;
    }

    @Test
    void aCarrierIsRegisteredAndReadBack() {
        Http.Answer answer = client.postJson(
                "/carriers?on=" + START,
                map("usdot", 3311002, "name", "Sons Rigging", "town", "Bangor", "state", "ME"));
        assertThat(answer.status()).isEqualTo(201);
        assertThat(answer.json().get("standing").asText()).isEqualTo("no certificate of insurance on file");
        assertThat(client.get("/carriers/3311002?on=" + START)
                        .json()
                        .get("name")
                        .asText())
                .isEqualTo("Sons Rigging");
    }

    @Test
    void theSameCarrierTwiceIsAConflict() {
        Map<String, Object> written = map("usdot", 1204471, "name", "Bickford", "town", "Newport", "state", "ME");
        Http.Answer answer = client.postJson("/carriers?on=" + START, written);
        assertThat(answer.status()).isEqualTo(409);
        assertThat(answer.json().get("code").asText()).isEqualTo("conflict");
    }

    @Test
    void aCarrierWithPaperIsInGoodStanding() {
        assertThat(client.get("/carriers/1204471?on=" + START)
                        .json()
                        .get("standing")
                        .asText())
                .isEqualTo("in good standing");
    }

    @Test
    void aCertificateFiledByTheCounterPutsACarrierRight() {
        assertThat(client.get("/carriers/2299001?on=" + START)
                        .json()
                        .get("standing")
                        .asText())
                .contains("no certificate");
        Http.Answer answer = client.postJson(
                "/carriers/2299001/insurance?on=" + START,
                map(
                        "policy",
                        "CA-99001",
                        "insurer",
                        "Acadia Mutual",
                        "liability",
                        "$1,000,000.00",
                        "effective",
                        "2025-01-01",
                        "expires",
                        "2025-12-31"));
        assertThat(answer.status()).isEqualTo(201);
        assertThat(answer.json().get("standing").asText()).isEqualTo("in good standing");
    }

    @Test
    void aBondIsFiledTheSameWay() {
        Http.Answer answer = client.postJson(
                "/carriers/1204471/bond?on=" + START,
                map(
                        "number",
                        "SB-9001",
                        "surety",
                        "Penobscot Surety",
                        "amount",
                        "$50,000.00",
                        "effective",
                        "2025-01-01",
                        "expires",
                        "2026-12-31"));
        assertThat(answer.status()).isEqualTo(201);
    }

    @Test
    void theCounterSearchesByPartOfAName() {
        JsonNode found = client.get("/carriers?name=crane&on=" + START).json();
        assertThat(usdots(found)).containsExactly(2299001);
        assertThat(usdots(client.get("/carriers?name=nobody&on=" + START).json()))
                .isEmpty();
    }

    @Test
    void aCarrierNobodyRegisteredIsA404() {
        assertThat(client.get("/carriers/9999999").status()).isEqualTo(404);
    }

    @Test
    void suspendingNeedsASupervisor() {
        Http clerk = clients.get(Role.CLERK);
        Http.Answer answer = clerk.post("/carriers/1204471/suspend?reason=unpaid&on=" + START);
        assertThat(answer.status()).isEqualTo(403);
    }

    @Test
    void aSuspensionShowsUpInTheStanding() {
        client.post("/carriers/1204471/suspend?reason=unpaid permits&on=" + START);
        assertThat(client.get("/carriers/1204471?on=" + START)
                        .json()
                        .get("standing")
                        .asText())
                .contains("suspended");
    }

    @Test
    void aSuspensionVoidsEveryPermitTheCarrierHolds() {
        String number = Bodies.issued(client);
        client.post("/carriers/1204471/suspend?reason=unpaid permits&on=" + START);
        assertThat(client.get("/permits/" + number).json().get("state").asText())
                .isEqualTo("void");
        JsonNode onTheRoad = client.get("/permits?day=" + START).json();
        assertThat(onTheRoad.isArray()).isTrue();
        assertThat(onTheRoad.size()).isZero();
    }

    @Test
    void everyVoidedPermitLandsOnTheTrailWithTheReason() {
        String number = Bodies.issued(client);
        client.post("/carriers/1204471/suspend?reason=unpaid permits&on=" + START);
        List<AuditRow> found = trail("permit", number);
        List<AuditRow> carrier = trail("carrier", "1204471");
        assertThat(actions(found)).containsExactly("permit.issued", "permit.voided");
        assertThat(found.get(1).getDetail()).isEqualTo("unpaid permits");
        assertThat(actions(carrier)).containsExactly("carrier.suspended");
    }

    @Test
    void restoringLetsTheCarrierBackAndLeavesThePermitsDead() {
        String number = Bodies.issued(client);
        client.post("/carriers/1204471/suspend?reason=unpaid permits&on=" + START);
        Http.Answer answer = client.post("/carriers/1204471/restore?on=2025-04-09");
        assertThat(answer.status()).isEqualTo(200);
        assertThat(client.get("/carriers/1204471?on=2025-04-10")
                        .json()
                        .get("standing")
                        .asText())
                .isEqualTo("in good standing");
        assertThat(client.get("/permits/" + number).json().get("state").asText())
                .isEqualTo("void");
    }
}
