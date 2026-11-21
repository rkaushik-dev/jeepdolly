package io.github.rkaushikdev.jeepdolly.api.routers;

import static io.github.rkaushikdev.jeepdolly.api.Bodies.FILED;
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
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** Filing a published table, and the day it starts working. */
@WebMvcTest
@Import(TestService.class)
class ApiTariffTest {

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

    private static Map<String, Object> table() {
        return table("2025-06-01", "104,000 lb");
    }

    /** An arterial weight table as the statute publishes it. */
    private static Map<String, Object> table(String effective, String gross) {
        return Bodies.map(
                "highway_class",
                "arterial",
                "effective",
                effective,
                "single",
                "23000",
                "tandem",
                "42000",
                "tridem",
                "49000",
                "quad",
                "57000",
                "beyond",
                "7000",
                "gross",
                gross,
                "tire_rate",
                600);
    }

    private static JsonNode forClass(JsonNode found, String highwayClass) {
        return StreamSupport.stream(found.spliterator(), false)
                .filter(one -> highwayClass.equals(one.get("highway_class").textValue()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void whatIsInForceTodayIsOneTablePerClass() {
        JsonNode found = client.get("/tariff/statutory?day=" + FILED).json();
        assertThat(found.isArray()).isTrue();
        assertThat(found.size()).isEqualTo(4);
        JsonNode arterial = forClass(found, "arterial");
        assertThat(arterial.get("gross").asText()).isEqualTo("100,000 lb");
        assertThat(arterial.get("effective").asText()).isEqualTo("2024-01-01");
    }

    @Test
    void aNewTableIsFiledAndTakesEffectOnItsOwnDay() {
        Http.Answer answer = client.postJson("/tariff/statutory", table());
        assertThat(answer.status()).isEqualTo(201);
        assertThat(answer.json().get("gross").asText()).isEqualTo("104,000 lb");
        JsonNode before = client.get("/tariff/statutory?day=" + FILED).json();
        JsonNode after = client.get("/tariff/statutory?day=2025-07-01").json();
        assertThat(forClass(before, "arterial").get("gross").asText()).isEqualTo("100,000 lb");
        assertThat(forClass(after, "arterial").get("gross").asText()).isEqualTo("104,000 lb");
    }

    @Test
    void twoTablesForOneClassOnOneDayIsAConflict() {
        client.postJson("/tariff/statutory", table());
        Http.Answer answer = client.postJson("/tariff/statutory", table());
        assertThat(answer.status()).isEqualTo(409);
        assertThat(answer.json().get("facts").get("highway_class").asText()).isEqualTo("arterial");
    }

    @Test
    void filingATableNeedsASupervisor() {
        Http clerk = clients.get(Role.CLERK);
        assertThat(clerk.postJson("/tariff/statutory", table()).status()).isEqualTo(403);
    }

    @Test
    void filingATableLandsOnTheTrail() {
        client.postJson("/tariff/statutory", table());
        try (UnitOfWork work = database.unitOfWork()) {
            List<AuditRow> found = Audit.forDocument(work.entityManager(), "tariff", "arterial 2025-06-01");
            assertThat(found).extracting(AuditRow::getAction).containsExactly("tariff.filed");
            assertThat(found.get(0).getDetail()).isEqualTo("gross 104,000 lb");
        }
    }

    @Test
    void theHistoryOfOneClassComesBackOldestFirst() {
        client.postJson("/tariff/statutory", table());
        JsonNode found = client.get("/tariff/statutory/arterial").json();
        assertThat(found.isArray()).isTrue();
        List<String> effective = new ArrayList<>();
        found.forEach(one -> effective.add(one.get("effective").textValue()));
        assertThat(effective).containsExactly("2024-01-01", "2025-06-01");
    }

    @Test
    void aNewTableChangesWhatAnAssessmentSays() {
        Map<String, Object> written = Bodies.application();
        written.put("route", List.of("US201-1", "US201-2", "SA148-2", "SR43-1"));
        String reference = Bodies.filed(client, written);
        JsonNode before = client.get("/applications/" + reference + "/assessment?on=" + FILED)
                .json();
        Map<String, Object> tightened = table("2025-04-01", "70000");
        tightened.put("highway_class", "state aid");
        client.postJson("/tariff/statutory", tightened);
        JsonNode after = client.get("/applications/" + reference + "/assessment?on=" + FILED)
                .json();
        assertThat(before.get("permittable").asBoolean()).isTrue();
        String total = before.get("fees").get("total").textValue();
        String tightenedTotal = after.get("fees").get("total").textValue();
        assertThat(total).isNotNull().isNotEqualTo(tightenedTotal);
    }

    @Test
    void aWeightThatCannotBeReadIsRefused() {
        Map<String, Object> written = table();
        written.put("gross", "heavy");
        Http.Answer answer = client.postJson("/tariff/statutory", written);
        assertThat(answer.status()).isEqualTo(422);
        assertThat(answer.json().get("code").asText()).isEqualTo("unreadable");
    }

    @Test
    void aClassOfRoadThatDoesNotExistIsRefused() {
        Map<String, Object> written = table();
        written.put("highway_class", "goat track");
        Http.Answer answer = client.postJson("/tariff/statutory", written);
        assertThat(answer.status()).isEqualTo(422);
        assertThat(answer.json().get("facts").get("written").asText()).isEqualTo("goat track");
    }
}
