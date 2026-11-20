package io.github.rkaushikdev.jeepdolly.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.jeepdolly.Written;
import io.github.rkaushikdev.jeepdolly.persistence.Database;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.persistence.models.ApiKeyRow;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import io.github.rkaushikdev.jeepdolly.testing.Guarded;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** Keys, the four roles, and the ladder between them. */
@WebMvcTest
@Import({TestService.class, Guarded.class})
class ApiSecurityTest {

    private static final LocalDate TODAY = LocalDate.now();

    @Autowired
    private MockMvc mvc;

    private Database database;
    private Http client;
    private Map<Role, String> keys;
    private String revoked;

    @BeforeEach
    void aSeededDatabaseWithAKeyForEveryRoleAndOneRevoked() {
        database = Databases.seeded();
        client = new Http(mvc);
        keys = new EnumMap<>(Role.class);
        try (UnitOfWork work = database.unitOfWork()) {
            for (Role role : Role.values()) {
                keys.put(
                        role,
                        Security.issueKey(work, "k-" + role.value(), Written.title(role.value()), role, TODAY)
                                .key());
            }
            Security.IssuedKey old = Security.issueKey(work, "k-old", "Gone Away", Role.CLERK, TODAY);
            old.row().setRevoked(TODAY);
            revoked = old.key();
            work.commit();
        }
    }

    private Http as(Role role) {
        return client.as(keys.get(role));
    }

    @Test
    void aKeyIsNeverStoredOnlyItsHash() {
        try (UnitOfWork work = database.unitOfWork()) {
            Security.IssuedKey issued = Security.issueKey(work, "k-1", "Dana Bickford", Role.CLERK, TODAY);
            work.commit();
            assertThat(issued.row().getDigest()).isEqualTo(Security.digestOf(issued.key()));
            assertThat(issued.row().getDigest()).doesNotContain(issued.key());
            assertThat(issued.row().getDigest()).hasSize(64);
        }
    }

    @Test
    void twoKeysAreNeverTheSame() {
        assertThat(Security.newKey()).isNotEqualTo(Security.newKey());
        assertThat(Security.newKey().length()).isGreaterThan(20);
    }

    @Test
    void aRequestWithNoKeyAtAllIsRefused() {
        assertThat(client.get("/guarded/reader").status()).isEqualTo(401);
    }

    @Test
    void aKeyNobodyIssuedIsRefusedWithoutSayingWhy() {
        Http.Answer answer = client.as("not-a-key").get("/guarded/reader");
        assertThat(answer.status()).isEqualTo(401);
        assertThat(answer.json().get("detail").asText()).isEqualTo("that key is not in use");
    }

    @Test
    void aRevokedKeyIsRefusedTheSameWay() {
        assertThat(client.as(revoked).get("/guarded/reader").status()).isEqualTo(401);
    }

    @Test
    void aReaderMayReadAndNothingElse() {
        Http reader = as(Role.READER);
        assertThat(reader.get("/guarded/reader").status()).isEqualTo(200);
        assertThat(reader.get("/guarded/clerk").status()).isEqualTo(403);
        assertThat(reader.get("/guarded/supervisor").status()).isEqualTo(403);
    }

    @Test
    void aClerkReachesEverythingAReaderDoes() {
        Http clerk = as(Role.CLERK);
        assertThat(clerk.get("/guarded/reader").status()).isEqualTo(200);
        assertThat(clerk.get("/guarded/clerk").status()).isEqualTo(200);
        assertThat(clerk.get("/guarded/supervisor").status()).isEqualTo(403);
    }

    @Test
    void aSupervisorReachesEverything() {
        Http supervisor = as(Role.SUPERVISOR);
        for (String route : List.of("reader", "clerk", "supervisor")) {
            assertThat(supervisor.get("/guarded/" + route).status()).as(route).isEqualTo(200);
        }
    }

    @Test
    void theRefusalSaysWhatWasNeededAndWhatWasHeld() {
        Http.Answer answer = as(Role.CLERK).get("/guarded/supervisor");
        assertThat(answer.json().get("detail").asText()).isEqualTo("this needs a supervisor, and you are a clerk");
    }

    @Test
    void theCallerCarriesTheNameThatGoesOnThePermit() {
        JsonNode found = as(Role.ENGINEER).get("/guarded/reader").json();
        assertThat(found.get("person").asText()).isEqualTo("Engineer");
        assertThat(found.get("role").asText()).isEqualTo("engineer");
    }

    @Test
    void aCarrierKeyCarriesTheNumberItBelongsTo() {
        String key;
        try (UnitOfWork work = database.unitOfWork()) {
            key = Security.issueKey(work, "k-carrier", "D Bickford", Role.READER, TODAY, 1204471)
                    .key();
            work.commit();
        }
        JsonNode found = client.as(key).get("/guarded/whoami").json();
        assertThat(found.get("usdot").asInt()).isEqualTo(1204471);
        assertThat(found.get("carrier").asBoolean()).isTrue();
    }

    @Test
    void anOfficeKeyBelongsToNobodyInParticular() {
        JsonNode found = as(Role.CLERK).get("/guarded/whoami").json();
        assertThat(found.get("usdot").isNull()).isTrue();
        assertThat(found.get("carrier").asBoolean()).isFalse();
    }

    @Test
    void aKeyIssuedForTomorrowIsNotLiveToday() {
        ApiKeyRow row = new ApiKeyRow(
                "k-future", Security.digestOf("whatever"), "Later", "clerk", null, LocalDate.of(2030, 1, 1), "");
        assertThat(row.liveOn(TODAY)).isFalse();
        assertThat(row.liveOn(LocalDate.of(2030, 6, 1))).isTrue();
        assertThat(row).hasToString("<ApiKeyRow k-future clerk>");
    }

    @Test
    void theLadderIsALadder() {
        assertThat(Role.SUPERVISOR.reaches(Role.READER)).isTrue();
        assertThat(Role.READER.reaches(Role.CLERK)).isFalse();
        assertThat(Role.ENGINEER.rank()).isGreaterThan(Role.CLERK.rank());
        assertThat(Role.ENGINEER).hasToString("engineer");
    }
}
