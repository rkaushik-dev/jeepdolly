package io.github.rkaushikdev.jeepdolly.api.routers;

import static io.github.rkaushikdev.jeepdolly.api.Bodies.map;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.jeepdolly.api.Http;
import io.github.rkaushikdev.jeepdolly.api.Role;
import io.github.rkaushikdev.jeepdolly.api.TestService;
import io.github.rkaushikdev.jeepdolly.persistence.Database;
import io.github.rkaushikdev.jeepdolly.persistence.ReferenceCache;
import io.github.rkaushikdev.jeepdolly.support.Clients;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** Overhead lines over the interface, and the clearance question. */
@WebMvcTest
@Import(TestService.class)
class ApiLinesTest {

    private static final String TRAVEL = "2025-04-09";
    private static final String EARLY = "2025-03-01";
    private static final String LATE = "2025-04-07";
    private static final List<String> UP_THE_KENNEBEC = List.of("US201-1", "US201-2");

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

    private static Map<String, Object> anOwner() {
        return anOwner(5);
    }

    private static Map<String, Object> anOwner(int notice) {
        return map("ident", "CMP", "name", "Central Maine Power", "telephone", "207 555 0100", "notice_days", notice);
    }

    private static Map<String, Object> aWire() {
        return aWire("L-1", "US201-2", "19'", "26'");
    }

    /** The Fairfield village wire, owned by Central Maine Power and surveyed last September. */
    private static Map<String, Object> aWire(String ident, String segment, String height, String liftTo) {
        return map(
                "ident",
                ident,
                "segment",
                segment,
                "place",
                "Fairfield village",
                "height",
                height,
                "owner",
                "CMP",
                "lift_to",
                liftTo,
                "measured",
                "2024-09-12");
    }

    private static Http seeded(Http client) {
        return seeded(client, List.of(aWire()));
    }

    private static Http seeded(Http client, List<Map<String, Object>> wires) {
        client.postJson("/lines/owners", anOwner());
        for (Map<String, Object> one : wires) {
            client.postJson("/lines", one);
        }
        return client;
    }

    private static Http.Answer clearance(Http client, String height) {
        return clearance(client, height, EARLY, UP_THE_KENNEBEC);
    }

    private static Http.Answer clearance(Http client, String height, String filed, List<String> segments) {
        String query = segments.stream().map(one -> "segments=" + one).collect(Collectors.joining("&"));
        return client.get("/lines/clearance?origin=waterville&" + query + "&height=" + height + "&travel=" + TRAVEL
                + "&filed=" + filed);
    }

    private static List<String> idents(JsonNode found) {
        assertThat(found.isArray()).as("a list").isTrue();
        List<String> made = new ArrayList<>();
        found.forEach(one -> made.add(one.get("ident").textValue()));
        return made;
    }

    @Test
    void anOwnerIsFiledAndReadBackWithNoWires() {
        Http.Answer answer = client.postJson("/lines/owners", anOwner());
        assertThat(answer.status()).isEqualTo(201);
        assertThat(answer.json().get("wires").intValue()).isZero();
    }

    @Test
    void theSameOwnerIsNotFiledTwice() {
        client.postJson("/lines/owners", anOwner());
        assertThat(client.postJson("/lines/owners", anOwner()).status()).isEqualTo(409);
    }

    @Test
    void anOwnerWantingAbsurdNoticeIsRefusedByTheSchema() {
        assertThat(client.postJson("/lines/owners", anOwner(400)).status()).isEqualTo(422);
    }

    @Test
    void aWireIsFiledAgainstAnOwnerOnFile() {
        client.postJson("/lines/owners", anOwner());
        Http.Answer answer = client.postJson("/lines", aWire());
        assertThat(answer.status()).isEqualTo(201);
        assertThat(answer.json().get("height").asText()).isEqualTo("19 ft");
        assertThat(answer.json().get("lift_to").asText()).isEqualTo("26 ft");
    }

    @Test
    void aWireAgainstAnOwnerNobodyHasIsA404() {
        assertThat(client.postJson("/lines", aWire()).status()).isEqualTo(404);
    }

    @Test
    void aWireOverARoadNobodyHasIsA404RatherThanAFiveHundred() {
        client.postJson("/lines/owners", anOwner());
        Http.Answer answer = client.postJson("/lines", aWire("L-1", "XX-9", "19'", "26'"));
        assertThat(answer.status()).isEqualTo(404);
        assertThat(answer.json().get("message").asText()).contains("XX-9");
    }

    @Test
    void theSameWireIsNotFiledTwice() {
        seeded(client);
        assertThat(client.postJson("/lines", aWire()).status()).isEqualTo(409);
    }

    @Test
    void aWireThatLiftsToBelowWhereItHangsIsRefused() {
        client.postJson("/lines/owners", anOwner());
        assertThat(client.postJson("/lines", aWire("L-1", "US201-2", "19'", "16'"))
                        .status())
                .isEqualTo(409);
    }

    @Test
    void theOwnerShowsHowManyWiresItHas() {
        seeded(client);
        assertThat(client.get("/lines/owners").json().get(0).get("wires").intValue())
                .isEqualTo(1);
    }

    @Test
    void theWiresOfOneSegmentCanBeAskedFor() {
        seeded(client, List.of(aWire(), aWire("L-2", "US2-1", "19'", "26'")));
        assertThat(idents(client.get("/lines?segment=US201-2").json())).hasSize(1);
        assertThat(idents(client.get("/lines").json())).hasSize(2);
    }

    @Test
    void oneWireCanBeReadBack() {
        seeded(client);
        assertThat(client.get("/lines/L-1").json().get("place").asText()).isEqualTo("Fairfield village");
    }

    @Test
    void aWireNobodyHasIsA404() {
        assertThat(client.get("/lines/L-9").status()).isEqualTo(404);
    }

    @Test
    void theSurveyListIsEverythingUnderAHeightLowestFirst() {
        seeded(client, List.of(aWire(), aWire("L-2", "US2-1", "17'", "26'")));
        JsonNode found = client.get("/lines/low?under=20'").json();
        assertThat(idents(found)).containsExactly("L-2", "L-1");
        assertThat(idents(client.get("/lines/low?under=16'").json())).isEmpty();
    }

    @Test
    void remeasuringReplacesWhatTheCardSaid() {
        seeded(client);
        Http.Answer answer = client.putJson(
                "/lines/L-1/measurement", map("height", "21'", "lift_to", "27'", "measured", "2025-10-02"));
        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.json().get("height").asText()).isEqualTo("21 ft");
        assertThat(answer.json().get("measured").asText()).isEqualTo("2025-10-02");
    }

    @Test
    void remeasuringCanTakeTheLiftAwayWhenTheSpanIsRebuilt() {
        seeded(client);
        Http.Answer answer = client.putJson("/lines/L-1/measurement", map("height", "28'", "measured", "2025-10-02"));
        assertThat(answer.json().has("lift_to")).isTrue();
        assertThat(answer.json().get("lift_to").isNull()).isTrue();
    }

    @Test
    void remeasuringToALiftBelowTheWireIsRefused() {
        seeded(client);
        Http.Answer answer = client.putJson(
                "/lines/L-1/measurement", map("height", "21'", "lift_to", "20'", "measured", "2025-10-02"));
        assertThat(answer.status()).isEqualTo(409);
    }

    @Test
    void aWireNobodyHasCannotBeRemeasured() {
        assertThat(client.putJson("/lines/L-9/measurement", map("height", "21'", "measured", "2025-10-02"))
                        .status())
                .isEqualTo(404);
    }

    @Test
    void aShortLoadClearsEverythingAndRingsNobody() {
        seeded(client);
        JsonNode found = clearance(client, "13'6").json();
        assertThat(found.get("clear").booleanValue()).isTrue();
        assertThat(found.get("lifts").isArray()).isTrue();
        assertThat(found.get("lifts").size()).isZero();
        assertThat(found.get("wanted").asText()).isEqualTo("13 ft 6 in");
    }

    @Test
    void aTallLoadGetsALiftWithATelephoneNumber() {
        seeded(client);
        JsonNode found = clearance(client, "20'").json();
        assertThat(found.get("clear").booleanValue()).isTrue();
        assertThat(found.get("lifts").get(0).get("telephone").asText()).isEqualTo("207 555 0100");
        assertThat(found.get("lifts").get(0).get("notice_by").asText()).isEqualTo("2025-04-02");
    }

    @Test
    void aLoadAskingTooLateIsNotClear() {
        seeded(client);
        JsonNode found = clearance(client, "20'", LATE, UP_THE_KENNEBEC).json();
        assertThat(found.get("clear").booleanValue()).isFalse();
        assertThat(found.get("blocked").get(0).get("reason").asText()).contains("working days notice");
    }

    @Test
    void aLoadNothingCanBeDoneAboutSaysBothHeights() {
        seeded(client, List.of(aWire("L-1", "US201-2", "19'", null)));
        JsonNode found = clearance(client, "20'").json();
        assertThat(found.get("blocked").get(0).get("reason").asText()).contains("hangs at 19 ft");
    }

    @Test
    void aRouteThatMissesTheWireIsClear() {
        seeded(client);
        JsonNode found =
                clearance(client, "20'", EARLY, List.of("I95-1", "I95-2")).json();
        assertThat(found.get("clear").booleanValue()).isTrue();
        assertThat(found.get("lifts").isArray()).isTrue();
        assertThat(found.get("lifts").size()).isZero();
    }

    @Test
    void aRouteThatCannotBeDrivenIsRefused() {
        seeded(client);
        assertThat(clearance(client, "20'", EARLY, List.of("SR8-1")).status()).isEqualTo(422);
    }

    @Test
    void aClerkCannotFileAWire() {
        Http clerk = clients.get(Role.CLERK);
        assertThat(clerk.postJson("/lines/owners", anOwner()).status()).isEqualTo(403);
    }

    @Test
    void aReaderCanAskTheClearanceQuestion() {
        seeded(clients.get(Role.SUPERVISOR));
        Http reader = clients.get(Role.READER);
        assertThat(clearance(reader, "20'").status()).isEqualTo(200);
    }
}
