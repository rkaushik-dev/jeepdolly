package io.github.rkaushikdev.jeepdolly.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
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

/**
 * One week in the office, end to end, through the interface only.
 *
 * <p>Every other test holds one rule still and pushes on it. This one runs the week the office
 * actually has: applications in, a road shut on the Tuesday, a thaw order signed on the Wednesday, a
 * carrier suspended on the Thursday, and the reports that get initialled on the Friday. It is
 * written against the http interface and nothing else, so a refactor that breaks a private helper
 * goes unnoticed here and a refactor that breaks the office does not.
 */
@WebMvcTest
@Import(TestService.class)
class WeekInTheOfficeTest {

    private static final String MONDAY = "2025-04-07";
    private static final String TUESDAY = "2025-04-08";
    private static final String WEDNESDAY = "2025-04-09";
    private static final String THURSDAY = "2025-04-10";
    private static final String FRIDAY = "2025-04-11";
    private static final List<String> ACROSS = List.of("US201-1", "US201-2", "SA148-2", "SR43-1");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ReferenceCache cache;

    private Database database;
    private Http clerk;
    private Http boss;

    /** Two people at two screens, over one database. */
    @BeforeEach
    void aClerkAndASupervisorOverASeededDatabase() {
        database = Databases.seeded();
        cache.reset();
        Map<Role, Http> office = Clients.clientsFor(new Http(mvc), database);
        clerk = office.get(Role.CLERK);
        boss = office.get(Role.SUPERVISOR);
    }

    /** The excavator move, running Tuesday to Friday. */
    private static Map<String, Object> application() {
        Map<String, Object> written = Bodies.application();
        written.put("start", TUESDAY);
        written.put("end", FRIDAY);
        return written;
    }

    private static Map<String, Object> application(List<String> route) {
        Map<String, Object> written = application();
        written.put("route", route);
        return written;
    }

    private static Map<String, Object> startingOn(String start) {
        Map<String, Object> written = application();
        written.put("start", start);
        return written;
    }

    private static List<String> texts(JsonNode list) {
        List<String> made = new ArrayList<>();
        list.forEach(one -> made.add(one.asText()));
        return made;
    }

    @Test
    void aWeekInTheOffice() {
        // Monday. Two haulers ring in. One is a walk in and gets a permit at the window. The other
        // wants a route the office has to find for him.
        Http.Answer walkIn = clerk.postJson("/permits/walk-in?on=" + MONDAY, application());
        assertThat(walkIn.status()).isEqualTo(201);
        String first = walkIn.json().get("number").asText();
        assertThat(first).isEqualTo("25-P-000001");

        Http.Answer second = clerk.postJson("/applications?filed=" + MONDAY, application(ACROSS));
        String reference = second.json().get("reference").asText();
        clerk.post("/applications/" + reference + "/submit?on=" + MONDAY);
        JsonNode quoted = clerk.get("/applications/" + reference + "/assessment?on=" + TUESDAY)
                .json();
        assertThat(quoted.get("permittable").asBoolean()).isTrue();
        assertThat(texts(quoted.get("route"))).isEqualTo(ACROSS);

        // Tuesday. The district rings to say the Carrabassett culvert is out. The office shuts
        // SA148-2 and finds out who is already crossing it.
        Http.Answer shut = boss.postJson(
                "/restrictions?on=" + TUESDAY,
                Bodies.map(
                        "ident", "R-culvert", "segment", "SA148-2", "kind", "closure", "note", "culvert washed out"));
        assertThat(shut.status()).isEqualTo(201);
        assertThat(shut.json().get("permits_crossing").isArray()).isTrue();
        assertThat(shut.json().get("permits_crossing").size()).isZero();

        // The application that named that road cannot be issued now.
        Http.Answer refused = clerk.post("/permits?reference=" + reference + "&on=" + TUESDAY);
        assertThat(refused.status()).isEqualTo(409);
        assertThat(texts(refused.json().get("facts").get("reasons"))).anyMatch(one -> one.contains("SA148-2"));

        // Wednesday. The district signs a thaw order for district four, and the office files it.
        // Nothing about it is retrospective.
        Http.Answer order = boss.postJson(
                "/seasons",
                Bodies.map(
                        "number",
                        "25-04",
                        "region",
                        "district 4",
                        "kind",
                        "thaw",
                        "start",
                        WEDNESDAY,
                        "end",
                        "2025-05-02",
                        "share",
                        65,
                        "signed_by",
                        "R Pelletier"));
        assertThat(order.status()).isEqualTo(201);
        assertThat(boss.get("/seasons/in-force/district 4?day=" + WEDNESDAY)
                        .json()
                        .get("share")
                        .asInt())
                .isEqualTo(65);

        // The hauler rings back and takes the interstate instead, which is not in district four
        // and not shut.
        Http.Answer replacement = clerk.postJson("/applications?filed=" + WEDNESDAY, startingOn(WEDNESDAY));
        String onward = replacement.json().get("reference").asText();
        clerk.post("/applications/" + onward + "/submit?on=" + WEDNESDAY);
        Http.Answer issued = clerk.post("/permits?reference=" + onward + "&on=" + WEDNESDAY);
        assertThat(issued.status()).isEqualTo(201);
        assertThat(texts(issued.json().get("route"))).containsExactly("I95-1", "I95-2");

        // Thursday. The carrier is suspended for unpaid permits, which kills both live permits and
        // says so.
        Http.Answer suspended = boss.post("/carriers/1204471/suspend?reason=unpaid permits&on=" + THURSDAY);
        assertThat(suspended.status()).isEqualTo(200);
        assertThat(suspended.json().get("standing").asText()).contains("suspended");
        assertThat(clerk.get("/permits/" + first).json().get("state").asText()).isEqualTo("void");
        JsonNode onTheRoad = clerk.get("/permits?day=" + THURSDAY).json();
        assertThat(onTheRoad.isArray()).isTrue();
        assertThat(onTheRoad.size()).isZero();

        // And nothing more can be written for them.
        Http.Answer blocked = clerk.postJson("/permits/walk-in?on=" + THURSDAY, startingOn(THURSDAY));
        assertThat(blocked.status()).isEqualTo(409);

        // Friday. The reports. Two permits were written this week, both of them are void, and the
        // money is still counted because it was taken.
        JsonNode week =
                clerk.get("/reports/issuance?day=" + MONDAY + "&to=" + FRIDAY).json();
        assertThat(week.get("count").asInt()).isEqualTo(2);
        assertThat(Http.asMap(week.get("by_series"))).isEqualTo(Map.of("P", 2));
        assertThat(week.get("taken").asText()).isNotEqualTo("$0.00");

        JsonNode revenue =
                clerk.get("/reports/revenue?start=" + MONDAY + "&end=" + FRIDAY).json();
        assertThat(revenue.get("permits").asInt()).isEqualTo(2);
        assertThat(revenue.get("by_kind").get("base fee").asText()).isEqualTo("$80.00");

        // The culvert closure carried no dates, so it is in force until somebody lifts it and it
        // never appears as a change.
        JsonNode roads = clerk.get("/reports/roads?day=" + MONDAY + "&days=7").json();
        JsonNode changes = roads.get("changes");
        assertThat(changes.get(changes.size() - 1).asText()).isEqualTo("nothing changes");
        List<String> standing = new ArrayList<>();
        roads.get("standing").forEach(one -> standing.add(one.get("segment").asText()));
        assertThat(standing).containsExactly("SA148-2");

        JsonNode numbers = clerk.get("/metrics.json?day=" + FRIDAY).json();
        assertThat(numbers.get("permits_void").asInt()).isEqualTo(2);
        assertThat(numbers.get("permits_issued_week").asInt()).isEqualTo(2);
        assertThat(numbers.get("restrictions").asInt()).isEqualTo(1);
    }

    @Test
    void theWeekLeavesATrailThatReadsInOrder() {
        clerk.postJson("/permits/walk-in?on=" + MONDAY, application());
        boss.post("/carriers/1204471/suspend?reason=unpaid permits&on=" + THURSDAY);

        List<AuditRow> permit;
        List<AuditRow> carrier;
        try (UnitOfWork work = database.unitOfWork()) {
            permit = Audit.forDocument(work.entityManager(), "permit", "25-P-000001");
            carrier = Audit.forDocument(work.entityManager(), "carrier", "1204471");
        }
        assertThat(permit).extracting(AuditRow::getAction).containsExactly("permit.issued", "permit.voided");
        assertThat(carrier).extracting(AuditRow::getAction).containsExactly("carrier.suspended");
        assertThat(permit.get(0).getActor()).isEqualTo("Clerk");
        assertThat(permit.get(1).getActor()).isEqualTo("Supervisor");
    }
}
