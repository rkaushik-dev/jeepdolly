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
import io.github.rkaushikdev.jeepdolly.services.Studies;
import io.github.rkaushikdev.jeepdolly.support.Clients;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** The structure routes and the study queue over http. */
@WebMvcTest
@Import(TestService.class)
class ApiStructuresTest {

    private static final String START = "2025-04-08";
    private static final String MONDAY = "2025-07-07";

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

    /** The excavator move over the Kennebec bridge, issued, and its permit number. */
    private static String issueOne(Http client) {
        Map<String, Object> written = Bodies.application();
        written.put("route", List.of("US201-1", "US201-2", "SA148-2", "SR43-1"));
        return Bodies.issue(client, written).json().get("number").asText();
    }

    private String queued() {
        return queued("25-F-000009");
    }

    /** A study of the Kennebec bridge in the queue for {@code reference}. */
    private String queued(String reference) {
        try (UnitOfWork work = database.unitOfWork()) {
            Studies.openStudy(work.entityManager(), reference, List.of("1247"), LocalDate.of(2025, 7, 7));
            work.commit();
        }
        return reference;
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

    @Test
    void theStructuresOfASegmentComeBackWithTheirRatings() {
        JsonNode found = client.get("/structures/on/US201-2").json();
        assertThat(field(found, "ident")).containsExactly("1247");
        assertThat(found.get(0).get("name").asText()).isEqualTo("Kennebec River");
        assertThat(found.get(0).get("longest_span").intValue()).isEqualTo(120);
        assertThat(found.get(0).get("operating_rating").asText()).isEqualTo("132,000 lb");
        assertThat(found.get(0).get("posted").isNull()).isTrue();
    }

    @Test
    void thePostedListIsTheOneTheRouterReads() {
        JsonNode found = client.get("/structures/posted").json();
        assertThat(field(found, "ident")).containsExactly("1533");
        assertThat(found.get(0).get("posted").asText()).isEqualTo("48,000 lb");
    }

    @Test
    void postingNeedsASupervisor() {
        Http clerk = clients.get(Role.CLERK);
        assertThat(clerk.post("/structures/1247/posting?weight=60000").status()).isEqualTo(403);
    }

    @Test
    void postingAStructureNamesThePermitsAlreadyCrossingIt() {
        String number = issueOne(client);
        Http.Answer answer = client.post("/structures/1247/posting?weight=60,000 lb&on=" + START);
        assertThat(answer.status()).isEqualTo(200);
        JsonNode found = answer.json();
        assertThat(found.get("structure").get("posted").asText()).isEqualTo("60,000 lb");
        assertThat(texts(found.get("permits_crossing"))).containsExactly(number);
    }

    @Test
    void aPostingDoesNotVoidWhatIsAlreadyOnTheRoad() {
        String number = issueOne(client);
        client.post("/structures/1247/posting?weight=60000&on=" + START);
        assertThat(client.get("/permits/" + number).json().get("state").asText())
                .isEqualTo("issued");
    }

    @Test
    void aStructureNobodyHasIsA404() {
        assertThat(client.post("/structures/9999/posting?weight=60000").status())
                .isEqualTo(404);
    }

    @Test
    void theStudyQueueComesBackOldestFirst() {
        queued("25-F-000002");
        queued("25-F-000001");
        JsonNode found = client.get("/structures/studies/waiting").json();
        assertThat(field(found, "reference")).containsExactly("25-F-000001", "25-F-000002");
        assertThat(found.get(0).get("state").asText()).isEqualTo("waiting");
    }

    @Test
    void anEngineerPicksAStudyUpAndTheQueueLosesIt() {
        String reference = queued();
        Http engineer = clients.get(Role.ENGINEER);
        Http.Answer answer =
                engineer.post("/structures/studies/" + reference + "/assign?engineer=R Pelletier&on=" + MONDAY);
        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.json().get("engineer").asText()).isEqualTo("R Pelletier");
        JsonNode waiting = engineer.get("/structures/studies/waiting").json();
        assertThat(waiting.isArray()).isTrue();
        assertThat(waiting.size()).isZero();
    }

    @Test
    void clearingAStudyKeepsTheConditionsTheEngineerWrote() {
        String reference = queued();
        Http engineer = clients.get(Role.ENGINEER);
        engineer.post("/structures/studies/" + reference + "/assign?engineer=R Pelletier&on=" + MONDAY);
        Http.Answer answer = engineer.post(
                "/structures/studies/" + reference + "/clear?condition=cross alone&condition=walking pace");
        assertThat(answer.json().get("state").asText()).isEqualTo("cleared");
        assertThat(texts(answer.json().get("conditions"))).containsExactly("cross alone", "walking pace");
    }

    /** One condition with a comma in it is one condition, not two. */
    @Test
    void aConditionWithACommaInItStaysOneCondition() {
        String reference = queued();
        Http engineer = clients.get(Role.ENGINEER);
        engineer.post("/structures/studies/" + reference + "/assign?engineer=R Pelletier&on=" + MONDAY);
        Http.Answer answer =
                engineer.post("/structures/studies/" + reference + "/clear?condition=cross alone, at walking pace");
        assertThat(texts(answer.json().get("conditions"))).containsExactly("cross alone, at walking pace");
    }

    @Test
    void refusingAStudyRecordsTheReason() {
        String reference = queued();
        Http engineer = clients.get(Role.ENGINEER);
        engineer.post("/structures/studies/" + reference + "/assign?engineer=R Pelletier&on=" + MONDAY);
        Http.Answer answer = engineer.post("/structures/studies/" + reference + "/refuse?reason=the truss will not");
        assertThat(answer.json().get("state").asText()).isEqualTo("refused");
    }

    @Test
    void aClerkMayNotSignAStudy() {
        String reference = queued();
        Http clerk = clients.get(Role.CLERK);
        assertThat(clerk.post("/structures/studies/" + reference + "/clear").status())
                .isEqualTo(403);
    }

    @Test
    void aStudyThatWasNeverOpenedIsA404() {
        Http engineer = clients.get(Role.ENGINEER);
        Http.Answer answer = engineer.post("/structures/studies/25-F-999999/assign?engineer=R Pelletier");
        assertThat(answer.status()).isEqualTo(404);
    }
}
