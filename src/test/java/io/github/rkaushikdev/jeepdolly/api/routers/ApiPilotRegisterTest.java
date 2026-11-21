package io.github.rkaushikdev.jeepdolly.api.routers;

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

/** The pilot car register over the interface. */
@WebMvcTest
@Import(TestService.class)
class ApiPilotRegisterTest {

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

    private static Map<String, Object> aCompany() {
        return Bodies.map(
                "ident", "PC-2", "name", "Somerset Escort Service", "town", "Skowhegan", "telephone", "207 555 0188");
    }

    private static Map<String, Object> aCard() {
        return aCard("PC-2", List.of());
    }

    private static Map<String, Object> aCard(String company, List<String> endorsements) {
        return Bodies.map(
                "card",
                "C-2001",
                "name",
                "J Hebert",
                "company",
                company,
                "issued",
                "2025-01-06",
                "expires",
                "2028-01-05",
                "endorsements",
                List.copyOf(endorsements));
    }

    /** A JSON boolean as a boolean, refusing anything that only looks like one. */
    private static boolean flag(JsonNode value) {
        assertThat(value.isBoolean()).as("a boolean").isTrue();
        return value.booleanValue();
    }

    private static List<String> texts(JsonNode list) {
        assertThat(list.isArray()).as("a list").isTrue();
        List<String> made = new ArrayList<>();
        list.forEach(one -> made.add(one.textValue()));
        return made;
    }

    private static List<String> field(JsonNode found, String name) {
        assertThat(found.isArray()).as("a list").isTrue();
        List<String> made = new ArrayList<>();
        found.forEach(one -> made.add(one.get(name).textValue()));
        return made;
    }

    private List<AuditRow> trail(String kind, String reference) {
        try (UnitOfWork work = database.unitOfWork()) {
            return Audit.forDocument(work.entityManager(), kind, reference);
        }
    }

    @Test
    void theSeededCompanyIsOnTheRegister() {
        JsonNode found = client.get("/pilots/companies").json();
        assertThat(field(found, "ident")).containsExactly("PC-1");
        assertThat(found.get(0).get("cards").intValue()).isEqualTo(3);
    }

    @Test
    void aNewCompanyCanBeFiledAndReadBack() {
        Http.Answer answer = client.postJson("/pilots/companies", aCompany());
        assertThat(answer.status()).isEqualTo(201);
        assertThat(answer.json().get("cards").isInt()).isTrue();
        assertThat(answer.json().get("cards").intValue()).isZero();
        assertThat(client.get("/pilots/companies/PC-2").json().get("name").asText())
                .isEqualTo("Somerset Escort Service");
    }

    @Test
    void theSameCompanyIsNotFiledTwice() {
        client.postJson("/pilots/companies", aCompany());
        assertThat(client.postJson("/pilots/companies", aCompany()).status()).isEqualTo(409);
    }

    @Test
    void aCompanyNobodyHasIsA404() {
        assertThat(client.get("/pilots/companies/PC-9").status()).isEqualTo(404);
    }

    @Test
    void aCardIsIssuedAgainstACompanyOnFile() {
        client.postJson("/pilots/companies", aCompany());
        Http.Answer answer = client.postJson("/pilots/cards", aCard());
        assertThat(answer.status()).isEqualTo(201);
        assertThat(answer.json().get("company").asText()).isEqualTo("PC-2");
        assertThat(texts(answer.json().get("endorsements"))).isEmpty();
    }

    @Test
    void aCardAgainstACompanyNobodyHasIsA404() {
        assertThat(client.postJson("/pilots/cards", aCard("PC-9", List.of())).status())
                .isEqualTo(404);
    }

    @Test
    void theSameCardNumberIsNotIssuedTwice() {
        client.postJson("/pilots/companies", aCompany());
        client.postJson("/pilots/cards", aCard());
        assertThat(client.postJson("/pilots/cards", aCard()).status()).isEqualTo(409);
    }

    @Test
    void theEndorsementsComeBackSorted() {
        client.postJson("/pilots/companies", aCompany());
        Http.Answer answer = client.postJson("/pilots/cards", aCard("PC-2", List.of("police liaison", "height pole")));
        assertThat(texts(answer.json().get("endorsements"))).containsExactly("height pole", "police liaison");
    }

    @Test
    void anEndorsementThisOfficeDoesNotIssueIsRefused() {
        client.postJson("/pilots/companies", aCompany());
        Http.Answer answer = client.postJson("/pilots/cards", aCard("PC-2", List.of("railroad flagging")));
        assertThat(answer.status()).isEqualTo(422);
    }

    @Test
    void theCardsOfOneCompanyCanBeAskedForOnTheirOwn() {
        client.postJson("/pilots/companies", aCompany());
        client.postJson("/pilots/cards", aCard());
        JsonNode found = client.get("/pilots/cards?company=PC-2").json();
        assertThat(field(found, "card")).containsExactly("C-2001");
        JsonNode every = client.get("/pilots/cards").json();
        assertThat(every.isArray()).isTrue();
        assertThat(every.size()).isEqualTo(4);
    }

    @Test
    void suspendingACompanyShowsOnItAndOnTheTrail() {
        Http.Answer answer = client.post("/pilots/companies/PC-1/suspension?reason=insurance lapsed");
        assertThat(flag(answer.json().get("suspended"))).isTrue();
        List<AuditRow> found = trail("pilot company", "PC-1");
        assertThat(found.get(0).getAction()).isEqualTo("pilot.company_suspended");
        assertThat(found.get(0).getDetail()).isEqualTo("insurance lapsed");
    }

    @Test
    void aCompanyCanBeLetBackByTheSameSwitch() {
        client.post("/pilots/companies/PC-1/suspension");
        Http.Answer answer = client.post("/pilots/companies/PC-1/suspension?suspended=false");
        assertThat(flag(answer.json().get("suspended"))).isFalse();
    }

    @Test
    void suspendingAnOperatorShowsOnTheCard() {
        Http.Answer answer = client.post("/pilots/cards/C-1001/suspension?reason=incident on US 2");
        assertThat(flag(answer.json().get("suspended"))).isTrue();
        List<AuditRow> found = trail("pilot card", "C-1001");
        assertThat(found.get(0).getAction()).isEqualTo("pilot.card_suspended");
    }

    @Test
    void anOperatorCanBeLetBackToo() {
        client.post("/pilots/cards/C-1001/suspension");
        assertThat(flag(client.post("/pilots/cards/C-1001/suspension?suspended=false")
                        .json()
                        .get("suspended")))
                .isFalse();
    }

    @Test
    void aCardNobodyHasCannotBeSuspended() {
        assertThat(client.post("/pilots/cards/C-9999/suspension").status()).isEqualTo(404);
    }

    @Test
    void theCardsRunningOutComeBackSoonestFirst() {
        JsonNode found =
                client.get("/pilots/expiring?start=2024-01-01&end=2027-01-01").json();
        assertThat(field(found, "card")).containsExactly("C-1003", "C-1001", "C-1002");
    }

    @Test
    void aSuspendedCardIsOffTheRenewalList() {
        client.post("/pilots/cards/C-1003/suspension");
        JsonNode found =
                client.get("/pilots/expiring?start=2024-01-01&end=2027-01-01").json();
        assertThat(field(found, "card")).doesNotContain("C-1003");
    }

    @Test
    void aWindowWithNothingInItComesBackEmpty() {
        JsonNode found =
                client.get("/pilots/expiring?start=2030-01-01&end=2030-12-31").json();
        assertThat(found.isArray()).isTrue();
        assertThat(found.size()).isZero();
    }

    @Test
    void aClerkCannotSuspendACompany() {
        Http clerk = clients.get(Role.CLERK);
        assertThat(clerk.post("/pilots/companies/PC-1/suspension").status()).isEqualTo(403);
    }

    @Test
    void aReaderCannotIssueACard() {
        Http reader = clients.get(Role.READER);
        assertThat(reader.postJson("/pilots/cards", aCard("PC-1", List.of())).status())
                .isEqualTo(403);
    }
}
