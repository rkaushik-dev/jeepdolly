package io.github.rkaushikdev.jeepdolly.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.jeepdolly.Jeepdolly;
import io.github.rkaushikdev.jeepdolly.Settings;
import io.github.rkaushikdev.jeepdolly.permits.Application;
import io.github.rkaushikdev.jeepdolly.persistence.Database;
import io.github.rkaushikdev.jeepdolly.persistence.ReferenceCache;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.support.Applications;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import io.github.rkaushikdev.jeepdolly.support.Permits;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** The routes about the service, which need no key at all. */
@WebMvcTest
@Import(TestService.class)
class ApiServiceTest {

    private static final String START = "2025-04-08";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ReferenceCache cache;

    private Database database;
    private Http client;

    @BeforeEach
    void aSeededDatabase() {
        database = Databases.seeded();
        cache.reset();
        client = new Http(mvc);
    }

    private void issued() {
        Application application = Applications.anApplication();
        try (UnitOfWork work = database.unitOfWork()) {
            work.applications().add(application);
            work.commit();
            work.permits().add(Permits.aPermit(application));
            work.commit();
        }
    }

    @Test
    void healthTouchesTheDatabaseAndSaysWhichService() {
        JsonNode found = client.get("/health").json();
        assertThat(found.get("service").asText()).isEqualTo("jeepdolly");
        assertThat(found.get("database").asText()).isEqualTo("up");
        assertThat(found.get("version").asText()).isEqualTo(Jeepdolly.VERSION);
    }

    @Test
    void versionIsAnsweredWithoutTouchingAnything() {
        Http.Answer answer = client.get("/version");
        assertThat(answer.status()).isEqualTo(200);
        assertThat(Http.asMap(answer.json())).isEqualTo(Map.of("service", "jeepdolly", "version", Jeepdolly.VERSION));
    }

    @Test
    void theScrapeIsPlainTextWithAHelpLine() {
        Http.Answer answer = client.get("/metrics?day=" + START);
        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.contentType()).startsWith("text/plain");
        assertThat(answer.text()).contains("# HELP jeepdolly_segments");
        assertThat(answer.text()).contains("jeepdolly_segments 12");
    }

    @Test
    void theScrapeMovesWhenTheOfficeDoes() {
        JsonNode before = client.get("/metrics.json?day=" + START).json();
        issued();
        JsonNode after = client.get("/metrics.json?day=" + START).json();
        assertThat(before.get("permits_on_the_road").asInt()).isEqualTo(0);
        assertThat(after.get("permits_on_the_road").asInt()).isEqualTo(1);
    }

    @Test
    void theJsonAndTheTextCarryTheSameNumbers() {
        JsonNode found = client.get("/metrics.json?day=" + START).json();
        String written = client.get("/metrics?day=" + START).text();
        assertThat(found.size()).isPositive();
        for (Map.Entry<String, JsonNode> one : found.properties()) {
            assertThat(written)
                    .contains("jeepdolly_" + one.getKey() + " " + one.getValue().asLong());
        }
    }

    @Test
    void noneOfTheServiceRoutesNeedsAKey() {
        for (String path : List.of("/health", "/version", "/metrics", "/metrics.json")) {
            assertThat(client.get(path).status()).as(path).isEqualTo(200);
        }
    }

    @Test
    void theServiceStillDescribesItself() {
        try (Running service = Running.start(Settings.defaults().withDatabase(Databases.URL), database)) {
            JsonNode schema = service.http().get("/openapi.json").json();
            assertThat(schema.get("info").get("version").asText()).isEqualTo(Jeepdolly.VERSION);
            assertThat(schema.get("paths").has("/metrics")).isTrue();
        }
    }
}
