package io.github.rkaushikdev.jeepdolly.cli;

import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * One command the office types: it works through the console, prints plain lines, and answers an
 * exit code.
 *
 * <p>Every command extends this. It cannot throw a checked exception, so anything it did not expect
 * comes out of {@link Main#run} exactly as it was thrown; a refusal is turned into its line on
 * standard error and {@link Main#REFUSED} there.
 */
abstract class Action implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    /** Do the work and say how it went: {@link Main#DONE} unless the command says otherwise. */
    @Override
    public abstract Integer call();

    /** The root of the command line this command was run from. */
    final Main main() {
        return (Main) spec.root().userObject();
    }

    /** The settings and the database the command works against. */
    final Console console() {
        return main().console();
    }

    /** A fresh unit of work on the console's database. */
    final UnitOfWork work() {
        return console().work();
    }

    /** One record, one line, two spaces between the fields. */
    final void say(Object... fields) {
        echo(Main.record(fields));
    }

    /** One line, exactly as it was produced. */
    final void echo(String line) {
        main().terminal().out().println(line);
    }

    /** Several lines, in order, exactly as they were produced. */
    final void echo(Iterable<String> lines) {
        lines.forEach(this::echo);
    }

    /** One line on standard error, for the trouble a person has to see even when output is piped. */
    final void complain(String line) {
        main().terminal().err().println(line);
    }

    /** A file the command reads, which must be there. */
    final Path existingFile(String label, String given) {
        return FileArgument.existing(spec, label, given);
    }

    /** A file the command writes. */
    final Path fileToWrite(String label, String given) {
        return FileArgument.written(spec, label, given);
    }
}
