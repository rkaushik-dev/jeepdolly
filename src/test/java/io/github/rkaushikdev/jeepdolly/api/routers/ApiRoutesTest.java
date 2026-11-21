package io.github.rkaushikdev.jeepdolly.api.routers;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.api.Bodies;
import io.github.rkaushikdev.jeepdolly.api.Http;
import io.github.rkaushikdev.jeepdolly.api.Role;
import io.github.rkaushikdev.jeepdolly.api.TestService;
import io.github.rkaushikdev.jeepdolly.domain.RestrictionKind;
import io.github.rkaushikdev.jeepdolly.permits.ApplicationState;
import io.github.rkaushikdev.jeepdolly.persistence.Database;
import io.github.rkaushikdev.jeepdolly.persistence.ReferenceCache;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.persistence.models.RestrictionRow;
import io.github.rkaushikdev.jeepdolly.support.Clients;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** The route routes, which answer questions and change nothing. */
@WebMvcTest
@Import(TestService.class)
class ApiRoutesTest {

    private static final String DAY = "2025-04-08";
    private static final List<String> ACROSS = List.of("US201-1", "US201-2", "SA148-2", "SR43-1");
    private static final String FIND = "/routes/find?origin=waterville&destination=pittsfield&day=" + DAY;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ReferenceCache cache;

    private Database database;
    private Http client;

    @BeforeEach
    void aReaderOverASeededDatabase() {
        database = Databases.seeded();
        cache.reset();
        client = Clients.keyed(new Http(mvc), database, Role.READER);
    }

    private static Map<String, Object> load() {
        return Bodies.map("width", "12'", "height", "13'6", "length", "82'", "gross", "94000");
    }

    private void close(String segment) {
        try (UnitOfWork work = database.unitOfWork()) {
            work.add(new RestrictionRow("R-" + segment, segment, RestrictionKind.CLOSURE, "bridge out"));
            work.commit();
        }
    }

    private static String query(List<String> segments, String origin) {
        return Stream.concat(
                        segments.stream().map(one -> "segments=" + one), Stream.of("origin=" + origin, "day=" + DAY))
                .collect(Collectors.joining("&"));
    }

    private static String check() {
        return "/routes/check?" + query(ACROSS, "waterville");
    }

    private static List<String> texts(JsonNode list) {
        List<String> made = new ArrayList<>();
        list.forEach(one -> made.add(one.asText()));
        return made;
    }

    @Test
    void aWrittenRouteIsWalkedAndComesBackClear() {
        Http.Answer answer = client.postJson(check(), load());
        assertThat(answer.status()).isEqualTo(200);
        JsonNode found = answer.json();
        assertThat(found.get("clear").asBoolean()).isTrue();
        assertThat(found.get("distance").asText()).isEqualTo("35.20 mi");
        assertThat(found.get("chargeable_miles").asInt()).isEqualTo(36);
        assertThat(found.get("towns").get(0).asText()).isEqualTo("Waterville");
        assertThat(found.get("obstructions").size()).isZero();
    }

    @Test
    void theNarrativeIsWhatWouldPrintOnThePermit() {
        JsonNode found = client.postJson(check(), load()).json();
        assertThat(found.get("narrative").asText()).startsWith("from Waterville, US 201 to Fairfield");
    }

    @Test
    void aClosureOnTheWrittenRouteComesBackAsAnObstruction() {
        close("SA148-2");
        JsonNode found = client.postJson(check(), load()).json();
        assertThat(found.get("clear").asBoolean()).isFalse();
        JsonNode trouble = found.get("obstructions").get(0);
        assertThat(trouble.get("segment").asText()).isEqualTo("SA148-2");
        assertThat(trouble.get("kind").asText()).isEqualTo("closure");
        assertThat(trouble.get("fatal").asBoolean()).isTrue();
    }

    @Test
    void aRouteThatDoesNotJoinIsRefused() {
        Http.Answer answer =
                client.postJson("/routes/check?" + query(List.of("US201-1", "SR43-1"), "waterville"), load());
        assertThat(answer.status()).isEqualTo(422);
        assertThat(answer.json().get("code").asText()).isEqualTo("impossible");
    }

    @Test
    void theRouterFindsTheCheapestRouteForALoad() {
        Http.Answer answer = client.postJson(FIND, load());
        assertThat(answer.status()).isEqualTo(200);
        assertThat(texts(answer.json().get("segments"))).containsExactly("I95-1", "I95-2");
        assertThat(answer.json().get("clear").asBoolean()).isTrue();
    }

    @Test
    void aClosurePushesTheFoundRouteOntoTheStateRoads() {
        close("I95-2");
        JsonNode found = client.postJson(FIND, load()).json();
        assertThat(texts(found.get("segments"))).doesNotContain("I95-2");
        assertThat(found.get("clear").asBoolean()).isTrue();
    }

    @Test
    void aLoadWithNowhereToGoIsA404ThatNamesBothEnds() {
        close("I95-2");
        close("SR43-1");
        Http.Answer answer = client.postJson(FIND, load());
        assertThat(answer.status()).isEqualTo(404);
        assertThat(Http.asMap(answer.json().get("facts")))
                .isEqualTo(Map.of("origin", "waterville", "destination", "pittsfield"));
    }

    @Test
    void everywhereALoadCanGetToComesBackAsAList() {
        Http.Answer answer = client.postJson("/routes/reachable?origin=skowhegan&day=" + DAY, load());
        assertThat(answer.status()).isEqualTo(200);
        assertThat(texts(answer.json())).contains("newport", "anson");
    }

    @Test
    void aClosureCutsOffWhatIsBehindIt() {
        close("US2-2");
        JsonNode places = client.postJson("/routes/reachable?origin=newport&day=" + DAY, load())
                .json();
        assertThat(texts(places)).containsExactly("newport");
    }

    @Test
    void aClearanceIsOnlyInTheWayOfALoadThatIsTooTall() {
        try (UnitOfWork work = database.unitOfWork()) {
            RestrictionRow arch = new RestrictionRow("R-1", "US201-2", RestrictionKind.CLEARANCE, "stone arch");
            arch.setHeight(Inches.of(13));
            work.add(arch);
            work.commit();
        }
        JsonNode tall = client.postJson(check(), load()).json();
        Map<String, Object> shorter = load();
        shorter.put("height", "12'");
        JsonNode lower = client.postJson(check(), shorter).json();
        assertThat(tall.get("clear").asBoolean()).isFalse();
        assertThat(lower.get("clear").asBoolean()).isTrue();
        assertThat(tall.get("obstructions").get(0).get("needed").asText()).isEqualTo("13 ft 6 in");
    }

    @Test
    void askingAboutARouteWritesNothingDown() {
        client.postJson(check(), load());
        client.postJson(FIND, load());
        try (UnitOfWork work = database.unitOfWork()) {
            assertThat(work.permits().onTheRoad(LocalDate.of(2025, 4, 8))).isEmpty();
            assertThat(work.applications().inState(ApplicationState.DRAFT)).isEmpty();
        }
    }

    @Test
    void aRequestWithNoKeyIsRefused() {
        assertThat(new Http(mvc).postJson(check(), load()).status()).isEqualTo(401);
    }

    @Test
    void theDefaultLoadIsALegalOneThatFitsEverywhere() {
        JsonNode found = client.postJson(check(), Map.of()).json();
        assertThat(found.get("clear").asBoolean()).isTrue();
        assertThat(found.get("distance").asText()).isEqualTo("35.20 mi");
    }
}
