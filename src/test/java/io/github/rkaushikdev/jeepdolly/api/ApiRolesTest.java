package io.github.rkaushikdev.jeepdolly.api;

import static io.github.rkaushikdev.jeepdolly.api.Bodies.FILED;
import static io.github.rkaushikdev.jeepdolly.api.Bodies.application;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.jeepdolly.persistence.Database;
import io.github.rkaushikdev.jeepdolly.persistence.ReferenceCache;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.persistence.models.AuditRow;
import io.github.rkaushikdev.jeepdolly.services.Audit;
import io.github.rkaushikdev.jeepdolly.support.Clients;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** Which rung each route needs, and what lands on the trail behind it. */
@WebMvcTest
@Import(TestService.class)
class ApiRolesTest {

    private static final String START = "2025-04-08";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ReferenceCache cache;

    private Database database;
    private Http nobody;
    private Map<Role, Http> clients;

    @BeforeEach
    void aClientForEveryRoleOverASeededDatabase() {
        database = Databases.seeded();
        cache.reset();
        nobody = new Http(mvc);
        clients = Clients.clientsFor(nobody, database);
    }

    private Http as(Role role) {
        return clients.get(role);
    }

    private static String issuedBy(Http client) {
        return Bodies.issued(client);
    }

    private <T> T onTheTrail(Function<UnitOfWork, T> read) {
        try (UnitOfWork work = database.unitOfWork()) {
            return read.apply(work);
        }
    }

    @Test
    void readingNeedsAKeyAtAll() {
        assertThat(nobody.get("/applications").status()).isEqualTo(401);
        assertThat(nobody.get("/permits").status()).isEqualTo(401);
    }

    @Test
    void theHealthRouteIsOpenBecauseALoadBalancerHasNoKey() {
        assertThat(nobody.get("/health").status()).isEqualTo(200);
    }

    @Test
    void filingAnApplicationNeedsAClerk() {
        assertThat(Bodies.file(as(Role.READER), application()).status()).isEqualTo(403);
        assertThat(Bodies.file(as(Role.CLERK), application()).status()).isEqualTo(201);
    }

    @Test
    void aReaderMayStillReadWhatAClerkFiled() {
        String reference = Bodies.filed(as(Role.CLERK), application());
        Http reader = as(Role.READER);
        assertThat(reader.get("/applications/" + reference).status()).isEqualTo(200);
        assertThat(reader.get("/applications/" + reference + "/assessment").status())
                .isEqualTo(200);
    }

    @Test
    void issuingNeedsAClerkAndVoidingNeedsASupervisor() {
        Http clerk = as(Role.CLERK);
        String number = issuedBy(clerk);
        assertThat(clerk.post("/permits/" + number + "/void?reason=no&on=" + START)
                        .status())
                .isEqualTo(403);
        Http boss = as(Role.SUPERVISOR);
        assertThat(boss.post("/permits/" + number + "/void?reason=cancelled&on=" + START)
                        .status())
                .isEqualTo(200);
    }

    @Test
    void anEngineerMayIssueBecauseTheLadderReachesDown() {
        assertThat(issuedBy(as(Role.ENGINEER))).startsWith("25-P-");
    }

    @Test
    void filingLandsOnTheTrailWithTheNameOfWhoDidIt() {
        String reference = Bodies.filed(as(Role.CLERK), application());
        List<AuditRow> found = onTheTrail(work -> Audit.forDocument(work.entityManager(), "application", reference));
        assertThat(found).extracting(AuditRow::getAction).containsExactly("application.filed");
        assertThat(found.get(0).getActor()).isEqualTo("Clerk");
        assertThat(found.get(0).getDetail()).isEqualTo("waterville to pittsfield");
    }

    @Test
    void submittingAndIssuingBothLandOnTheTrail() {
        String number = issuedBy(as(Role.CLERK));
        List<AuditRow> permitTrail = onTheTrail(work -> Audit.forDocument(work.entityManager(), "permit", number));
        List<AuditRow> submitted = onTheTrail(work -> Audit.ofAction(work.entityManager(), "application.submitted"));
        assertThat(permitTrail).extracting(AuditRow::getAction).containsExactly("permit.issued");
        assertThat(permitTrail.get(0).getDetail()).isEqualTo("94,000 lb to Bickford Heavy Haul");
        assertThat(submitted).hasSize(1);
    }

    @Test
    void voidingLandsOnTheTrailWithTheReason() {
        String number = issuedBy(as(Role.CLERK));
        as(Role.SUPERVISOR).post("/permits/" + number + "/void?reason=load cancelled&on=" + START);
        List<AuditRow> found = onTheTrail(work -> Audit.forDocument(work.entityManager(), "permit", number));
        assertThat(found).extracting(AuditRow::getAction).containsExactly("permit.issued", "permit.voided");
        assertThat(found.get(1).getActor()).isEqualTo("Supervisor");
        assertThat(found.get(1).getDetail()).isEqualTo("load cancelled");
    }

    @Test
    void aRefusedIssueLeavesNothingOnTheTrail() {
        Http clerk = as(Role.CLERK);
        String reference = Bodies.submitted(clerk, Bodies.gravel());
        clerk.post("/permits?reference=" + reference + "&on=" + FILED);
        List<AuditRow> issued = onTheTrail(work -> Audit.ofAction(work.entityManager(), "permit.issued"));
        assertThat(issued).isEmpty();
    }

    @Test
    void readingNeverLandsOnTheTrail() {
        Http clerk = as(Role.CLERK);
        String reference = Bodies.filed(clerk, application());
        clerk.get("/applications/" + reference);
        clerk.get("/applications/" + reference + "/assessment");
        List<AuditRow> found = onTheTrail(work -> Audit.forDocument(work.entityManager(), "application", reference));
        assertThat(found).hasSize(1);
    }
}
