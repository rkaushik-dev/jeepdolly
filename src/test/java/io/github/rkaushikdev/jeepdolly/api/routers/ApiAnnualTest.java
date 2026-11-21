package io.github.rkaushikdev.jeepdolly.api.routers;

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

/** The annual routes, including the one that answers before anybody pays. */
@WebMvcTest
@Import(TestService.class)
class ApiAnnualTest {

    private static final String FILED = "2025-11-03";

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

    private static Map<String, Object> question() {
        return question("11'", "110000", "CRNE");
    }

    private static Map<String, Object> question(String width, String gross, String commodity) {
        return Bodies.map(
                "envelope", Bodies.map("width", width, "height", "13'6", "length", "90'", "rear_overhang", "6'"),
                "gross", gross,
                "commodity", commodity);
    }

    /** The excavator move for November, filed and submitted, with any part of the body changed. */
    private String filed(Map<String, Object> changes) {
        Map<String, Object> written = Bodies.application();
        written.put("start", "2025-11-04");
        written.put("end", "2025-11-06");
        written.putAll(changes);
        String reference = client.postJson("/applications?filed=" + FILED, written)
                .json()
                .get("reference")
                .asText();
        client.post("/applications/" + reference + "/submit?on=" + FILED);
        return reference;
    }

    private Http.Answer issue(String reference) {
        return client.post("/annual?reference=" + reference + "&on=" + FILED);
    }

    private static List<String> texts(JsonNode list) {
        List<String> made = new ArrayList<>();
        list.forEach(one -> made.add(one.asText()));
        return made;
    }

    @Test
    void aCombinationInsideTheEnvelopeIsEligible() {
        Http.Answer answer = client.postJson("/annual/eligibility", question());
        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.json().get("allowed").asBoolean()).isTrue();
        assertThat(answer.json().get("reasons").size()).isZero();
    }

    @Test
    void theAnswerCarriesTheEnvelopeItWasReadAgainst() {
        JsonNode found = client.postJson("/annual/eligibility", question()).json();
        assertThat(found.get("envelope_width").asText()).isEqualTo("12 ft");
        assertThat(found.get("envelope_height").asText()).isEqualTo("14 ft");
        assertThat(found.get("gross_cap").asText()).isEqualTo("120,000 lb");
    }

    @Test
    void aLoadPastTheEnvelopeGetsTheReasonsBack() {
        JsonNode found = client.postJson("/annual/eligibility", question("14'", "110000", "CRNE"))
                .json();
        assertThat(found.get("allowed").asBoolean()).isFalse();
        assertThat(texts(found.get("reasons"))).containsExactly("width is past the annual envelope");
    }

    @Test
    void aLoadOverTheCapGetsThatReasonToo() {
        JsonNode found = client.postJson("/annual/eligibility", question("11'", "130000", "CRNE"))
                .json();
        assertThat(found.get("reasons").get(0).asText()).contains("over the annual cap");
    }

    @Test
    void aCommodityNobodyFiledIsA404() {
        assertThat(client.postJson("/annual/eligibility", question("11'", "110000", "ZZZZ"))
                        .status())
                .isEqualTo(404);
    }

    @Test
    void askingTheQuestionWritesNothingDown() {
        client.postJson("/annual/eligibility", question());
        assertThat(client.get("/permits?day=2025-11-03").json().size()).isZero();
    }

    @Test
    void anAnnualIsIssuedAgainstAnApplicationOnFile() {
        Http.Answer answer = issue(filed(Map.of()));
        assertThat(answer.status()).isEqualTo(201);
        JsonNode found = answer.json();
        assertThat(found.get("number").asText()).startsWith("25-A-");
        assertThat(texts(found.get("route"))).containsExactly("all state maintained highways");
        assertThat(found.get("fees").get("total").asText()).isEqualTo("$500.00");
    }

    @Test
    void theAnnualRunsAYearAndSaysSoOnTheFace() {
        JsonNode found = issue(filed(Map.of())).json();
        assertThat(found.get("start").asText()).isEqualTo("2025-11-03");
        assertThat(found.get("end").asText()).isEqualTo("2026-11-02");
        assertThat(found.get("conditions").get(0).get("code").asText()).isEqualTo("T-01");
    }

    @Test
    void aCombinationTooBigForAnAnnualIsRefused() {
        Map<String, Object> envelope = Bodies.part(Bodies.application(), "envelope");
        envelope.put("width", "16'");
        Http.Answer answer = issue(filed(Map.of("envelope", envelope)));
        assertThat(answer.status()).isEqualTo(409);
        assertThat(answer.json().get("facts").get("reasons").size()).isPositive();
    }

    @Test
    void aCarrierThatIsNotInGoodStandingIsRefused() {
        Http.Answer answer = issue(filed(Map.of("usdot", 2299001)));
        assertThat(answer.status()).isEqualTo(409);
        assertThat(answer.json().get("facts").get("usdot").asInt()).isEqualTo(2299001);
    }

    @Test
    void theApplicationIsMarkedIssuedLikeAnyOther() {
        String reference = filed(Map.of());
        issue(reference);
        assertThat(client.get("/applications/" + reference).json().get("state").asText())
                .isEqualTo("issued");
    }
}
