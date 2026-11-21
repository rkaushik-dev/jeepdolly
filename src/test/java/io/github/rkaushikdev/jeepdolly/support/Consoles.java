package io.github.rkaushikdev.jeepdolly.support;

import io.github.rkaushikdev.jeepdolly.Settings;
import io.github.rkaushikdev.jeepdolly.cli.Console;
import io.github.rkaushikdev.jeepdolly.cli.Main;
import io.github.rkaushikdev.jeepdolly.cli.Terminal;
import io.github.rkaushikdev.jeepdolly.persistence.Database;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Running a command the way every command line test does, and reading back what it printed.
 *
 * <p>A run gets an empty environment, an empty keyboard, and two streams that are also written,
 * interleaved in the order they were written, to one output: a test asserts on what a person at the
 * terminal would have seen. Anything the command line lets out unexpectedly is caught and kept, with
 * an exit code of one, which is what the process would have exited with.
 */
public final class Consoles {

    private Consoles() {}

    /** What one run printed, how it exited, and what got out of it unexpectedly. */
    public record Answer(int exitCode, String output, String out, String err, Optional<Throwable> exception) {

        /** The lines the command printed, which is what the assertions are about. */
        public List<String> shown() {
            return output.lines().toList();
        }
    }

    /** A console over the suite's database, whatever state the test left it in. */
    public static Console over(Database database) {
        return new Console(Settings.defaults().withDatabase(Databases.URL), database);
    }

    /**
     * Run one command against a console.
     *
     * <p>Quiet is always passed. The log goes to standard error and the output mixes the two streams,
     * so a test that did not pass it would be asserting on log lines it never meant to read.
     */
    public static Answer call(Console console, String... arguments) {
        List<String> quiet = new ArrayList<>();
        quiet.add("--quiet");
        quiet.addAll(List.of(arguments));
        return run(Optional.of(console), quiet.toArray(String[]::new));
    }

    /** Run one command line exactly as typed, against the database it names itself. */
    public static Answer invoke(String... arguments) {
        return run(Optional.empty(), arguments);
    }

    /** The lines a command printed. */
    public static List<String> shown(Answer answer) {
        return answer.shown();
    }

    private static Answer run(Optional<Console> console, String... arguments) {
        StringBuilder output = new StringBuilder();
        StringBuilder out = new StringBuilder();
        StringBuilder err = new StringBuilder();
        Terminal terminal = new Terminal(
                Map.<String, String>of()::get,
                new BufferedReader(new StringReader("")),
                new PrintWriter(new Both(output, out), true),
                new PrintWriter(new Both(output, err), true));
        int code;
        Optional<Throwable> escaped = Optional.empty();
        try {
            code = console.map(handed -> Main.run(terminal, handed, arguments))
                    .orElseGet(() -> Main.run(terminal, arguments));
        } catch (RuntimeException unexpected) {
            code = 1;
            escaped = Optional.of(unexpected);
        }
        return new Answer(code, output.toString(), out.toString(), err.toString(), escaped);
    }

    /** A writer that writes everything both to the shared output and to its own stream. */
    private static final class Both extends Writer {

        private final StringBuilder shared;
        private final StringBuilder own;

        Both(StringBuilder shared, StringBuilder own) {
            this.shared = shared;
            this.own = own;
        }

        @Override
        public void write(char[] text, int offset, int length) {
            shared.append(text, offset, length);
            own.append(text, offset, length);
        }

        @Override
        public void flush() {
            // Nothing is held back: every write lands in both buffers at once.
        }

        @Override
        public void close() {
            // The buffers outlive the run, so there is nothing to release.
        }
    }
}
