package io.github.rkaushikdev.jeepdolly.api.routers;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.jeepdolly.api.Http;
import io.github.rkaushikdev.jeepdolly.api.TestService;
import io.github.rkaushikdev.jeepdolly.domain.RestrictionKind;
import io.github.rkaushikdev.jeepdolly.permits.Application;
import io.github.rkaushikdev.jeepdolly.persistence.Database;
import io.github.rkaushikdev.jeepdolly.persistence.ReferenceCache;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.persistence.models.RestrictionRow;
import io.github.rkaushikdev.jeepdolly.support.Applications;
import io.github.rkaushikdev.jeepdolly.support.Clients;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import io.github.rkaushikdev.jeepdolly.support.Permits;
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

/** The report routes, which hand back both the lines and the figures. */
@WebMvcTest
@Import(TestService.class)
class ApiReportsTest {

    private static final String APRIL = "2025-04-07";

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

    /** The excavator move's permit, issued on the seventh of April. */
    private void issued() {
        Application application = Applications.anApplication();
        try (UnitOfWork work = database.unitOfWork()) {
            work.applications().add(application);
            work.commit();
            work.permits().add(Permits.aPermit(application));
            work.commit();
        }
    }

    private void restricted(RestrictionRow row) {
        try (UnitOfWork work = database.unitOfWork()) {
            work.add(row);
            work.commit();
        }
    }

    private static List<String> texts(JsonNode list) {
        List<String> made = new ArrayList<>();
        list.forEach(one -> made.add(one.asText()));
        return made;
    }

    @Test
    void theEveningReportComesBackAsLinesAndAsFigures() {
        issued();
        JsonNode found = client.get("/reports/issuance?day=" + APRIL).json();
        assertThat(found.get("count").asInt()).isEqualTo(1);
        assertThat(found.get("taken").asText()).isEqualTo("$46.12");
        assertThat(Http.asMap(found.get("by_series"))).isEqualTo(Map.of("P", 1));
        assertThat(found.get("lines").get(0).asText()).isEqualTo("permits issued 2025-04-07");
    }

    @Test
    void aSpanIsAskedForWithASecondDay() {
        issued();
        JsonNode found =
                client.get("/reports/issuance?day=" + APRIL + "&to=2025-04-30").json();
        assertThat(found.get("end").asText()).isEqualTo("2025-04-30");
        assertThat(found.get("lines").get(0).asText()).endsWith("to 2025-04-30");
    }

    @Test
    void aDayWithNothingOnItIsAReportWithNothingOnIt() {
        JsonNode found = client.get("/reports/issuance?day=" + APRIL).json();
        assertThat(found.get("count").asInt()).isZero();
        assertThat(found.get("taken").asText()).isEqualTo("$0.00");
    }

    @Test
    void aReportThatEndsBeforeItStartsIsRefused() {
        Http.Answer answer = client.get("/reports/issuance?day=2025-04-30&to=" + APRIL);
        assertThat(answer.status()).isEqualTo(422);
        assertThat(answer.json().get("code").asText()).isEqualTo("impossible");
    }

    @Test
    void theRevenueReportCarriesTheKindsAsMoney() {
        issued();
        JsonNode found = client.get("/reports/revenue?start=" + APRIL + "&end=2025-04-30")
                .json();
        assertThat(found.get("permits").asInt()).isEqualTo(1);
        assertThat(found.get("by_kind").get("base fee").asText()).isEqualTo("$40.00");
        assertThat(Http.asMap(found.get("by_month"))).isEqualTo(Map.of("2025-04", "$46.12"));
    }

    @Test
    void theRevenueReportNeedsBothEnds() {
        assertThat(client.get("/reports/revenue?start=" + APRIL).status()).isEqualTo(422);
    }

    @Test
    void theRoadReportCarriesTheChangesAndWhatIsStanding() {
        RestrictionRow work = new RestrictionRow("R-1", "US2-1", RestrictionKind.CLOSURE, "culvert work");
        work.setEffective(LocalDate.of(2025, 4, 9));
        work.setExpires(LocalDate.of(2025, 4, 11));
        restricted(work);
        JsonNode found = client.get("/reports/roads?day=" + APRIL + "&days=7").json();
        assertThat(texts(found.get("changes"))).anyMatch(one -> one.contains("closes"));
        assertThat(found.get("standing").size()).isZero();
    }

    @Test
    void whatIsStandingTodayIsBesideWhatChanges() {
        restricted(new RestrictionRow("R-2", "SA148-1", RestrictionKind.CLOSURE, "washout"));
        JsonNode found = client.get("/reports/roads?day=" + APRIL).json();
        assertThat(found.get("standing").get(0).get("segment").asText()).isEqualTo("SA148-1");
        assertThat(found.get("standing").get(0).get("permanent").asBoolean()).isTrue();
        List<String> changes = texts(found.get("changes"));
        assertThat(changes.get(changes.size() - 1)).isEqualTo("nothing changes");
    }

    @Test
    void aFortnightIsTheDefaultAndTheSpanIsCapped() {
        assertThat(client.get("/reports/roads?day=" + APRIL).json().get("days").asInt())
                .isEqualTo(14);
        assertThat(client.get("/reports/roads?day=" + APRIL + "&days=0").status())
                .isEqualTo(422);
        assertThat(client.get("/reports/roads?day=" + APRIL + "&days=400").status())
                .isEqualTo(422);
    }

    @Test
    void readingAReportNeedsAKey() {
        assertThat(new Http(mvc).get("/reports/issuance?day=" + APRIL).status()).isEqualTo(401);
    }
}
