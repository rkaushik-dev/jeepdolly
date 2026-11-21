package io.github.rkaushikdev.jeepdolly.cli;

import io.github.rkaushikdev.jeepdolly.Written;
import io.github.rkaushikdev.jeepdolly.domain.Envelope;
import io.github.rkaushikdev.jeepdolly.persistence.Books;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.persistence.models.SegmentRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.StructureRow;
import io.github.rkaushikdev.jeepdolly.reports.CornerReport;
import io.github.rkaushikdev.jeepdolly.routing.Corner;
import io.github.rkaushikdev.jeepdolly.routing.Itinerary;
import io.github.rkaushikdev.jeepdolly.routing.TurnBook;
import io.github.rkaushikdev.jeepdolly.services.RoadLogFile;
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
 * Road log commands, which is how the log actually gets loaded.
 *
 * <p>Twice a year a file arrives and somebody has to put it in. That is this, and it is a command
 * rather than a route because the file is forty thousand lines and nobody is uploading that through
 * a form.
 *
 * <p>The load prints what it did and what it could not read, and it commits only if it is not a dry
 * run. The dry run is what gets used first, every time, because the state's file has three bad lines
 * in it every time and the office wants to see them before the good lines go in.
 *
 * <p>The corner commands are here rather than under reports because they are about the road rather
 * than about the money, and because the counter looks for them where the segment lookup is.
 */
final class NetworkCommands {

    private NetworkCommands() {}

    @Command(
            name = "roads",
            description = "The road log: what is on it and how to put more on.",
            subcommands = {Export.class, Load.class, Segment.class, District.class, Corners.class, Turn.class})
    static final class Roads {}

    @Command(name = "export", description = "Write the whole log out, for a diff against what the state sent.")
    static final class Export extends Action {

        @Option(names = "--to", paramLabel = "PATH")
        private Optional<String> path = Optional.empty();

        @Override
        public Integer call() {
            Optional<Path> file = path.map(given -> fileToWrite("--to", given));
            List<String> lines;
            try (UnitOfWork work = work()) {
                lines = RoadLogFile.exportLog(work.entityManager());
            }
            if (file.isEmpty()) {
                echo(lines);
                return Main.DONE;
            }
            try {
                Files.writeString(file.get(), String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
            } catch (IOException trouble) {
                throw new UncheckedIOException(trouble);
            }
            say("wrote", lines.size(), path.get());
            return Main.DONE;
        }
    }

    @Command(name = "load", description = "Read a published log in. Run it dry first, every time.")
    static final class Load extends Action {

        @Parameters(paramLabel = "PATH")
        private String path;

        @Option(names = "--dry-run", description = "Read it and roll back.")
        private boolean dryRun;

        @Override
        public Integer call() {
            List<String> lines;
            try {
                lines = Written.lines(Files.readString(existingFile("PATH", path), StandardCharsets.UTF_8));
            } catch (IOException trouble) {
                throw new UncheckedIOException(trouble);
            }
            RoadLogFile.Loaded found;
            try (UnitOfWork work = work()) {
                found = RoadLogFile.loadLog(work.entityManager(), lines);
                if (dryRun) {
                    work.rollback();
                } else {
                    work.commit();
                }
            }
            echo(found.lines());
            if (!found.refused().isEmpty() && !dryRun) {
                complain(found.refused().size() + " lines were not read");
            }
            return Main.DONE;
        }
    }

    @Command(name = "segment", description = "One segment, with what is on it and what is wrong with it.")
    static final class Segment extends Action {

        @Parameters(paramLabel = "IDENT")
        private String ident;

        @Override
        public Integer call() {
            try (UnitOfWork work = work()) {
                SegmentRow row = work.network().segment(ident);
                say(row.getIdent(), row.toDomain().route(), row.getStartNode(), row.getEndNode(), row.getLength());
                for (StructureRow structure : work.network().structuresOn(ident)) {
                    say("  structure", structure.getIdent(), structure.getName(), structure.getPosted());
                }
            }
            return Main.DONE;
        }
    }

    @Command(name = "district", description = "Every segment in one district, which is what a thaw order reaches.")
    static final class District extends Action {

        @Parameters(paramLabel = "NUMBER")
        private int number;

        @Override
        public Integer call() {
            try (UnitOfWork work = work()) {
                for (SegmentRow row : work.network().inDistrict(number)) {
                    say(row.getIdent(), row.toDomain().route(), row.getTown(), row.getLength());
                }
            }
            return Main.DONE;
        }
    }

    @Command(name = "corners", description = "The list on the wall: the tight corners and what turned at each.")
    static final class Corners extends Action {

        @Option(names = "--from")
        private Optional<LocalDate> start = Optional.empty();

        @Option(names = "--to")
        private Optional<LocalDate> end = Optional.empty();

        @Option(names = "--under", description = "Radius in feet, tighter than the default.")
        private Optional<Integer> under = Optional.empty();

        @Override
        public Integer call() {
            // Both days, or the calendar year the office is in: one day on its own is not a span.
            LocalDate today = LocalDate.now();
            boolean span = start.isPresent() && end.isPresent();
            LocalDate first = span ? start.get() : LocalDate.of(today.getYear(), 1, 1);
            LocalDate last = span ? end.get() : LocalDate.of(today.getYear(), 12, 31);
            List<CornerReport.Wall> found;
            try (UnitOfWork work = work()) {
                TurnBook book = TurnBook.coded();
                found = under.map(feet -> CornerReport.wallList(work.entityManager(), book, first, last, feet))
                        .orElseGet(() -> CornerReport.wallList(work.entityManager(), book, first, last));
            }
            if (found.isEmpty()) {
                say("nothing on the card index is under that radius");
                return Main.DONE;
            }
            for (CornerReport.Wall one : found) {
                switch (one.corner()) {
                    case Corner(String at, String into, String outOf, int radius, String note) ->
                        say(at, into, outOf, radius, one.turned());
                }
            }
            return Main.DONE;
        }
    }

    @Command(name = "turn", description = "What one load meets on one written route, corner by corner.")
    static final class Turn extends Action {

        @Parameters(index = "0", paramLabel = "ORIGIN")
        private String origin;

        @Parameters(index = "1..*", arity = "1..*", paramLabel = "SEGMENTS")
        private List<String> segments;

        @Option(names = "--length", defaultValue = "80'", description = "Overall length of the combination.")
        private String length;

        @Option(names = "--rear", defaultValue = "0", description = "Rear overhang.")
        private String rear;

        @Override
        public Integer call() {
            Envelope envelope = Envelope.read("8'6", "13'6", length, "0", rear);
            Itinerary itinerary;
            try (UnitOfWork work = work()) {
                itinerary = new Itinerary(Books.networkFrom(work.entityManager()), origin, segments);
            }
            echo(CornerReport.routeReport(itinerary, envelope, TurnBook.coded()));
            return Main.DONE;
        }
    }
}
