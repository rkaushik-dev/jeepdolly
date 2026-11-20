package io.github.rkaushikdev.jeepdolly.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.jeepdolly.Settings;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import io.github.rkaushikdev.jeepdolly.testing.Raiser;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The application factory and the one error shape, over a database with nothing in it. */
class ApiAppTest {

    private static Running service;
    private Http client;

    @BeforeAll
    static void startTheServiceWithRoutesThatRaise() {
        service = Running.start(Settings.defaults().withDatabase(Databases.URL), Databases.empty(), Raiser.class);
    }

    @AfterAll
    static void stopTheService() {
        service.close();
    }

    @BeforeEach
    void anEmptyDatabase() {
        Databases.empty();
        client = service.http();
    }

    @Test
    void theHealthRouteSaysWhichServiceAndWhichVersion() {
        Http.Answer answer = client.get("/health");
        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.json().get("service").asText()).isEqualTo("jeepdolly");
        assertThat(answer.json().get("database").asText()).isEqualTo("up");
    }

    @Test
    void theHealthRouteActuallyTouchesTheDatabase() {
        assertThat(client.get("/health").json().get("database").asText()).isEqualTo("up");
    }

    @Test
    void theServiceDescribesItselfForTheCarriersOwnSoftware() {
        Http.Answer schema = client.get("/openapi.json");
        assertThat(schema.status()).isEqualTo(200);
        assertThat(schema.json().get("info").get("title").asText()).isEqualTo("jeepdolly");
    }

    @Test
    void aRefusalComesBackInTheOneShape() {
        Http.Answer answer = client.get("/testing/not_on_file");
        assertThat(answer.status()).isEqualTo(404);
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("code", "not_on_file");
        expected.put("message", "no carrier with usdot 9999");
        expected.put("facts", Map.of("usdot", 9999));
        assertThat(Http.asMap(answer.json())).isEqualTo(expected);
    }

    @Test
    void eachRefusalCarriesTheStatusTheTableGivesIt() {
        assertThat(client.get("/testing/conflict").status()).isEqualTo(409);
        assertThat(client.get("/testing/impossible").status()).isEqualTo(422);
        assertThat(client.get("/testing/over_limit").status()).isEqualTo(422);
        assertThat(client.get("/testing/blocked").status()).isEqualTo(422);
        assertThat(client.get("/testing/not_allowed").status()).isEqualTo(409);
    }

    @Test
    void theFactsComeThroughSoTheCallerCanActOnThem() {
        Http.Answer body = client.get("/testing/blocked");
        assertThat(Http.asMap(body.json().get("facts"))).isEqualTo(Map.of("segment", "US2-1"));
        assertThat(body.json().get("code").asText()).isEqualTo("blocked");
    }

    @Test
    void anUnknownCodeFallsBackToTheRefusedStatus() {
        assertThat(Errors.statusFor("something_new")).isEqualTo(422);
        assertThat(Errors.statusFor("not_on_file")).isEqualTo(404);
    }

    @Test
    void aRouteThatIsNotThereIsTheFrameworksOwnAnswer() {
        assertThat(client.get("/nothing/here").status()).isEqualTo(404);
    }
}
