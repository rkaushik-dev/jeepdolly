package io.github.rkaushikdev.jeepdolly.cli;

import static io.github.rkaushikdev.jeepdolly.support.Consoles.call;
import static io.github.rkaushikdev.jeepdolly.support.Consoles.shown;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.jeepdolly.Unreadable;
import io.github.rkaushikdev.jeepdolly.Written;
import io.github.rkaushikdev.jeepdolly.exports.Federal;
import io.github.rkaushikdev.jeepdolly.permits.Application;
import io.github.rkaushikdev.jeepdolly.permits.Permit;
import io.github.rkaushikdev.jeepdolly.permits.PermitNumber;
import io.github.rkaushikdev.jeepdolly.persistence.Database;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.support.Applications;
import io.github.rkaushikdev.jeepdolly.support.Consoles;
import io.github.rkaushikdev.jeepdolly.support.Consoles.Answer;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import io.github.rkaushikdev.jeepdolly.support.Permits;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Writing the federal return, and reading it back the way it will be read. */
class CliExportsTest {

    private static final String APRIL = "2025-04-01";
    private static final String LAST = "2025-04-30";

    @TempDir
    Path tmp;

    private Database database;
    private Console console;

    @BeforeEach
    void aConsoleOverASeededDatabase() {
        database = Databases.seeded();
        console = Consoles.over(database);
    }

    /** The excavator move, applied for and issued. */
    private void issued() {
        try (UnitOfWork work = database.unitOfWork()) {
            Application application = Applications.anApplication();
            work.applications().add(application);
            work.commit();
            work.permits().add(Permits.aPermit(application));
            work.commit();
        }
    }

    private Path written() {
        Path path = tmp.resolve("ooretn.txt");
        call(console, "export", "federal", "--from", APRIL, "--to", LAST, "--to-file", path.toString());
        return path;
    }

    private static List<String> linesOf(Path path) throws IOException {
        return Written.lines(Files.readString(path, StandardCharsets.US_ASCII));
    }

    private static void write(Path path, String... records) throws IOException {
        Files.writeString(path, String.join("\n", records) + "\n", StandardCharsets.US_ASCII);
    }

    @Test
    void theReturnPrintsToTheScreenWhenNoFileIsNamed() {
        issued();
        Answer answer = call(console, "export", "federal", "--from", APRIL, "--to", LAST);
        assertThat(answer.exitCode()).isZero();
        List<String> printed = shown(answer);
        assertThat(printed).hasSize(3);
        assertThat(printed.get(0)).startsWith("H23");
        assertThat(printed.get(printed.size() - 1)).startsWith("T");
    }

    @Test
    void everyPrintedRecordIsEightyColumns() {
        issued();
        Answer answer = call(console, "export", "federal", "--from", APRIL, "--to", LAST);
        assertThat(shown(answer)).allSatisfy(one -> assertThat(one).hasSize(Federal.WIDTH));
    }

    @Test
    void aQuietMonthStillPrintsAHeaderAndATrailer() {
        Answer answer = call(console, "export", "federal", "--from", APRIL, "--to", LAST);
        assertThat(shown(answer)).hasSize(2);
    }

    @Test
    void theReturnCanBeWrittenToAFile() throws IOException {
        issued();
        Path path = tmp.resolve("ooretn.txt");
        Answer answer = call(console, "export", "federal", "--from", APRIL, "--to", LAST, "--to-file", path.toString());
        assertThat(answer.exitCode()).isZero();
        assertThat(answer.output().strip()).startsWith("wrote");
        assertThat(linesOf(path)).hasSize(3);
    }

    @Test
    void theWrittenFileUsesUnixLineEndings() throws IOException {
        issued();
        Path path = written();
        assertThat(new String(Files.readAllBytes(path), StandardCharsets.US_ASCII))
                .doesNotContain("\r\n");
    }

    @Test
    void aFreshlyWrittenFileVerifies() {
        issued();
        Path path = written();
        Answer answer = call(console, "export", "verify", path.toString());
        assertThat(answer.exitCode()).isZero();
        assertThat(answer.output().strip()).startsWith("good");
    }

    @Test
    void verifyingNamesTheWindowOffTheHeader() {
        issued();
        Path path = written();
        assertThat(call(console, "export", "verify", path.toString()).output()).contains("2025-04-01 to 2025-04-30");
    }

    @Test
    void aRecordSomebodyShortenedByHandIsFound() throws IOException {
        issued();
        Path path = written();
        List<String> body = linesOf(path);
        write(path, body.get(0), body.get(1).stripTrailing(), body.get(2));
        Answer answer = call(console, "export", "verify", path.toString());
        assertThat(answer.exitCode()).isEqualTo(1);
        assertThat(answer.output()).contains("columns and not 80");
    }

    @Test
    void aDeletedDetailRecordIsFoundByTheTrailer() throws IOException {
        issued();
        Path path = written();
        List<String> body = linesOf(path);
        write(path, body.get(0), body.get(2));
        Answer answer = call(console, "export", "verify", path.toString());
        assertThat(answer.exitCode()).isEqualTo(1);
        assertThat(answer.output()).contains("says 1 details and the file has 0");
    }

    @Test
    void aFileWithNoHeaderIsFound() throws IOException {
        Path path = tmp.resolve("ooretn.txt");
        write(path, "D" + " ".repeat(79));
        Answer answer = call(console, "export", "verify", path.toString());
        assertThat(answer.exitCode()).isEqualTo(1);
        assertThat(answer.output()).contains("is not a header");
    }

    @Test
    void anEmptyFileIsFound() throws IOException {
        Path path = tmp.resolve("ooretn.txt");
        Files.writeString(path, "", StandardCharsets.US_ASCII);
        Answer answer = call(console, "export", "verify", path.toString());
        assertThat(answer.exitCode()).isEqualTo(1);
        assertThat(answer.output()).contains("the file is empty");
    }

    @Test
    void aFileNobodyWroteIsAUsageError() {
        assertThat(call(console, "export", "verify", tmp.resolve("nothing.txt").toString())
                        .exitCode())
                .isEqualTo(2);
    }

    @Test
    void theCoveringLetterCountPrintsWhenSomethingWasReplaced() {
        try (UnitOfWork work = database.unitOfWork()) {
            Application application = Applications.anApplication();
            work.applications().add(application);
            work.commit();
            Permit permit = Permits.aPermit(application);
            work.permits().add(permit);
            work.commit();
            permit.supersede(LocalDate.of(2025, 4, 9), PermitNumber.parse("25-R-000001"));
            work.permits().saveState(permit);
            work.commit();
        }
        Path path = tmp.resolve("ooretn.txt");
        Answer answer = call(console, "export", "federal", "--from", APRIL, "--to", LAST, "--to-file", path.toString());
        assertThat(answer.output()).contains("since superseded");
    }

    @Test
    void theVerifierReadsTheFileRatherThanTheDatabase() throws IOException {
        issued();
        Path path = written();
        List<String> body = linesOf(path);
        String broken = body.get(2).substring(0, 8) + "99999999" + body.get(2).substring(16);
        write(path, body.get(0), body.get(1), broken);
        Answer answer = call(console, "export", "verify", path.toString());
        assertThat(answer.exitCode()).isEqualTo(1);
        assertThat(answer.output()).contains("in fees and the details add to");
    }

    /** The checks, without a file or a console in the way. */
    @Nested
    class TheVerifierOnItsOwn {

        private Federal.Return made() {
            try (UnitOfWork work = database.unitOfWork()) {
                return Federal.build(work.entityManager(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 30));
            }
        }

        @Test
        void aGoodFileHasNoProblems() {
            assertThat(Federal.verify(made().lines())).isEmpty();
        }

        @Test
        void aWindowCanBeReadOffAGoodHeader() {
            assertThat(Federal.windowOf(made().lines()))
                    .isEqualTo(new Federal.Period(LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 30)));
        }

        @Test
        void aWindowCannotBeReadOffAFileWithNoHeader() {
            assertThatThrownBy(() -> Federal.windowOf(List.of("D" + " ".repeat(79))))
                    .isInstanceOf(Unreadable.class);
        }

        @Test
        void aRecordThatIsNotADetailInTheBodyIsFound() {
            List<String> body = new ArrayList<>(made().lines());
            body.add(1, "X" + " ".repeat(79));
            List<Federal.Problem> found = Federal.verify(body);
            assertThat(found).extracting(Federal.Problem::record).containsExactly(2);
            assertThat(found.get(0).toString()).contains("is not a detail record");
        }
    }
}
