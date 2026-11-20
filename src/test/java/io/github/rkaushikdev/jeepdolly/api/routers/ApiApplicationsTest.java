package io.github.rkaushikdev.jeepdolly.api.routers;

import static io.github.rkaushikdev.jeepdolly.api.Bodies.FILED;
import static io.github.rkaushikdev.jeepdolly.api.Bodies.application;
import static io.github.rkaushikdev.jeepdolly.api.Bodies.file;
import static io.github.rkaushikdev.jeepdolly.api.Bodies.filed;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.jeepdolly.api.Bodies;
import io.github.rkaushikdev.jeepdolly.api.Http;
import io.github.rkaushikdev.jeepdolly.api.TestService;
import io.github.rkaushikdev.jeepdolly.persistence.ReferenceCache;
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

/** The application routes, over a real database. */
@WebMvcTest
@Import(TestService.class)
class ApiApplicationsTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ReferenceCache cache;

    private Http client;

    @BeforeEach
    void aSupervisorOverASeededDatabase() {
        client = Clients.keyed(new Http(mvc), Databases.seeded());
        cache.reset();
    }

    private static List<String> references(JsonNode found) {
        List<String> made = new ArrayList<>();
        found.forEach(one -> made.add(one.get("reference").asText()));
        return made;
    }

    @Test
    void anApplicationIsFiledAndGivenAReference() {
        Http.Answer answer = file(client, application());
        assertThat(answer.status()).isEqualTo(201);
        JsonNode found = answer.json();
        assertThat(found.get("reference").asText()).isEqualTo("25-F-000001");
        assertThat(found.get("state").asText()).isEqualTo("draft");
        assertThat(found.get("gross").asText()).isEqualTo("94,000 lb");
    }

    @Test
    void theReferencesRunOnWithoutGaps() {
        assertThat(filed(client, application())).isEqualTo("25-F-000001");
        assertThat(filed(client, application())).isEqualTo("25-F-000002");
    }

    @Test
    void aFiledApplicationCanBeReadBack() {
        String reference = filed(client, application());
        JsonNode found = client.get("/applications/" + reference).json();
        assertThat(found.get("origin").asText()).isEqualTo("waterville");
        assertThat(found.get("width").asText()).isEqualTo("12 ft");
        assertThat(found.get("height").asText()).isEqualTo("13 ft 6 in");
    }

    @Test
    void anApplicationNobodyFiledIsA404InTheOneShape() {
        Http.Answer answer = client.get("/applications/25-F-999999");
        assertThat(answer.status()).isEqualTo(404);
        assertThat(answer.json().get("code").asText()).isEqualTo("not_on_file");
    }

    @Test
    void aBodyTheOfficeCannotReadIsRefusedWithAReason() {
        Map<String, Object> written = application();
        Bodies.part(written, "envelope").put("height", "about fourteen feet");
        Http.Answer answer = file(client, written);
        assertThat(answer.status()).isEqualTo(422);
        assertThat(answer.json().get("code").asText()).isEqualTo("unreadable");
        assertThat(answer.json().get("facts").get("written").asText()).isEqualTo("about fourteen feet");
    }

    @Test
    void aCombinationThatCannotExistIsRefusedWithAReason() {
        Map<String, Object> written = application();
        Bodies.item(written, "units", 1).put("axles", 4);
        Http.Answer answer = file(client, written);
        assertThat(answer.status()).isEqualTo(422);
        assertThat(answer.json().get("code").asText()).isEqualTo("impossible");
        assertThat(Http.asMap(answer.json().get("facts"))).isEqualTo(Map.of("declared", 7, "stream", 6));
    }

    @Test
    void aBodyTheFrameworkCannotParseIsTheFrameworksOwnAnswer() {
        assertThat(client.postJson("/applications", Map.of("usdot", 1)).status())
                .isEqualTo(422);
    }

    @Test
    void submittingMovesItIntoTheQueue() {
        String reference = filed(client, application());
        Http.Answer answer = client.post("/applications/" + reference + "/submit?on=" + FILED);
        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.json().get("state").asText()).isEqualTo("submitted");
        assertThat(client.get("/applications/" + reference).json().get("state").asText())
                .isEqualTo("submitted");
    }

    @Test
    void submittingTwiceIsRefusedAndSaysWhy() {
        String reference = filed(client, application());
        client.post("/applications/" + reference + "/submit?on=" + FILED);
        Http.Answer answer = client.post("/applications/" + reference + "/submit?on=" + FILED);
        assertThat(answer.status()).isEqualTo(409);
        assertThat(answer.json().get("code").asText()).isEqualTo("not_allowed");
    }

    @Test
    void theQueueIsReadByState() {
        String first = filed(client, application());
        filed(client, application());
        client.post("/applications/" + first + "/submit?on=" + FILED);
        JsonNode submitted = client.get("/applications?state=submitted").json();
        JsonNode drafts = client.get("/applications?state=draft").json();
        assertThat(references(submitted)).containsExactly(first);
        assertThat(drafts.size()).isEqualTo(1);
    }

    @Test
    void aStateThatIsNotAStateIsRefusedRatherThanCrashing() {
        Http.Answer answer = client.get("/applications?state=nonsense");
        assertThat(answer.status()).isEqualTo(422);
        assertThat(answer.json().get("code").asText()).isEqualTo("unreadable");
    }

    @Test
    void withdrawingClosesTheFile() {
        String reference = filed(client, application());
        Http.Answer answer = client.post("/applications/" + reference + "/withdraw?on=" + FILED);
        assertThat(answer.json().get("state").asText()).isEqualTo("withdrawn");
    }

    @Test
    void theAssessmentPricesTheTripWithoutChangingAnything() {
        String reference = filed(client, application());
        JsonNode found =
                client.get("/applications/" + reference + "/assessment").json();
        assertThat(found.get("permittable").asBoolean()).isTrue();
        assertThat(found.get("kind").asText()).isEqualTo("ordinary");
        assertThat(found.get("route").size()).isPositive();
        assertThat(found.get("fees").get("total").asText()).startsWith("$");
        assertThat(client.get("/applications/" + reference).json().get("state").asText())
                .isEqualTo("draft");
    }

    @Test
    void anAssessmentThatRefusesSaysEveryReason() {
        String reference = filed(client, Bodies.gravel());
        JsonNode found =
                client.get("/applications/" + reference + "/assessment").json();
        assertThat(found.get("permittable").asBoolean()).isFalse();
        assertThat(found.get("refusals").size()).isPositive();
        assertThat(found.get("fees").isNull()).isTrue();
    }

    @Test
    void theConditionsComeBackWithTheAssessment() {
        String reference = filed(client, application());
        JsonNode found =
                client.get("/applications/" + reference + "/assessment").json();
        List<String> codes = new ArrayList<>();
        found.get("conditions").forEach(one -> codes.add(one.get("code").asText()));
        assertThat(codes).contains("T-01", "G-01");
    }
}
