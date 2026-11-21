package io.github.rkaushikdev.jeepdolly.cli;

import static io.github.rkaushikdev.jeepdolly.support.Consoles.invoke;
import static io.github.rkaushikdev.jeepdolly.support.Consoles.shown;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.jeepdolly.Logs;
import io.github.rkaushikdev.jeepdolly.OverLimit;
import io.github.rkaushikdev.jeepdolly.Settings;
import io.github.rkaushikdev.jeepdolly.Written;
import io.github.rkaushikdev.jeepdolly.support.Consoles;
import io.github.rkaushikdev.jeepdolly.support.Consoles.Answer;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.event.Level;
import picocli.CommandLine.Command;

/** The console, its three exit codes, and the plain lines it prints. */
class CliMainTest {

    @TempDir
    Path tmp;

    @AfterEach
    void theLogGoesBackToStandardError() {
        Logs.configure(Level.WARN, null);
    }

    /** A file database in the test's own directory, which nothing has built yet. */
    private String scratch() {
        return "jdbc:h2:file:" + tmp.resolve("office");
    }

    /** What a run printed on each stream, for the tests that call the entry point directly. */
    private record Streams(Terminal terminal, StringWriter out, StringWriter err) {

        static Streams empty() {
            StringWriter out = new StringWriter();
            StringWriter err = new StringWriter();
            Terminal terminal = new Terminal(
                    Map.<String, String>of()::get,
                    new BufferedReader(new StringReader("")),
                    new PrintWriter(out, true),
                    new PrintWriter(err, true));
            return new Streams(terminal, out, err);
        }

        int run(Object extra, String... arguments) {
            return Main.run(terminal, Optional.empty(), List.of(extra), arguments);
        }
    }

    @Command(name = "sample")
    static final class Sample extends Action {

        @Override
        public Integer call() {
            say("25-P-004471", "issued", "94,000 lb", null);
            return Main.DONE;
        }
    }

    @Command(name = "refuser")
    static final class Refuser extends Action {

        @Override
        public Integer call() {
            throw new OverLimit("the tandem is 4,000 lb over");
        }
    }

    @Command(name = "walker")
    static final class Walker extends Action {

        @Override
        public Integer call() {
            throw new Stopped();
        }
    }

    @Command(name = "breaker")
    static final class Breaker extends Action {

        @Override
        public Integer call() {
            throw new IllegalStateException("a defect, not a refusal");
        }
    }

    @Test
    void theConsoleSaysWhichVersionItIs() {
        Answer answer = invoke("--version");
        assertThat(answer.exitCode()).isZero();
        assertThat(answer.output()).contains("jeepdolly");
    }

    @Test
    void theHelpNamesWhatTheOfficeDoes() {
        Answer answer = invoke("--help");
        assertThat(answer.output()).contains("Permits for the motor carrier office");
    }

    @Test
    void buildingAScratchDatabaseAndReachingIt() {
        String scratch = scratch();
        Answer built = invoke("--database", scratch, "--quiet", "build");
        assertThat(built.exitCode()).isZero();
        assertThat(shown(built)).containsExactly("tariff  seeded", "schema  built  " + scratch);
        Answer checked = invoke("--database", scratch, "--quiet", "health");
        assertThat(checked.exitCode()).isZero();
        assertThat(checked.output()).startsWith("database  up");
    }

    @Test
    void aDatabaseThatIsNotThereIsAFailureAndNotAZero() {
        // A file database makes the directories it needs, so the url says what is meant: open the
        // database only if it is there, and it is not.
        String missing = "jdbc:h2:file:" + tmp.resolve("nowhere").resolve("office") + ";IFEXISTS=TRUE";
        Answer answer = invoke("--database", missing, "--quiet", "health");
        assertThat(answer.exitCode()).isNotZero();
    }

    @Test
    void aCommandThatDoesNotExistIsAUsageError() {
        Answer answer = invoke("--quiet", "nonsense");
        assertThat(answer.exitCode()).isEqualTo(2);
    }

    @Test
    void aRecordIsOneLineWithTwoSpacesBetweenTheFields() {
        Streams streams = Streams.empty();
        streams.run(new Sample(), "--quiet", "sample");
        assertThat(streams.out()).hasToString("25-P-004471  issued  94,000 lb  \n");
    }

    @Test
    void theConsoleHoldsTheSettingsItWasBuiltWith() {
        try (Console console = new Console(Settings.defaults().withDatabase("jdbc:h2:mem:held"))) {
            assertThat(console.settings().inMemory()).isTrue();
            assertThat(console.database()).isSameAs(console.database());
        }
    }

    @Test
    void aRefusalOutOfACommandIsExitCodeOne() {
        Streams streams = Streams.empty();
        assertThat(streams.run(new Refuser(), "--quiet", "refuser")).isEqualTo(1);
        assertThat(streams.err().toString()).contains("over_limit: the tandem is 4,000 lb over");
    }

    @Test
    void aUsageMistakeOutOfRunIsExitCodeTwo() {
        assertThat(Main.run(Streams.empty().terminal(), "--quiet", "nonsense")).isEqualTo(2);
    }

    @Test
    void aCommandThatWorksIsExitCodeZero() {
        String where = "jdbc:h2:file:" + tmp.resolve("office");
        assertThat(Main.run(Streams.empty().terminal(), "--database", where, "--quiet", "build"))
                .isZero();
    }

    @Test
    void aQuestionTheOperatorWalksAwayFromIsStoppedAndExitCodeOne() {
        Streams streams = Streams.empty();
        assertThat(streams.run(new Walker(), "--quiet", "walker")).isEqualTo(1);
        assertThat(streams.err()).hasToString("stopped\n");
        assertThat(streams.out().toString()).isEmpty();
    }

    @Test
    void aDefectComesOutOfTheCommandLineAsItWasThrown() {
        Streams streams = Streams.empty();
        assertThatThrownBy(() -> streams.run(new Breaker(), "--quiet", "breaker"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("a defect, not a refusal");
        assertThat(streams.err().toString()).isEmpty();
    }

    @Test
    void aUsageMistakeSaysWhatWasWrongAndWhereTheHelpIs() {
        Answer answer = invoke("--quiet", "permits", "show");
        assertThat(answer.exitCode()).isEqualTo(2);
        assertThat(answer.out()).isEmpty();
        assertThat(answer.err())
                .contains("Usage: jeepdolly permits show")
                .contains("Try 'jeepdolly permits show --help' for help.")
                .contains("Error: Missing required parameter: 'NUMBER'");
    }

    @Test
    void everyCommandAnswersHelpAndNothingAnswersTheShortForm() {
        Answer help = invoke("roads", "load", "--help");
        assertThat(help.exitCode()).isZero();
        assertThat(help.output()).contains("Read a published log in.").contains("--dry-run");
        assertThat(invoke("roads", "load", "-h").exitCode()).isEqualTo(2);
    }

    @Test
    void aGroupWithNoCommandNamedPrintsItsHelp() {
        Answer answer = invoke("--quiet", "citations");
        assertThat(answer.exitCode()).isZero();
        assertThat(answer.output()).contains("owing").contains("watch");
    }

    @Test
    void theLogGoesToStandardErrorUnlessTheCommandIsToldToBeQuiet() {
        Console console = Consoles.over(Databases.seeded());
        Streams loud = Streams.empty();
        assertThat(Main.run(loud.terminal(), console, "nightly", "--day", "2025-04-01"))
                .isZero();
        assertThat(loud.err().toString()).contains("INFO nightly.done day=2025-04-01");
        assertThat(loud.out().toString()).doesNotContain("nightly.done");
        Streams quiet = Streams.empty();
        Main.run(quiet.terminal(), console, "--quiet", "nightly", "--day", "2025-04-01");
        assertThat(quiet.err().toString()).isEmpty();
    }

    @Test
    void aDayIsReadTheWayItIsWrittenAndNothingElse() {
        assertThat(Days.read("2025-04-08")).hasToString("2025-04-08");
        assertThat(Days.read("2025-4-8")).hasToString("2025-04-08");
        assertThat(Days.read("2025-04- 8")).hasToString("2025-04-08");
        for (String wrong :
                List.of("last tuesday", "2025-02-30", "0000-01-01", "25-04-08", "2025-04-08 ", "2025/04/08")) {
            assertThatThrownBy(() -> Days.read(wrong))
                    .hasMessage(Written.quoted(wrong) + " does not match the format '%Y-%m-%d'.");
        }
    }
}
