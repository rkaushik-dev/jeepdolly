package io.github.rkaushikdev.jeepdolly.api.routers;

import static io.github.rkaushikdev.jeepdolly.api.Bodies.FILED;
import static io.github.rkaushikdev.jeepdolly.api.Bodies.application;
import static io.github.rkaushikdev.jeepdolly.api.Bodies.issue;
import static io.github.rkaushikdev.jeepdolly.api.Bodies.issued;
import static io.github.rkaushikdev.jeepdolly.api.Bodies.submitted;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** The permit routes, over a real database. */
@WebMvcTest
@Import(TestService.class)
class ApiPermitsTest {

    private static final String START = "2025-04-08";

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

    @Test
    void aPermitIsIssuedAgainstAnApplication() {
        Http.Answer answer = issue(client, application());
        assertThat(answer.status()).isEqualTo(201);
        JsonNode found = answer.json();
        assertThat(found.get("number").asText()).isEqualTo("25-P-000001");
        assertThat(found.get("carrier").asText()).isEqualTo("Bickford Heavy Haul");
        assertThat(found.get("state").asText()).isEqualTo("issued");
        assertThat(found.get("route").size()).isPositive();
        assertThat(found.get("fees").get("total").asText()).startsWith("$");
    }

    @Test
    void thePermitCarriesTheConditionsThatWereWorkedOut() {
        List<String> codes = new ArrayList<>();
        issue(client, application())
                .json()
                .get("conditions")
                .forEach(one -> codes.add(one.get("code").asText()));
        assertThat(codes).contains("T-01", "R-01", "G-02");
    }

    @Test
    void theApplicationIsMarkedIssuedWhenThePermitIsWritten() {
        String reference = submitted(client, application());
        client.post("/permits?reference=" + reference + "&on=" + FILED);
        assertThat(client.get("/applications/" + reference).json().get("state").asText())
                .isEqualTo("issued");
    }

    @Test
    void theNumbersRunOnWithoutGapsAcrossRequests() {
        assertThat(issued(client)).isEqualTo("25-P-000001");
        assertThat(issued(client)).isEqualTo("25-P-000002");
    }

    @Test
    void anApplicationThatCannotBePermittedIsRefusedWithItsReasons() {
        String reference = submitted(client, Bodies.gravel());
        Http.Answer answer = client.post("/permits?reference=" + reference + "&on=" + FILED);
        assertThat(answer.status()).isEqualTo(409);
        assertThat(answer.json().get("code").asText()).isEqualTo("not_allowed");
        assertThat(answer.json().get("facts").get("reasons").size()).isPositive();
    }

    @Test
    void aRefusedIssueTakesNoNumberWithIt() {
        String reference = submitted(client, Bodies.gravel());
        client.post("/permits?reference=" + reference + "&on=" + FILED);
        assertThat(issued(client)).isEqualTo("25-P-000001");
    }

    @Test
    void aPermitCanBeReadBackByItsNumber() {
        String number = issued(client);
        JsonNode found = client.get("/permits/" + number).json();
        assertThat(found.get("gross").asText()).isEqualTo("94,000 lb");
        assertThat(found.get("narrative").asText()).startsWith("from Waterville");
    }

    @Test
    void aNumberNobodyIssuedIsA404InTheOneShape() {
        Http.Answer answer = client.get("/permits/25-P-999999");
        assertThat(answer.status()).isEqualTo(404);
        assertThat(answer.json().get("code").asText()).isEqualTo("not_on_file");
    }

    @Test
    void whatIsOnTheRoadIsAskedByDay() {
        issued(client);
        assertThat(client.get("/permits?day=" + START).json().size()).isEqualTo(1);
        assertThat(client.get("/permits?day=2025-05-01").json().size()).isZero();
    }

    @Test
    void voidingTakesItOffTheRoadAndKeepsTheReason() {
        String number = issued(client);
        Http.Answer answer = client.post("/permits/" + number + "/void?reason=load cancelled&on=" + START);
        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.json().get("state").asText()).isEqualTo("void");
        assertThat(client.get("/permits?day=" + START).json().size()).isZero();
    }

    @Test
    void voidingTwiceIsRefused() {
        String number = issued(client);
        client.post("/permits/" + number + "/void?reason=load cancelled&on=" + START);
        Http.Answer answer = client.post("/permits/" + number + "/void?reason=again&on=" + START);
        assertThat(answer.status()).isEqualTo(409);
    }

    @Test
    void voidingWithNoReasonIsRefusedByTheFramework() {
        String number = issued(client);
        assertThat(client.post("/permits/" + number + "/void?reason=x&on=" + START)
                        .status())
                .isEqualTo(422);
    }

    @Test
    void theExpiringListIsWhatTheOfficeRingsAbout() {
        issued(client);
        assertThat(client.get("/permits/expiring/soon?day=" + START + "&within=3")
                        .json()
                        .size())
                .isEqualTo(1);
        assertThat(client.get("/permits/expiring/soon?day=2025-05-01&within=3")
                        .json()
                        .size())
                .isZero();
    }

    @Test
    void aCarrierCanBeAskedWhatItHasOut() {
        issued(client);
        issued(client);
        assertThat(client.get("/permits/carrier/1204471").json().size()).isEqualTo(2);
        assertThat(client.get("/permits/carrier/2299001").json().size()).isZero();
    }
}
