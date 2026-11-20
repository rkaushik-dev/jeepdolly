package io.github.rkaushikdev.jeepdolly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

/** Log lines the office can grep, and the values that never appear in one. */
class LogsTest {

    private final ByteArrayOutputStream stream = new ByteArrayOutputStream();

    @AfterEach
    void pointTheLoggerBackAtStandardError() {
        Logs.configure(Level.WARN, null);
    }

    private String written() {
        return stream.toString(StandardCharsets.UTF_8);
    }

    @Test
    void aLineLeadsWithTheEventAndThenKeyAndValue() {
        String written = Logs.line("permit.issued", Facts.of("number", "25-P-004471", "usdot", 1204471));
        assertThat(written).isEqualTo("permit.issued number=25-P-004471 usdot=1204471");
    }

    @Test
    void theFieldsKeepTheOrderTheyWereGivenIn() {
        String written = Logs.line("route.checked", Facts.of("origin", "waterville", "destination", "pittsfield"));
        assertThat(written.indexOf("origin")).isLessThan(written.indexOf("destination"));
    }

    @Test
    void aValueWithASpaceInItIsQuotedAndOneWithoutIsNot() {
        String written = Logs.line("carrier.suspended", Facts.of("name", "Bickford Heavy Haul", "usdot", 1204471));
        assertThat(written).contains("name=\"Bickford Heavy Haul\"");
        assertThat(written).contains("usdot=1204471");
    }

    @Test
    void aQuoteInsideAValueDoesNotBreakTheLine() {
        String written = Logs.line("permit.voided", Facts.of("reason", "the customer said \"no\""));
        assertThat(written.chars().filter(one -> one == '"').count()).isEqualTo(2);
        assertThat(written).contains("'no'");
    }

    @Test
    void anEmptyValueIsWrittenAsEmptyQuotes() {
        assertThat(Logs.line("permit.issued", Facts.of("detail", ""))).isEqualTo("permit.issued detail=\"\"");
        Map<String, Object> none = new HashMap<>();
        none.put("detail", null);
        assertThat(Logs.line("permit.issued", none)).isEqualTo("permit.issued detail=\"\"");
    }

    @Test
    void anythingThatLooksLikeASecretIsNeverWritten() {
        String written = Logs.line("key.issued", Facts.of("person", "Dana", "api_key", "abc123", "digest", "deadbeef"));
        assertThat(written).doesNotContain("abc123");
        assertThat(written).doesNotContain("deadbeef");
        assertThat(written.split("\\*\\*\\*", -1)).hasSize(3);
    }

    @Test
    void aLineWithNoEventIsAMistakeInTheCaller() {
        assertThatThrownBy(() -> Logs.line("   ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aLineWithNoFieldsIsJustTheEvent() {
        assertThat(Logs.line("service.started")).isEqualTo("service.started");
    }

    @Test
    void theOfficeLoggerWritesWhereItWasPointed() {
        Logs.configure(Level.INFO, stream);
        Logs.event("permit.issued", Facts.of("number", "25-P-004471"));
        assertThat(written()).contains("permit.issued number=25-P-004471");
        assertThat(written()).contains("INFO");
    }

    @Test
    void troubleAndFailureGoOutAtTheirOwnLevels() {
        Logs.configure(Level.INFO, stream);
        Logs.trouble("route.blocked", Facts.of("segment", "US2-1"));
        Logs.failure("database.unreachable", Facts.of("url", "sqlite"));
        assertThat(written()).contains("WARNING route.blocked segment=US2-1");
        assertThat(written()).contains("ERROR database.unreachable");
    }

    @Test
    void configuringTwiceDoesNotWriteEveryLineTwice() {
        Logs.configure(Level.INFO, stream);
        Logs.configure(Level.INFO, stream);
        Logs.event("service.started");
        assertThat(written().split("service\\.started", -1)).hasSize(2);
    }

    @Test
    void theLevelKeepsTheQuietLinesOut() {
        Logs.configure(Level.WARN, stream);
        Logs.event("permit.issued", Facts.of("number", "25-P-004471"));
        Logs.trouble("route.blocked", Facts.of("segment", "US2-1"));
        assertThat(written()).doesNotContain("permit.issued");
        assertThat(written()).contains("route.blocked");
    }
}
