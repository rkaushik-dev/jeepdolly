package io.github.rkaushikdev.jeepdolly.api.routers;

import static io.github.rkaushikdev.jeepdolly.api.Bodies.map;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.jeepdolly.api.Http;
import io.github.rkaushikdev.jeepdolly.api.Role;
import io.github.rkaushikdev.jeepdolly.api.TestService;
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

/** Citations over the interface, which is the counter typing in the post. */
@WebMvcTest
@Import(TestService.class)
class ApiCitationsTest {

    private static final String WRITTEN = "2025-04-08";
    private static final String FILED = "2025-04-11";

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

    /** The overweight ticket Sgt Thibodeau wrote at Fairfield, with any field replaced. */
    private static Map<String, Object> ticket(Object... changes) {
        Map<String, Object> body = map(
                "number",
                "E-25-0417",
                "usdot",
                1204471,
                "officer",
                "Sgt A Thibodeau",
                "offence",
                "overweight",
                "place",
                "US201-2 at Fairfield",
                "written",
                WRITTEN,
                "filed",
                FILED,
                "over",
                "12000");
        body.putAll(map(changes));
        return body;
    }

    private static Http.Answer fileOne(Http client, Object... changes) {
        return client.postJson("/citations", ticket(changes));
    }

    private static List<String> numbers(JsonNode found) {
        assertThat(found.isArray()).as("a list").isTrue();
        List<String> made = new ArrayList<>();
        found.forEach(one -> made.add(one.get("number").textValue()));
        return made;
    }

    @Test
    void aTicketIsFiledAndPricedOffTheSchedule() {
        Http.Answer answer = fileOne(client);
        assertThat(answer.status()).isEqualTo(201);
        JsonNode found = answer.json();
        assertThat(found.get("amount").asText()).isEqualTo("$580.00");
        assertThat(found.get("multiplier").intValue()).isEqualTo(1);
        assertThat(found.get("state").asText()).isEqualTo("written");
    }

    @Test
    void theCarrierNameComesOffTheRegister() {
        assertThat(fileOne(client).json().get("carrier_name").asText()).isEqualTo("Bickford Heavy Haul");
    }

    @Test
    void theContestWindowIsOnTheAnswer() {
        assertThat(fileOne(client).json().get("contest_closes").asText()).isEqualTo("2025-04-29");
    }

    @Test
    void anOffenceThisOfficeDoesNotPriceIsRefused() {
        Http.Answer answer = fileOne(client, "offence", "parked badly");
        assertThat(answer.status()).isEqualTo(422);
        assertThat(answer.json().get("message").asText()).contains("parked badly");
    }

    @Test
    void aCarrierNobodyHasRegisteredIsA404() {
        assertThat(fileOne(client, "usdot", 9999999).status()).isEqualTo(404);
    }

    @Test
    void theSameNumberIsNotFiledTwice() {
        fileOne(client);
        assertThat(fileOne(client).status()).isEqualTo(409);
    }

    @Test
    void aTicketFiledBeforeItWasWrittenIsRefused() {
        assertThat(fileOne(client, "written", "2025-05-01").status()).isEqualTo(422);
    }

    @Test
    void aSecondTicketInTheYearIsPricedAsARepeat() {
        fileOne(client, "number", "E-25-0100", "written", "2025-01-20");
        JsonNode found = fileOne(client).json();
        assertThat(found.get("multiplier").intValue()).isEqualTo(2);
        assertThat(found.get("amount").asText()).isEqualTo("$1,160.00");
    }

    @Test
    void aFlatOffenceCarriesNoOverageOnTheAnswer() {
        JsonNode found = fileOne(client, "offence", "off route", "over", null).json();
        assertThat(found.has("over")).isTrue();
        assertThat(found.get("over").isNull()).isTrue();
        assertThat(found.get("amount").asText()).isEqualTo("$250.00");
    }

    @Test
    void oneCitationCanBeReadBack() {
        fileOne(client);
        assertThat(client.get("/citations/E-25-0417").json().get("place").asText())
                .isEqualTo("US201-2 at Fairfield");
    }

    @Test
    void aCitationNobodyHasIsA404() {
        assertThat(client.get("/citations/E-25-9999").status()).isEqualTo(404);
    }

    @Test
    void theChaseListIsWhatIsStillOwing() {
        fileOne(client, "number", "E-25-0100", "written", "2025-01-20");
        fileOne(client);
        assertThat(numbers(client.get("/citations/owing").json())).hasSize(2);
        client.post("/citations/E-25-0417/payment?on=2025-04-22&reference=cheque 4471");
        assertThat(numbers(client.get("/citations/owing").json())).containsExactly("E-25-0100");
    }

    @Test
    void everythingAgainstOneCarrierComesBackNewestFirst() {
        fileOne(client, "number", "E-25-0100", "written", "2025-01-20");
        fileOne(client);
        JsonNode found = client.get("/citations/carrier/1204471").json();
        assertThat(numbers(found)).containsExactly("E-25-0417", "E-25-0100");
    }

    @Test
    void aCarrierWithNothingAgainstItComesBackEmpty() {
        assertThat(numbers(client.get("/citations/carrier/2299001").json())).isEmpty();
    }

    @Test
    void contestingItMovesItAndLandsOnTheTrail() {
        fileOne(client);
        Http.Answer answer = client.post("/citations/E-25-0417/contest?reason=scale not certified&on=2025-04-20");
        assertThat(answer.json().get("state").asText()).isEqualTo("contested");
        List<AuditRow> found;
        try (UnitOfWork work = database.unitOfWork()) {
            found = Audit.forDocument(work.entityManager(), "citation", "E-25-0417");
        }
        assertThat(found.stream().map(AuditRow::getAction).toList())
                .containsExactly("citation.filed", "citation.contested");
    }

    @Test
    void contestingItAfterTheWindowIsA409() {
        fileOne(client);
        Http.Answer answer = client.post("/citations/E-25-0417/contest?reason=too late&on=2025-05-15");
        assertThat(answer.status()).isEqualTo(409);
    }

    @Test
    void contestingItWithoutGroundsIsRefusedByTheQuery() {
        fileOne(client);
        assertThat(client.post("/citations/E-25-0417/contest?reason=no").status())
                .isEqualTo(422);
    }

    @Test
    void upholdingItLeavesItOwing() {
        fileOne(client);
        client.post("/citations/E-25-0417/contest?reason=grounds&on=2025-04-20");
        Http.Answer answer = client.post("/citations/E-25-0417/uphold?on=2025-05-02");
        assertThat(answer.json().get("state").asText()).isEqualTo("upheld");
        assertThat(numbers(client.get("/citations/owing").json())).hasSize(1);
    }

    @Test
    void dismissingItClosesIt() {
        fileOne(client);
        client.post("/citations/E-25-0417/contest?reason=grounds&on=2025-04-20");
        Http.Answer answer = client.post("/citations/E-25-0417/dismiss?reason=scale had lapsed&on=2025-05-02");
        assertThat(answer.json().get("state").asText()).isEqualTo("dismissed");
        assertThat(numbers(client.get("/citations/owing").json())).isEmpty();
    }

    @Test
    void nothingIsUpheldBeforeItIsContested() {
        fileOne(client);
        assertThat(client.post("/citations/E-25-0417/uphold").status()).isEqualTo(409);
    }

    @Test
    void payingItClosesIt() {
        fileOne(client);
        Http.Answer answer = client.post("/citations/E-25-0417/payment?on=2025-04-22");
        assertThat(answer.json().get("state").asText()).isEqualTo("paid");
    }

    @Test
    void voidingItNeedsAReasonAndClosesIt() {
        fileOne(client);
        Http.Answer answer = client.post("/citations/E-25-0417/void?reason=wrong usdot&on=2025-04-14");
        assertThat(answer.json().get("state").asText()).isEqualTo("void");
    }

    @Test
    void aPaidCitationCannotBeVoided() {
        fileOne(client);
        client.post("/citations/E-25-0417/payment?on=2025-04-22");
        assertThat(client.post("/citations/E-25-0417/void?reason=wrong usdot").status())
                .isEqualTo(409);
    }

    @Test
    void aNumberCanBeHadOffTheCounterForTheFewTheOfficeWrites() {
        String first = client.get("/citations/next-number?on=2025-04-08")
                .json()
                .get("number")
                .asText();
        String second = client.get("/citations/next-number?on=2025-04-08")
                .json()
                .get("number")
                .asText();
        assertThat(first).isEqualTo("25-E-000001");
        assertThat(second).isEqualTo("25-E-000002");
    }

    @Test
    void aClerkCannotDismissATicket() {
        Http clerk = clients.get(Role.CLERK);
        clerk.postJson("/citations", ticket());
        clerk.post("/citations/E-25-0417/contest?reason=grounds&on=2025-04-20");
        Http.Answer answer = clerk.post("/citations/E-25-0417/dismiss?reason=scale&on=2025-05-02");
        assertThat(answer.status()).isEqualTo(403);
    }

    @Test
    void aClerkCanTakeTheMoney() {
        Http clerk = clients.get(Role.CLERK);
        clerk.postJson("/citations", ticket());
        assertThat(clerk.post("/citations/E-25-0417/payment?on=2025-04-22").status())
                .isEqualTo(200);
    }

    @Test
    void aReaderCannotFileATicket() {
        Http reader = clients.get(Role.READER);
        assertThat(reader.postJson("/citations", ticket()).status()).isEqualTo(403);
    }
}
