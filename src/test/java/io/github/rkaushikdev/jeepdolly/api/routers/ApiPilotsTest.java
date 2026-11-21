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
import io.github.rkaushikdev.jeepdolly.support.Applications;
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

/** Naming the pilot cars, which happens after the application is typed. */
@WebMvcTest
@Import(TestService.class)
class ApiPilotsTest {

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

    /** The excavator move on file, as the counter typed it, and its reference. */
    private String filed() {
        try (UnitOfWork work = database.unitOfWork()) {
            work.applications().add(Applications.anApplication());
            work.commit();
        }
        return Applications.REFERENCE;
    }

    private static Http.Answer name(Http client, String reference, List<String> cards) {
        return client.postJson("/applications/" + reference + "/pilots?on=" + FILED, cards);
    }

    private static List<String> texts(JsonNode list) {
        assertThat(list.isArray()).as("a list").isTrue();
        List<String> made = new ArrayList<>();
        list.forEach(one -> made.add(one.textValue()));
        return made;
    }

    private List<AuditRow> trail(String reference) {
        try (UnitOfWork work = database.unitOfWork()) {
            return Audit.forDocument(work.entityManager(), "application", reference);
        }
    }

    @Test
    void namingTwoCardsPutsThemOnTheApplication() {
        Http.Answer answer = name(client, filed(), List.of("C-1001", "C-1002"));
        assertThat(answer.status()).isEqualTo(200);
        assertThat(texts(answer.json().get("escort_cards"))).containsExactly("C-1001", "C-1002");
    }

    @Test
    void theCardsAreStillThereWhenTheApplicationIsReadBack() {
        String reference = filed();
        name(client, reference, List.of("C-1001"));
        JsonNode found = client.get("/applications/" + reference).json();
        assertThat(texts(found.get("escort_cards"))).containsExactly("C-1001");
    }

    @Test
    void namingThemAgainReplacesRatherThanAdds() {
        String reference = filed();
        name(client, reference, List.of("C-1001", "C-1002"));
        Http.Answer answer = name(client, reference, List.of("C-1002"));
        assertThat(texts(answer.json().get("escort_cards"))).containsExactly("C-1002");
    }

    @Test
    void namingNobodyTakesTheCardsOffAgain() {
        String reference = filed();
        name(client, reference, List.of("C-1001"));
        assertThat(texts(name(client, reference, List.of()).json().get("escort_cards")))
                .isEmpty();
    }

    @Test
    void aCardNobodyHasIsA404WhileTheCarrierIsStillOnThePhone() {
        Http.Answer answer = name(client, filed(), List.of("C-9999"));
        assertThat(answer.status()).isEqualTo(404);
        assertThat(answer.json().get("code").asText()).isEqualTo("not_on_file");
        assertThat(answer.json().get("message").asText()).contains("C-9999");
    }

    @Test
    void aBadCardLeavesTheGoodOnesOffToo() {
        String reference = filed();
        name(client, reference, List.of("C-1001", "C-9999"));
        JsonNode found = client.get("/applications/" + reference).json();
        assertThat(texts(found.get("escort_cards"))).isEmpty();
    }

    @Test
    void aCardThatHasRunOutCanStillBeNamedBecauseItMayBeRenewed() {
        assertThat(name(client, filed(), List.of("C-1003")).status()).isEqualTo(200);
    }

    @Test
    void namingThePilotsWritesALineOnTheTrail() {
        String reference = filed();
        name(client, reference, List.of("C-1001"));
        List<AuditRow> found = trail(reference);
        assertThat(found).extracting(AuditRow::getAction).containsExactly("application.pilots_named");
        assertThat(found.get(0).getDetail()).isEqualTo("C-1001");
    }

    @Test
    void namingNobodySaysSoOnTheTrail() {
        String reference = filed();
        name(client, reference, List.of());
        assertThat(trail(reference).get(0).getDetail()).isEqualTo("nobody");
    }

    @Test
    void anApplicationNobodyHasIsA404() {
        assertThat(name(client, "25-99999", List.of("C-1001")).status()).isEqualTo(404);
    }

    @Test
    void thePilotsCannotBeChangedOnceTheApplicationIsClosed() {
        String reference = filed();
        client.post("/applications/" + reference + "/submit?on=" + FILED);
        client.post("/applications/" + reference + "/refuse?reason=load cancelled&on=" + FILED);
        Http.Answer answer = name(client, reference, List.of("C-1001"));
        assertThat(answer.status()).isEqualTo(409);
    }

    @Test
    void aReaderCannotNameAPilotCar() {
        Http reader = clients.get(Role.READER);
        try (UnitOfWork work = database.unitOfWork()) {
            work.applications().add(Applications.anApplication());
            work.commit();
        }
        assertThat(name(reader, Applications.REFERENCE, List.of("C-1001")).status())
                .isEqualTo(403);
    }

    @Test
    void theCardsCanBeNamedWhenTheApplicationIsFirstFiled() {
        Map<String, Object> sent = Bodies.application();
        sent.put("escort_cards", List.of("C-1001"));
        Http.Answer answer = client.postJson("/applications?filed=" + FILED, sent);
        assertThat(answer.status()).isEqualTo(201);
        assertThat(texts(answer.json().get("escort_cards"))).containsExactly("C-1001");
    }
}
