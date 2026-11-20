package io.github.rkaushikdev.jeepdolly.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import io.github.rkaushikdev.jeepdolly.testing.Echo;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * How a request is read before any route decides anything: what is a 422, where the body says the
 * problem was, and what is quietly accepted.
 */
@WebMvcTest
@Import({TestService.class, Echo.class})
class RequestShapesTest {

    @Autowired
    private MockMvc mvc;

    private Http client;

    @BeforeEach
    void aClient() {
        Databases.empty();
        client = new Http(mvc);
    }

    @Test
    void aBodyMissingARequiredFieldIsA422ThatSaysWhere() {
        Map<String, Object> body = SchemasTest.body();
        body.remove("usdot");
        Http.Answer answer = client.postJson("/echo/application", body);
        assertThat(answer.status()).isEqualTo(422);
        JsonNode problem = answer.json().get("detail").get(0);
        assertThat(problem.get("loc").toString()).isEqualTo("[\"body\",\"usdot\"]");
        assertThat(problem.get("msg").asText()).isEqualTo(Rejected.REQUIRED);
    }

    @Test
    void anEmptyListThatMustHoldSomethingIsA422() {
        Map<String, Object> body = SchemasTest.body();
        body.put("axles", List.of());
        Http.Answer answer = client.postJson("/echo/application", body);
        assertThat(answer.status()).isEqualTo(422);
        assertThat(answer.json().get("detail").get(0).get("loc").toString()).isEqualTo("[\"body\",\"axles\"]");
    }

    @Test
    void aNumberWhereAMeasureBelongsIsA422RatherThanAString() {
        Map<String, Object> body = SchemasTest.body();
        SchemasTest.part(body, "envelope").put("width", 144);
        Http.Answer answer = client.postJson("/echo/application", body);
        assertThat(answer.status()).isEqualTo(422);
        assertThat(answer.json().get("detail").get(0).get("loc").toString())
                .isEqualTo("[\"body\",\"envelope\",\"width\"]");
    }

    @Test
    void aFieldNobodyDeclaredIsIgnoredAndTheDefaultsFillIn() {
        Map<String, Object> body = SchemasTest.body();
        body.put("colour", "red");
        Http.Answer answer = client.postJson("/echo/application", body);
        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.json().get("axles").asInt()).isEqualTo(6);
        assertThat(answer.json().get("notes").asText()).isEmpty();
        JsonNode applicant = client.postJson("/echo/applicant", Map.of("name", "Dana", "phone", "207"))
                .json();
        assertThat(applicant.get("email").asText()).isEmpty();
    }

    @Test
    void aQueryOutOfItsRangeOrMissingOrUnreadableIsA422() {
        assertThat(client.get("/echo/days?days=14&day=2025-04-08").status()).isEqualTo(200);
        assertThat(client.get("/echo/days?days=0&day=2025-04-08").status()).isEqualTo(422);
        assertThat(client.get("/echo/days?days=400&day=2025-04-08").status()).isEqualTo(422);
        assertThat(client.get("/echo/days?day=2025-04-08").status()).isEqualTo(422);
        assertThat(client.get("/echo/days?days=ten&day=2025-04-08").status()).isEqualTo(422);
        assertThat(client.get("/echo/days?days=14&day=April").status()).isEqualTo(422);
    }

    @Test
    void aBodyThatIsNotJsonAtAllIsA422() {
        Http.Answer answer = client.send(MockMvcRequestBuilders.post("/echo/applicant")
                .contentType("application/json")
                .content("{not json"));
        assertThat(answer.status()).isEqualTo(422);
        assertThat(answer.json().get("detail").get(0).get("loc").get(0).asText())
                .isEqualTo("body");
    }
}
