package io.github.rkaushikdev.jeepdolly.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.jeepdolly.OfficeRules;
import io.github.rkaushikdev.jeepdolly.Settings;
import io.github.rkaushikdev.jeepdolly.enforcement.EnforcementBook;
import io.github.rkaushikdev.jeepdolly.enforcement.Offence;
import io.github.rkaushikdev.jeepdolly.enforcement.Standing;
import io.github.rkaushikdev.jeepdolly.persistence.Books;
import io.github.rkaushikdev.jeepdolly.persistence.Database;
import io.github.rkaushikdev.jeepdolly.persistence.ReferenceCache;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.services.Enforcement;
import io.github.rkaushikdev.jeepdolly.support.Clients;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** How this office is configured, and what happens when it is configured differently. */
@WebMvcTest
@Import(TestService.class)
class ApiSettingsTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ReferenceCache cache;

    private Database database;
    private Http plain;
    private Http client;

    @BeforeEach
    void aSeededDatabaseAndASupervisor() {
        database = Databases.seeded();
        cache.reset();
        plain = new Http(mvc);
        client = Clients.keyed(plain, database);
    }

    /** The service started over this database with one set of office rules. */
    private Running serviceWith(OfficeRules rules) {
        return Running.start(Settings.defaults().withDatabase(Databases.URL).withRules(rules), database);
    }

    @Test
    void theSettingsPageSaysHowTheOfficeIsSetUp() {
        JsonNode found = client.get("/settings").json();
        assertThat(found.get("free_copies").asInt()).isEqualTo(3);
        assertThat(found.get("renewal_days").asInt()).isEqualTo(30);
        assertThat(found.get("citation_grace").asInt()).isEqualTo(30);
        assertThat(found.get("line_survey_years").asInt()).isEqualTo(5);
    }

    @Test
    void theSettingsPageCarriesThePrintedLines() {
        JsonNode found = client.get("/settings").json();
        assertThat(found.get("lines").get(0).asText()).isEqualTo("free copies       3");
        assertThat(found.get("lines").size()).isEqualTo(4);
    }

    @Test
    void theSettingsPageWantsAKey() {
        assertThat(plain.get("/settings").status()).isIn(401, 403);
    }

    @Test
    void healthStillWantsNoKey() {
        assertThat(plain.get("/health").status()).isEqualTo(200);
    }

    @Test
    void aChangedRuleShowsOnThePage() {
        try (Running service =
                serviceWith(OfficeRules.defaults().withFreeCopies(5).withRenewalDays(45))) {
            Http changed = Clients.keyed(service.http(), database);
            JsonNode found = changed.get("/settings").json();
            assertThat(found.get("free_copies").asInt()).isEqualTo(5);
            assertThat(found.get("renewal_days").asInt()).isEqualTo(45);
        }
    }

    @Test
    void aShorterGraceHoldsACarrierSooner() {
        EnforcementBook generous;
        EnforcementBook tight;
        try (UnitOfWork work = database.unitOfWork()) {
            Enforcement.fileCitation(
                    work,
                    "E-25-0417",
                    1204471,
                    "Sgt A Thibodeau",
                    Offence.NO_PERMIT,
                    "US201-2",
                    LocalDate.of(2025, 4, 8),
                    LocalDate.of(2025, 4, 8),
                    "M Cyr");
            work.commit();
            generous = Books.enforcementFrom(work.entityManager(), 30);
            tight = Books.enforcementFrom(work.entityManager(), 0);
        }
        LocalDate on = LocalDate.of(2025, 5, 10);
        assertThat(generous.hold(1204471, on)).isEmpty();
        assertThat(tight.hold(1204471, on)).isPresent();
    }

    @Test
    void theDefaultGraceIsTheOneTheModuleOwns() {
        assertThat(OfficeRules.defaults().citationGrace()).isEqualTo(Standing.GRACE_DAYS);
    }
}
