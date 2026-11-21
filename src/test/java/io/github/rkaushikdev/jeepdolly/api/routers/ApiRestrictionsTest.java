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

/** Closing a road and opening it again. */
@WebMvcTest
@Import(TestService.class)
class ApiRestrictionsTest {

    private static final String START = "2025-04-08";
    private static final List<String> ACROSS = List.of("US201-1", "US201-2", "SA148-2", "SR43-1");

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

    private static Map<String, Object> closure() {
        return closure("R-1", "SA148-2");
    }

    /** Culvert work closing a segment, with any field replaced. */
    private static Map<String, Object> closure(String ident, String segment, Object... changes) {
        Map<String, Object> fields = map("ident", ident, "segment", segment, "kind", "closure", "note", "culvert work");
        fields.putAll(map(changes));
        return fields;
    }

    /** The excavator move over the Kennebec and on to Pittsfield, issued, and its permit number. */
    private static String issueAcross(Http client) {
        Map<String, Object> written = Bodies.application();
        written.put("route", ACROSS);
        return Bodies.issue(client, written).json().get("number").asText();
    }

    private static List<String> texts(JsonNode list) {
        assertThat(list.isArray()).as("a list").isTrue();
        List<String> made = new ArrayList<>();
        list.forEach(one -> made.add(one.textValue()));
        return made;
    }

    private static List<String> idents(JsonNode found) {
        assertThat(found.isArray()).as("a list").isTrue();
        List<String> made = new ArrayList<>();
        found.forEach(one -> made.add(one.get("ident").textValue()));
        return made;
    }

    @Test
    void aRoadIsClosedInOneRequest() {
        Http.Answer answer = client.postJson("/restrictions?on=" + START, closure());
        assertThat(answer.status()).isEqualTo(201);
        assertThat(answer.json().get("segment").asText()).isEqualTo("SA148-2");
        assertThat(texts(answer.json().get("permits_crossing"))).isEmpty();
    }

    @Test
    void theAnswerNamesThePermitsAlreadyCrossingIt() {
        String number = issueAcross(client);
        Http.Answer answer = client.postJson("/restrictions?on=" + START, closure());
        assertThat(texts(answer.json().get("permits_crossing"))).containsExactly(number);
    }

    @Test
    void aClosureShowsUpOnTheSegmentItWasPutOn() {
        client.postJson("/restrictions?on=" + START, closure());
        JsonNode found = client.get("/restrictions/on/SA148-2?day=" + START).json();
        assertThat(idents(found)).containsExactly("R-1");
        assertThat(idents(client.get("/restrictions/on/US2-1?day=" + START).json()))
                .isEmpty();
    }

    @Test
    void aClosureChangesWhatTheRouterWillDo() {
        Map<String, Object> load = map("width", "12'", "height", "13'6", "length", "82'", "gross", "94000");
        String find = "/routes/find?origin=waterville&destination=pittsfield&day=" + START;
        JsonNode before = client.postJson(find, load).json();
        client.postJson("/restrictions?on=" + START, closure("R-1", "I95-2"));
        JsonNode after = client.postJson(find, load).json();
        assertThat(texts(before.get("segments"))).containsExactly("I95-1", "I95-2");
        assertThat(texts(after.get("segments"))).doesNotContain("I95-2");
    }

    @Test
    void theSameIdentTwiceIsAConflict() {
        client.postJson("/restrictions?on=" + START, closure());
        assertThat(client.postJson("/restrictions?on=" + START, closure()).status())
                .isEqualTo(409);
    }

    @Test
    void aSegmentThatIsNotOnTheLogIsA404() {
        Http.Answer answer = client.postJson("/restrictions?on=" + START, closure("R-1", "NOPE-1"));
        assertThat(answer.status()).isEqualTo(404);
    }

    @Test
    void closingARoadNeedsASupervisor() {
        Http clerk = clients.get(Role.CLERK);
        assertThat(clerk.postJson("/restrictions?on=" + START, closure()).status())
                .isEqualTo(403);
    }

    @Test
    void aClearanceCarriesItsHeight() {
        Map<String, Object> written = closure("R-2", "US2-1", "kind", "clearance", "note", "arch");
        written.put("height", "13'6");
        assertThat(client.postJson("/restrictions?on=" + START, written).status())
                .isEqualTo(201);
        JsonNode found = client.get("/restrictions/on/US2-1?day=" + START).json();
        assertThat(found.get(0).get("kind").asText()).isEqualTo("clearance");
    }

    @Test
    void openingTheRoadAgainTakesTheRowOff() {
        client.postJson("/restrictions?on=" + START, closure());
        Http.Answer answer = client.delete("/restrictions/R-1?on=" + START);
        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.json().get("ident").asText()).isEqualTo("R-1");
        assertThat(idents(client.get("/restrictions/on/SA148-2?day=" + START).json()))
                .isEmpty();
    }

    @Test
    void openingSomethingThatIsNotClosedIsA404() {
        assertThat(client.delete("/restrictions/R-9").status()).isEqualTo(404);
    }

    @Test
    void bothEndsOfTheClosureLandOnTheTrail() {
        client.postJson("/restrictions?on=" + START, closure());
        client.delete("/restrictions/R-1?on=" + START);
        List<AuditRow> found;
        try (UnitOfWork work = database.unitOfWork()) {
            found = Audit.forDocument(work.entityManager(), "segment", "SA148-2");
        }
        assertThat(found.stream().map(AuditRow::getAction).toList()).containsExactly("road.closed", "road.opened");
        assertThat(found.get(0).getDetail()).contains("culvert work");
    }
}
