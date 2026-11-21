package io.github.rkaushikdev.jeepdolly.api.routers;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.jeepdolly.api.Bodies;
import io.github.rkaushikdev.jeepdolly.api.Http;
import io.github.rkaushikdev.jeepdolly.api.Role;
import io.github.rkaushikdev.jeepdolly.api.TestService;
import io.github.rkaushikdev.jeepdolly.domain.RestrictionKind;
import io.github.rkaushikdev.jeepdolly.persistence.Database;
import io.github.rkaushikdev.jeepdolly.persistence.ReferenceCache;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.persistence.models.RestrictionRow;
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

/** Seasonal order routes, and the day the routing starts reading them. */
@WebMvcTest
@Import(TestService.class)
class ApiSeasonsTest {

    private static final String APRIL = "2025-04-08";

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

    private static Map<String, Object> order() {
        return order("25-04", 65);
    }

    /** The spring thaw order for district 4, as the district engineer signed it. */
    private static Map<String, Object> order(String number, int share) {
        return Bodies.map(
                "number",
                number,
                "region",
                "district 4",
                "kind",
                "thaw",
                "start",
                "2025-03-20",
                "end",
                "2025-05-02",
                "share",
                share,
                "signed_by",
                "R Pelletier");
    }

    /** A seasonal posting on a piece of state aid road in district 4. */
    private void postedRoad() {
        String segment = "SA148-2";
        try (UnitOfWork work = database.unitOfWork()) {
            RestrictionRow row =
                    new RestrictionRow("R-" + segment, segment, RestrictionKind.SEASONAL, "spring posting");
            row.setRegion("district 4");
            work.add(row);
            work.commit();
        }
    }

    private static Map<String, Object> overTheBridge() {
        Map<String, Object> written = Bodies.application();
        written.put("route", List.of("US201-1", "US201-2", "SA148-2", "SR43-1"));
        return written;
    }

    private static List<String> field(JsonNode found, String name) {
        assertThat(found.isArray()).as("a list").isTrue();
        List<String> made = new ArrayList<>();
        found.forEach(one -> made.add(one.get(name).textValue()));
        return made;
    }

    @Test
    void anOrderIsFiledAndReadBack() {
        Http.Answer answer = client.postJson("/seasons", order());
        assertThat(answer.status()).isEqualTo(201);
        assertThat(answer.json().get("share").intValue()).isEqualTo(65);
        assertThat(client.get("/seasons/25-04").json().get("signed_by").asText())
                .isEqualTo("R Pelletier");
    }

    @Test
    void anOverlappingOrderInOneRegionIsAConflict() {
        client.postJson("/seasons", order());
        Http.Answer answer = client.postJson("/seasons", order("25-05", 65));
        assertThat(answer.status()).isEqualTo(409);
    }

    @Test
    void anOrderThatDoesNotReduceAnythingIsRefused() {
        Http.Answer answer = client.postJson("/seasons", order("25-04", 100));
        assertThat(answer.status()).isEqualTo(422);
        assertThat(answer.json().get("code").asText()).isEqualTo("impossible");
    }

    @Test
    void theOrdersOfARegionComeBackInTheOrderTheyRan() {
        client.postJson("/seasons", order());
        Map<String, Object> autumn = order("25-08", 65);
        autumn.put("start", "2025-11-03");
        autumn.put("end", "2025-12-20");
        autumn.put("share", 80);
        client.postJson("/seasons", autumn);
        JsonNode found = client.get("/seasons?region=district 4").json();
        assertThat(field(found, "number")).containsExactly("25-04", "25-08");
    }

    @Test
    void whatIsInForceIsAskedByRegionAndDay() {
        client.postJson("/seasons", order());
        assertThat(client.get("/seasons/in-force/district 4?day=" + APRIL)
                        .json()
                        .get("number")
                        .asText())
                .isEqualTo("25-04");
        assertThat(client.get("/seasons/in-force/district 4?day=2025-06-01")
                        .json()
                        .isNull())
                .isTrue();
    }

    @Test
    void filingAnOrderNeedsASupervisor() {
        Http clerk = clients.get(Role.CLERK);
        assertThat(clerk.postJson("/seasons", order()).status()).isEqualTo(403);
    }

    @Test
    void liftingAnOrderEndsItOnTheDayAndKeepsTheRow() {
        client.postJson("/seasons", order());
        Http.Answer answer = client.post("/seasons/25-04/lift?on=" + APRIL);
        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.json().get("end").asText()).isEqualTo(APRIL);
        assertThat(client.get("/seasons/in-force/district 4?day=2025-04-20")
                        .json()
                        .isNull())
                .isTrue();
        JsonNode every = client.get("/seasons?region=district 4").json();
        assertThat(every.isArray()).isTrue();
        assertThat(every.size()).isEqualTo(1);
    }

    @Test
    void anOrderNobodySignedIsA404() {
        assertThat(client.get("/seasons/25-99").status()).isEqualTo(404);
    }

    @Test
    void theOfficeNowReadsTheOrdersWhenItAssesses() {
        postedRoad();
        client.postJson("/seasons", order());
        String reference = Bodies.filed(client, overTheBridge());
        JsonNode found = client.get("/applications/" + reference + "/assessment?on=" + APRIL)
                .json();
        assertThat(found.get("permittable").asBoolean()).isTrue();
        assertThat(field(found.get("conditions"), "code")).contains("R-02");
    }

    @Test
    void aRouteWithNoOrderRunningCarriesNoPostingCondition() {
        postedRoad();
        String reference = Bodies.filed(client, overTheBridge());
        JsonNode found = client.get("/applications/" + reference + "/assessment?on=" + APRIL)
                .json();
        assertThat(field(found.get("conditions"), "code")).doesNotContain("R-02");
    }

    @Test
    void readingAnOrderNeedsAKey() {
        Http nobody = new Http(mvc);
        assertThat(nobody.get("/seasons/25-04").status()).isEqualTo(401);
    }
}
