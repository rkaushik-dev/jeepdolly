package io.github.rkaushikdev.jeepdolly.cli;

import io.github.rkaushikdev.jeepdolly.Written;
import io.github.rkaushikdev.jeepdolly.exports.Federal;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Export commands, which are run on the first working day of the month.
 *
 * <p>Two of them and the second is the one that matters. Writing the return is a query and a
 * format. Verifying it is reading the written file back and checking it the way the receiving
 * system will, and it is a separate command rather than part of writing because the file gets
 * edited by hand between the two.
 *
 * <p>That sounds like it should not happen and it happens every year. A carrier name with a
 * character the federal office will not take, a permit that should not have been in the month, a
 * line somebody deleted with a text editor that helpfully stripped the trailing blanks. Verifying
 * reads whatever is on disk now, not what was written an hour ago.
 *
 * <p>Both commands take the window rather than working it out. The month is obvious until the year
 * somebody runs it on the second of January.
 */
final class ExportCommands {

    private ExportCommands() {}

    @Command(
            name = "export",
            description = "Files this office sends somebody else.",
            subcommands = {Return.class, Verify.class})
    static final class Export {}

    @Command(name = "federal", description = "Write the monthly return, to a file or to the screen.")
    static final class Return extends Action {

        @Option(names = "--from", required = true)
        private LocalDate start;

        @Option(names = "--to", required = true)
        private LocalDate end;

        @Option(names = "--to-file", paramLabel = "PATH")
        private Optional<String> path = Optional.empty();

        @Override
        public Integer call() {
            Optional<Path> file = path.map(given -> fileToWrite("--to-file", given));
            Federal.Return made;
            int replaced;
            try (UnitOfWork work = work()) {
                made = Federal.build(work.entityManager(), start, end);
                replaced = Federal.supersededCount(work.entityManager(), start, end);
            }
            if (file.isEmpty()) {
                echo(made.lines());
                return Main.DONE;
            }
            try {
                Files.writeString(file.get(), made.render(), StandardCharsets.US_ASCII);
            } catch (IOException trouble) {
                throw new UncheckedIOException(trouble);
            }
            say("wrote", path.get(), made.count(), made.fees());
            if (replaced > 0) {
                say("for the covering letter", replaced, "since superseded");
            }
            return Main.DONE;
        }
    }

    @Command(name = "verify", description = "Read a written return back and find what the federal office would find.")
    static final class Verify extends Action {

        @Parameters(paramLabel = "PATH")
        private String path;

        @Override
        public Integer call() {
            List<String> lines;
            try {
                lines = Written.lines(Files.readString(existingFile("PATH", path), StandardCharsets.US_ASCII));
            } catch (IOException trouble) {
                throw new UncheckedIOException(trouble);
            }
            List<Federal.Problem> found = Federal.verify(lines);
            if (found.isEmpty()) {
                Federal.Period window = Federal.windowOf(lines);
                say("good", path, lines.size(), window.start() + " to " + window.end());
                return Main.DONE;
            }
            found.forEach(problem -> echo(problem.toString()));
            complain(found.size() + " problems, the file will be rejected");
            return Main.REFUSED;
        }
    }
}
