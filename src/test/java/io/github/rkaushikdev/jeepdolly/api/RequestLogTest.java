package io.github.rkaushikdev.jeepdolly.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.jeepdolly.Logs;
import io.github.rkaushikdev.jeepdolly.persistence.Database;
import io.github.rkaushikdev.jeepdolly.persistence.ReferenceCache;
import io.github.rkaushikdev.jeepdolly.support.Clients;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** One line per request, and what is deliberately not in it. */
@WebMvcTest
@Import(TestService.class)
class RequestLogTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ReferenceCache cache;

    private final ByteArrayOutputStream stream = new ByteArrayOutputStream();
    private Http client;

    @BeforeEach
    void aSupervisorAndALogToRead() {
        Database database = Databases.seeded();
        cache.reset();
        client = Clients.keyed(new Http(mvc), database);
        Logs.configure(Level.INFO, stream);
    }

    @AfterEach
    void pointTheLoggerBackAtStandardError() {
        Logs.configure(Level.WARN, null);
    }

    private String written() {
        return stream.toString(StandardCharsets.UTF_8);
    }

    @Test
    void everyRequestLeavesOneLineWithWhatWasAsked() {
        client.get("/health");
        String found = written();
        assertThat(found).contains("request.done");
        assertThat(found).contains("method=GET");
        assertThat(found).contains("path=/health");
        assertThat(found).contains("status=200");
    }

    @Test
    void theLineCarriesHowLongItTook() {
        client.get("/health");
        assertThat(written()).contains("ms=");
    }

    @Test
    void aRefusalIsLoggedAtInfoBecauseItIsTheServiceWorking() {
        client.get("/permits/25-P-999999");
        String found = written();
        assertThat(found).contains("status=404");
        assertThat(found).doesNotContain("ERROR");
    }

    @Test
    void thePermitNumberIsInThePathAndSoItIsInTheLine() {
        client.get("/permits/25-P-999999");
        assertThat(written()).contains("path=/permits/25-P-999999");
    }

    @Test
    void theQueryStringNeverReachesTheLog() {
        Bodies.file(client, Bodies.application());
        String found = written();
        assertThat(found).contains("path=/applications");
        assertThat(found).doesNotContain("filed=2025-04-07");
    }

    @Test
    void issuingWritesItsOwnLineWithTheThingsWorthGrepping() {
        Bodies.issued(client);
        String found = written();
        assertThat(found).contains("permit.issued number=25-P-000001");
        assertThat(found).contains("usdot=1204471");
        assertThat(found).contains("gross=94000");
        assertThat(found).contains("by=Supervisor");
    }

    @Test
    void voidingWritesItsOwnLineToo() {
        String number = Bodies.issued(client);
        client.post("/permits/" + number + "/void?reason=load cancelled&on=2025-04-08");
        assertThat(written()).contains("permit.voided number=" + number);
    }

    @Test
    void theKeyThatWasSentIsNeverInTheLog() {
        client.get("/health");
        String key = client.key().orElseThrow();
        assertThat(written()).doesNotContain(key);
    }

    @Test
    void aDurationIsWholeMillisecondsFromAStart() throws NoSuchMethodException {
        assertThat(RequestLog.durationSince(System.nanoTime())).isGreaterThanOrEqualTo(0);
        Method duration = RequestLog.class.getMethod("durationSince", long.class);
        assertThat(duration.getReturnType()).isEqualTo(int.class);
        assertThat(RequestLog.SLOW_MILLISECONDS).isEqualTo(500);
    }
}
