package io.github.rkaushikdev.jeepdolly.api.routers;

import static io.github.rkaushikdev.jeepdolly.api.Bodies.FILED;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.jeepdolly.api.Bodies;
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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** Taking money over the interface, and the drawer at half past four. */
@WebMvcTest
@Import(TestService.class)
class ApiReceiptsTest {

    private static final String RECEIPTS = "/receipts?on=" + FILED;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ReferenceCache cache;

    private Database database;
    private Map<Role, Http> clients;
    private Http client;
    private String number;

    /** A permit written at the window and not yet paid for. */
    @BeforeEach
    void aWalkInPermit() {
        database = Databases.seeded();
        cache.reset();
        clients = Clients.clientsFor(new Http(mvc), database);
        client = clients.get(Role.SUPERVISOR);
        number = client.postJson("/permits/walk-in?on=" + FILED, Bodies.application())
                .json()
                .get("number")
                .asText();
    }

    private static Map<String, Object> paying(String permit, String amount, String method) {
        return Bodies.map("permit", permit, "amount", amount, "method", method);
    }

    private String owed() {
        return client.get("/receipts/owing/" + number).json().get("owed").asText();
    }

    @Test
    void aPermitStartsOwedInFull() {
        JsonNode found = client.get("/receipts/owing/" + number).json();
        assertThat(found.get("paid").asText()).isEqualTo("$0.00");
        assertThat(found.get("owed").asText()).isEqualTo(found.get("charged").asText());
        assertThat(found.get("settled").asBoolean()).isFalse();
    }

    @Test
    void aPaymentSettlesItAndSaysSo() {
        String owed = owed();
        Http.Answer answer = client.postJson(RECEIPTS, paying(number, owed, "cash"));
        assertThat(answer.status()).isEqualTo(201);
        assertThat(answer.json().get("settled").asBoolean()).isTrue();
        assertThat(answer.json().get("paid").asText()).isEqualTo(owed);
    }

    @Test
    void aPartPaymentLeavesTheRest() {
        Http.Answer answer = client.postJson(RECEIPTS, paying(number, "$10.00", "cheque"));
        assertThat(answer.json().get("settled").asBoolean()).isFalse();
        assertThat(answer.json().get("paid").asText()).isEqualTo("$10.00");
    }

    @Test
    void aWayOfPayingTheOfficeDoesNotTakeIsRefused() {
        Http.Answer answer = client.postJson(RECEIPTS, paying(number, "$10.00", "goats"));
        assertThat(answer.status()).isEqualTo(422);
        assertThat(answer.json().get("facts").get("method").asText()).isEqualTo("goats");
    }

    @Test
    void aPaymentAgainstAPermitNobodyIssuedIsA404() {
        Http.Answer answer = client.postJson(RECEIPTS, paying("25-P-999999", "$10.00", "cash"));
        assertThat(answer.status()).isEqualTo(404);
    }

    @Test
    void takingMoneyNeedsAClerk() {
        Http.Answer answer = clients.get(Role.READER).postJson(RECEIPTS, paying(number, "$10.00", "cash"));
        assertThat(answer.status()).isEqualTo(403);
    }

    @Test
    void aPaymentLandsOnTheTrail() {
        client.postJson(RECEIPTS, paying(number, "$10.00", "cash"));
        try (UnitOfWork work = database.unitOfWork()) {
            List<AuditRow> found = Audit.forDocument(work.entityManager(), "permit", number);
            assertThat(found).extracting(AuditRow::getAction).containsExactly("permit.issued", "payment.taken");
            assertThat(found.get(1).getDetail()).isEqualTo("cash $10.00");
        }
    }

    @Test
    void theOutstandingListLosesWhatIsPaid() {
        assertThat(client.get("/receipts/outstanding?day=" + FILED).json().size())
                .isEqualTo(1);
        client.postJson(RECEIPTS, paying(number, owed(), "cash"));
        assertThat(client.get("/receipts/outstanding?day=" + FILED).json().size())
                .isZero();
    }

    @Test
    void aCarrierIsToldOneNumber() {
        JsonNode found = client.get("/receipts/carrier/1204471?day=" + FILED).json();
        assertThat(found.get("usdot").asInt()).isEqualTo(1204471);
        assertThat(found.get("owed").asText()).startsWith("$");
    }

    @Test
    void theDrawerSplitsTheCashFromTheAccountWork() {
        client.postJson(RECEIPTS, paying(number, "$20.00", "cash"));
        client.postJson(RECEIPTS, paying(number, "$26.12", "account"));
        JsonNode found = client.get("/receipts/drawer?day=" + FILED).json();
        assertThat(found.get("in_the_drawer").asText()).isEqualTo("$20.00");
        assertThat(found.get("on_account").asText()).isEqualTo("$26.12");
        assertThat(found.get("receipts").asInt()).isEqualTo(2);
        assertThat(found.get("lines").get(0).asText()).isEqualTo("till 2025-04-07");
    }
}
