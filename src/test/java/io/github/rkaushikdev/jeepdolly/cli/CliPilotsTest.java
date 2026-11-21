package io.github.rkaushikdev.jeepdolly.cli;

import static io.github.rkaushikdev.jeepdolly.support.Consoles.call;
import static io.github.rkaushikdev.jeepdolly.support.Consoles.shown;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.jeepdolly.support.Consoles;
import io.github.rkaushikdev.jeepdolly.support.Consoles.Answer;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The pilot car commands, and the renewal run that made them necessary. */
class CliPilotsTest {

    private Console console;

    @BeforeEach
    void aConsoleOverASeededDatabase() {
        console = Consoles.over(Databases.seeded());
    }

    /** A new card for P Ouellette, with whatever endorsements are named. */
    private Answer certify(String... endorsements) {
        List<String> arguments = new ArrayList<>(List.of(
                "pilots",
                "certify",
                "C-1004",
                "P Ouellette",
                "PC-1",
                "--issued",
                "2025-06-02",
                "--expires",
                "2028-06-01"));
        for (String endorsement : endorsements) {
            arguments.addAll(List.of("--endorsement", endorsement));
        }
        return call(console, arguments.toArray(String[]::new));
    }

    private static String last(List<String> lines) {
        return lines.get(lines.size() - 1);
    }

    @Test
    void theCompaniesPrintWithHowManyCardsEachHolds() {
        Answer answer = call(console, "pilots", "companies");
        assertThat(answer.exitCode()).isZero();
        assertThat(shown(answer).get(0)).startsWith("PC-1  Kennebec Pilot Car  Waterville  207 555 0142  3");
    }

    @Test
    void aSuspendedCompanyIsMarkedAndCanBeAskedForAlone() {
        call(console, "pilots", "companies");
        assertThat(call(console, "pilots", "companies", "--suspended").output()).isEmpty();
    }

    @Test
    void everyCardPrintsWithItsStandingOnTheDay() {
        Answer answer = call(console, "pilots", "cards", "--day", "2025-06-10");
        assertThat(answer.exitCode()).isZero();
        List<String> printed = shown(answer);
        assertThat(printed.get(0)).endsWith("2026-04-30  good  ");
        assertThat(printed.get(2)).contains("expired");
    }

    @Test
    void theEndorsementShowsOnTheCardLine() {
        List<String> printed = shown(call(console, "pilots", "cards", "--day", "2025-06-10"));
        assertThat(printed.get(1)).endsWith("height pole");
    }

    @Test
    void theCardsOfOneCompanyCanBeAskedFor() {
        Answer answer = call(console, "pilots", "cards", "--company", "PC-1");
        assertThat(shown(answer)).hasSize(3);
    }

    @Test
    void theRenewalRunPrintsWhoToRingSoonestFirst() {
        Answer answer = call(console, "pilots", "expiring", "--day", "2026-02-01", "--within", "120");
        assertThat(answer.exitCode()).isZero();
        List<String> printed = shown(answer);
        assertThat(printed).hasSize(2);
        assertThat(printed.get(0)).startsWith("C-1001  R Dubois  Kennebec Pilot Car  207 555 0142  2026-04-30");
        assertThat(printed.get(0)).endsWith("  88");
    }

    @Test
    void aWindowWithNothingInItSaysSo() {
        Answer answer = call(console, "pilots", "expiring", "--day", "2030-01-01", "--within", "10");
        assertThat(answer.output().strip()).isEqualTo("nothing runs out in that window");
    }

    @Test
    void aCompanyCanBeFiledFromTheCommandLine() {
        Answer answer = call(console, "pilots", "company", "PC-2", "Somerset Escort", "--town", "Skowhegan");
        assertThat(answer.exitCode()).isZero();
        assertThat(answer.output().strip()).isEqualTo("filed  PC-2  Somerset Escort");
        assertThat(shown(call(console, "pilots", "companies"))).hasSize(2);
    }

    @Test
    void filingTheSameCompanyTwiceIsARefusalAndNotATraceback() {
        call(console, "pilots", "company", "PC-2", "Somerset Escort");
        assertThat(call(console, "pilots", "company", "PC-2", "Somerset Escort").exitCode())
                .isNotZero();
    }

    @Test
    void aCardCanBeIssuedFromTheCommandLine() {
        Answer answer = certify();
        assertThat(answer.exitCode()).isZero();
        assertThat(answer.output().strip()).isEqualTo("certified  C-1004  P Ouellette  PC-1  2028-06-01");
    }

    @Test
    void anEndorsementCanBeGivenMoreThanOnce() {
        Answer answer = certify("height pole", "police liaison");
        assertThat(answer.exitCode()).isZero();
        List<String> printed = shown(call(console, "pilots", "cards", "--day", "2025-06-10"));
        assertThat(last(printed)).endsWith("height pole,police liaison");
    }

    @Test
    void anEndorsementNobodyIssuesIsARefusal() {
        Answer answer = certify("railroad flagging");
        assertThat(answer.exitCode()).isNotZero();
    }

    @Test
    void anOperatorCanBeSuspendedAndLetBack() {
        Answer stopped = call(console, "pilots", "suspend", "C-1001");
        assertThat(stopped.output().strip()).isEqualTo("suspended  C-1001  R Dubois");
        assertThat(call(console, "pilots", "cards", "--day", "2025-06-10").output())
                .contains("suspended");
        Answer lifted = call(console, "pilots", "suspend", "C-1001", "--lift");
        assertThat(lifted.output().strip()).isEqualTo("lifted  C-1001  R Dubois");
    }

    @Test
    void whoIsAvailableOnADayPrintsWithTheTelephoneNumber() {
        Answer answer = call(console, "pilots", "available", "--day", "2025-06-10");
        assertThat(answer.exitCode()).isZero();
        assertThat(shown(answer)).hasSize(2);
        assertThat(shown(answer).get(0)).isEqualTo("C-1001  R Dubois  Kennebec Pilot Car  207 555 0142");
    }

    @Test
    void theEndorsementNarrowsWhoIsAvailable() {
        Answer answer = call(console, "pilots", "available", "--day", "2025-06-10", "--endorsement", "height pole");
        assertThat(shown(answer)).containsExactly("C-1002  M Levesque  Kennebec Pilot Car  207 555 0142");
    }

    @Test
    void aDayWhenNobodyIsAvailableSaysSoRatherThanPrintingNothing() {
        Answer answer = call(console, "pilots", "available", "--day", "2030-01-01");
        assertThat(answer.exitCode()).isZero();
        assertThat(answer.output().strip()).isEqualTo("nobody is available that day");
    }
}
