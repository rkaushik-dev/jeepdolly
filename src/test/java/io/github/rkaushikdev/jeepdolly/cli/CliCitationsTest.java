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

/** The citation commands: the chase list and the quarterly letters. */
class CliCitationsTest {

    private Console console;

    @BeforeEach
    void aConsoleOverASeededDatabase() {
        console = Consoles.over(Databases.seeded());
    }

    private Answer fileOne() {
        return fileOne("E-25-0417", "overweight", "2025-04-08", "12000");
    }

    private Answer fileOne(String number, String written) {
        return fileOne(number, "overweight", written, "12000");
    }

    private Answer fileOne(String number, String offence, String written, String over) {
        List<String> arguments = new ArrayList<>(List.of(
                "citations",
                "file",
                number,
                "1204471",
                offence,
                "--officer",
                "Sgt A Thibodeau",
                "--place",
                "US201-2 at Fairfield",
                "--written",
                written));
        if (over != null) {
            arguments.addAll(List.of("--over", over));
        }
        return call(console, arguments.toArray(String[]::new));
    }

    @Test
    void aTicketCanBeFiledFromTheCommandLine() {
        Answer answer = fileOne();
        assertThat(answer.exitCode()).isZero();
        assertThat(answer.output().strip()).isEqualTo("filed  E-25-0417  Bickford Heavy Haul  overweight  $580.00");
    }

    @Test
    void anOffenceNobodyPricesIsARefusalRatherThanATraceback() {
        Answer answer = fileOne("E-25-0417", "parked badly", "2025-04-08", null);
        assertThat(answer.exitCode()).isNotZero();
    }

    @Test
    void aCarrierNobodyHasIsARefusal() {
        Answer answer = call(
                console,
                "citations",
                "file",
                "E-25-0417",
                "9999999",
                "no permit",
                "--officer",
                "Sgt A Thibodeau",
                "--place",
                "US201-2",
                "--written",
                "2025-04-08");
        assertThat(answer.exitCode()).isNotZero();
    }

    @Test
    void theChaseListPrintsOneCitationToALine() {
        fileOne();
        Answer answer = call(console, "citations", "owing");
        assertThat(answer.exitCode()).isZero();
        assertThat(shown(answer)).containsExactly("E-25-0417  2025-04-08  Bickford Heavy Haul  $580.00  written");
    }

    @Test
    void anEmptyChaseListSaysSo() {
        assertThat(call(console, "citations", "owing").output().strip()).isEqualTo("nothing is owed");
    }

    @Test
    void payingItTakesItOffTheChaseList() {
        fileOne();
        Answer answer = call(console, "citations", "pay", "E-25-0417", "--on", "2025-04-22");
        assertThat(answer.output().strip()).isEqualTo("paid  E-25-0417  $580.00");
        assertThat(call(console, "citations", "owing").output().strip()).isEqualTo("nothing is owed");
    }

    @Test
    void everythingAgainstOneCarrierPrintsNewestFirst() {
        fileOne("E-25-0100", "2025-01-20");
        fileOne();
        List<String> printed = shown(call(console, "citations", "carrier", "1204471"));
        assertThat(printed.get(0)).startsWith("E-25-0417");
        assertThat(printed.get(1)).startsWith("E-25-0100");
    }

    @Test
    void oneCitationReadsOutInFull() {
        fileOne();
        List<String> printed = shown(call(console, "citations", "show", "E-25-0417"));
        assertThat(printed.get(0)).isEqualTo("E-25-0417  2025-04-08  Bickford Heavy Haul (1204471)");
        assertThat(printed.get(printed.size() - 1)).isEqualTo("  written");
    }

    @Test
    void aCitationNobodyHasIsARefusal() {
        assertThat(call(console, "citations", "show", "E-25-9999").exitCode()).isNotZero();
    }

    @Test
    void theQuarterReportPrintsTheCountsAndTheMoney() {
        fileOne();
        Answer answer = call(console, "citations", "report", "--from", "2025-01-01", "--to", "2025-12-31");
        assertThat(answer.exitCode()).isZero();
        List<String> printed = shown(answer);
        assertThat(printed.get(0)).isEqualTo("enforcement 2025-01-01 to 2025-12-31");
        assertThat(printed).contains("overweight  1");
        assertThat(printed).contains("assessed  $580.00");
    }

    @Test
    void aQuietQuarterSaysSo() {
        Answer answer = call(console, "citations", "report", "--from", "2025-01-01", "--to", "2025-12-31");
        List<String> printed = shown(answer);
        assertThat(printed.get(printed.size() - 1)).isEqualTo("nothing was written in that window");
    }

    @Test
    void theLetterListNeedsTwoBeforeItNamesAnybody() {
        fileOne();
        Answer answer = call(console, "citations", "watch", "--from", "2025-01-01", "--to", "2025-12-31");
        assertThat(answer.output().strip()).isEqualTo("nobody is over the line");
    }

    @Test
    void twoCitationsPutACarrierOnTheLetterList() {
        fileOne("E-25-0100", "2025-01-20");
        fileOne();
        Answer answer = call(console, "citations", "watch", "--from", "2025-01-01", "--to", "2025-12-31");
        assertThat(shown(answer)).containsExactly("1204471  Bickford Heavy Haul  2  $1,740.00");
    }

    @Test
    void theLineCanBeDrawnAtOne() {
        fileOne();
        Answer answer = call(console, "citations", "watch", "--from", "2025-01-01", "--to", "2025-12-31", "--at", "1");
        assertThat(shown(answer)).containsExactly("1204471  Bickford Heavy Haul  1  $580.00");
    }

    @Test
    void theSecondTicketFromTheCommandLineIsPricedAsARepeat() {
        fileOne("E-25-0100", "2025-01-20");
        Answer answer = fileOne();
        assertThat(answer.output().strip()).endsWith("$1,160.00");
    }
}
