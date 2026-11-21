package io.github.rkaushikdev.jeepdolly.cli;

import static io.github.rkaushikdev.jeepdolly.support.Consoles.call;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.jeepdolly.Written;
import io.github.rkaushikdev.jeepdolly.persistence.Database;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.support.Consoles;
import io.github.rkaushikdev.jeepdolly.support.Consoles.Answer;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The road log commands, including the dry run everybody runs first. */
class CliNetworkTest {

    private static final String NEW_LINE = "US201-4|US 201|anson|solon|910|Somerset|Anson|4|0";

    private static final String[] TO_MADISON = {"roads", "turn", "waterville", "US201-1", "US201-2", "US2-1", "SR8-1"};

    @TempDir
    Path tmp;

    private Database database;
    private Console console;

    @BeforeEach
    void aConsoleOverASeededDatabase() {
        database = Databases.seeded();
        console = Consoles.over(database);
    }

    private String written(String... lines) throws IOException {
        Path path = tmp.resolve("roadlog.txt");
        Files.writeString(path, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
        return path.toString();
    }

    private int segmentsOnFile() {
        try (UnitOfWork work = database.unitOfWork()) {
            return work.network().segments().size();
        }
    }

    private static String[] toMadison(String... more) {
        String[] arguments = new String[TO_MADISON.length + more.length];
        System.arraycopy(TO_MADISON, 0, arguments, 0, TO_MADISON.length);
        System.arraycopy(more, 0, arguments, TO_MADISON.length, more.length);
        return arguments;
    }

    @Test
    void theLogPrintsOneSegmentToALine() {
        Answer answer = call(console, "roads", "export");
        assertThat(answer.exitCode()).isZero();
        assertThat(answer.shown()).hasSize(12);
        assertThat(answer.shown().get(0)).startsWith("I95-1|");
    }

    @Test
    void theLogCanBeWrittenToAFile() throws IOException {
        String path = tmp.resolve("out.txt").toString();
        Answer answer = call(console, "roads", "export", "--to", path);
        assertThat(answer.output().strip()).isEqualTo("wrote  12  " + path);
        assertThat(Written.lines(Files.readString(Path.of(path), StandardCharsets.UTF_8)))
                .hasSize(12);
    }

    @Test
    void aDryRunReadsTheFileAndChangesNothing() throws IOException {
        String path = written(NEW_LINE);
        Answer answer = call(console, "roads", "load", path, "--dry-run");
        assertThat(answer.output()).contains("1 added");
        assertThat(segmentsOnFile()).isEqualTo(12);
    }

    @Test
    void aRealLoadPutsTheSegmentOn() throws IOException {
        String path = written(NEW_LINE);
        Answer answer = call(console, "roads", "load", path);
        assertThat(answer.output()).contains("1 added");
        assertThat(segmentsOnFile()).isEqualTo(13);
    }

    @Test
    void aBadLineIsReportedByNumberAndTheRestGoesIn() throws IOException {
        String path = written("nonsense", NEW_LINE);
        Answer answer = call(console, "roads", "load", path);
        assertThat(answer.output()).contains("1 added");
        assertThat(answer.output()).contains("line 1");
        assertThat(segmentsOnFile()).isEqualTo(13);
    }

    @Test
    void aFileThatIsNotThereIsAUsageError() {
        assertThat(call(console, "roads", "load", "nowhere.txt").exitCode()).isEqualTo(2);
    }

    @Test
    void theLogRoundTripsThroughAFile() {
        String path = tmp.resolve("out.txt").toString();
        call(console, "roads", "export", "--to", path);
        Answer answer = call(console, "roads", "load", path);
        assertThat(answer.output()).contains("12 updated");
        assertThat(answer.output()).contains("0 refused");
    }

    @Test
    void aSegmentPrintsWithWhatIsOnIt() {
        List<String> lines = call(console, "roads", "segment", "US201-2").shown();
        assertThat(lines.get(0)).startsWith("US201-2  US 201  fairfield  skowhegan  12.40 mi");
        assertThat(lines.get(1).strip()).startsWith("structure  1247  Kennebec River");
    }

    @Test
    void aSegmentNobodyHasIsARefusal() {
        Answer answer = call(console, "roads", "segment", "NOPE-1");
        assertThat(answer.exitCode()).isEqualTo(1);
    }

    @Test
    void aDistrictPrintsWhatAThawOrderWouldReach() {
        Answer answer = call(console, "roads", "district", "4");
        assertThat(answer.shown()).hasSize(7);
        assertThat(call(console, "roads", "district", "9").output()).isEmpty();
    }

    @Test
    void theCornerListPrintsTheTightOnesOneToALine() {
        Answer answer = call(console, "roads", "corners");
        assertThat(answer.exitCode()).isZero();
        List<String> lines = answer.shown();
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0)).startsWith("madison  SR8-1  TR104-1  35");
    }

    @Test
    void aWiderRadiusPutsMoreCornersOnThePrintedList() {
        Answer answer = call(console, "roads", "corners", "--under", "70");
        assertThat(answer.exitCode()).isZero();
        assertThat(answer.shown().size()).isGreaterThan(2);
    }

    @Test
    void aRadiusNothingIsUnderPrintsASentence() {
        Answer answer = call(console, "roads", "corners", "--under", "10");
        assertThat(answer.exitCode()).isZero();
        assertThat(answer.output().strip()).isEqualTo("nothing on the card index is under that radius");
    }

    @Test
    void theCornerListTakesAPairOfDates() {
        Answer answer = call(console, "roads", "corners", "--from", "2025-01-01", "--to", "2025-12-31");
        assertThat(answer.exitCode()).isZero();
        assertThat(answer.output()).contains("madison");
    }

    @Test
    void oneWrittenRouteComesBackCornerByCorner() {
        Answer answer = call(console, TO_MADISON);
        assertThat(answer.exitCode()).isZero();
        List<String> lines = answer.shown();
        assertThat(lines.get(0)).isEqualTo("corners on waterville to madison");
        assertThat(lines.get(1)).contains("clear");
    }

    @Test
    void aLongLoadOnTheSameRouteIsStoppedAndSaysWhere() {
        Answer answer = call(console, toMadison("--length", "150'"));
        assertThat(answer.exitCode()).isZero();
        List<String> lines = answer.shown();
        assertThat(lines.get(lines.size() - 1)).isEqualTo("this load is stopped at 2 of them");
    }

    @Test
    void aRearOverhangIsReadOnTheTurnCommand() {
        Answer plain = call(console, TO_MADISON);
        Answer hanging = call(console, toMadison("--rear", "20'"));
        assertThat(plain.output()).isNotEqualTo(hanging.output());
        assertThat(hanging.output()).contains("will not get round");
    }

    @Test
    void aRouteThatCannotBeDrivenIsARefusalRatherThanATraceback() {
        Answer answer = call(console, "roads", "turn", "waterville", "SR8-1");
        assertThat(answer.exitCode()).isNotZero();
    }
}
