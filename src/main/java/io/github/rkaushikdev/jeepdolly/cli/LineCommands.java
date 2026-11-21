package io.github.rkaushikdev.jeepdolly.cli;

import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.domain.Envelope;
import io.github.rkaushikdev.jeepdolly.persistence.Books;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.persistence.models.LineCrossingRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.LineOwnerRow;
import io.github.rkaushikdev.jeepdolly.reports.LineSurvey;
import io.github.rkaushikdev.jeepdolly.routing.Itinerary;
import io.github.rkaushikdev.jeepdolly.utilities.BlockedLine;
import io.github.rkaushikdev.jeepdolly.utilities.Crossing;
import io.github.rkaushikdev.jeepdolly.utilities.Lift;
import io.github.rkaushikdev.jeepdolly.utilities.LineOwner;
import io.github.rkaushikdev.jeepdolly.utilities.LineReading;
import io.github.rkaushikdev.jeepdolly.utilities.Lines;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Line commands, which are the survey van's list and the counter's question.
 *
 * <p>The van gets a printed list every May. It has one column of idents, one of roads, and one of
 * heights, and it goes on a clipboard on the dash. That is this, and it is the reason the report
 * exists at all.
 *
 * <p>The clearance question is here as well as on the interface for the ordinary reason: the
 * traffic office at six in the morning has a telephone and a terminal and no web server yet.
 *
 * <p>Remeasuring from the command line is how the van's results go back in, thirty at a time, from
 * a text file the crew chief types up. There is no bulk load command because there has never been
 * more than thirty, and a command that read a file would want a file format nobody has agreed.
 */
final class LineCommands {

    private static final String LIFT_HELP = "Leave it off for a wire nothing can lift.";

    private LineCommands() {}

    @Command(
            name = "wires",
            description = "Overhead lines: the survey, the owners, and what is in the way.",
            subcommands = {
                Listing.class,
                Owners.class,
                AddOwner.class,
                Add.class,
                Measure.class,
                Survey.class,
                Clearance.class
            })
    static final class Wires {}

    @Command(name = "list", description = "Every wire on file, or every wire over one road.")
    static final class Listing extends Action {

        @Option(names = "--segment", description = "One road rather than all of them.")
        private Optional<String> segment = Optional.empty();

        @Option(names = "--under", description = "Only wires below this height.")
        private Optional<String> ceiling = Optional.empty();

        @Override
        public Integer call() {
            try (UnitOfWork work = work()) {
                List<LineCrossingRow> rows;
                if (ceiling.isPresent()) {
                    rows = work.lines().under(Inches.parse(ceiling.get()));
                } else {
                    rows = segment.map(work.lines()::crossings).orElseGet(work.lines()::crossings);
                }
                for (LineCrossingRow row : rows) {
                    String lift = row.getLiftTo().map(Inches::toString).orElse("fixed");
                    String when = row.getMeasured().map(LocalDate::toString).orElse("never");
                    say(row.getIdent(), row.getSegmentIdent(), row.getPlace(), row.getHeight(), lift, when);
                }
            }
            return Main.DONE;
        }
    }

    @Command(name = "owners", description = "Every line owner, with how many wires and how much notice.")
    static final class Owners extends Action {

        @Override
        public Integer call() {
            try (UnitOfWork work = work()) {
                for (LineOwnerRow row : work.lines().owners()) {
                    say(
                            row.getIdent(),
                            row.getName(),
                            row.getTelephone(),
                            row.getNoticeDays() + " days",
                            row.getWires().size());
                }
            }
            return Main.DONE;
        }
    }

    @Command(name = "owner", description = "Put a line owner on file.")
    static final class AddOwner extends Action {

        @Parameters(index = "0", paramLabel = "IDENT")
        private String ident;

        @Parameters(index = "1", paramLabel = "NAME")
        private String name;

        @Option(names = "--telephone", defaultValue = "")
        private String telephone;

        @Option(names = "--notice", defaultValue = "5")
        private int notice;

        @Override
        public Integer call() {
            try (UnitOfWork work = work()) {
                work.lines().addOwner(new LineOwner(ident, name, telephone, notice));
                work.commit();
            }
            say("filed", ident, name);
            return Main.DONE;
        }
    }

    @Command(name = "add", description = "Put a measured wire on file.")
    static final class Add extends Action {

        @Parameters(index = "0", paramLabel = "IDENT")
        private String ident;

        @Parameters(index = "1", paramLabel = "SEGMENT")
        private String segment;

        @Parameters(index = "2", paramLabel = "OWNER")
        private String owner;

        @Option(names = "--place", required = true)
        private String place;

        @Option(names = "--height", required = true)
        private String height;

        @Option(names = "--lift-to", description = LIFT_HELP)
        private Optional<String> liftTo = Optional.empty();

        @Option(names = "--measured")
        private Optional<LocalDate> measured = Optional.empty();

        @Override
        public Integer call() {
            Inches hangs = Inches.parse(height);
            Crossing crossing = liftTo.map(Inches::parse)
                    .map(lift -> new Crossing(ident, segment, place, hangs, owner, lift))
                    .orElseGet(() -> new Crossing(ident, segment, place, hangs, owner));
            try (UnitOfWork work = work()) {
                work.lines().add(crossing, measured.orElse(null));
                work.commit();
            }
            say("filed", ident, segment, crossing.height());
            return Main.DONE;
        }
    }

    @Command(name = "measure", description = "Write what the crew found, replacing what the card said.")
    static final class Measure extends Action {

        @Parameters(index = "0", paramLabel = "IDENT")
        private String ident;

        @Parameters(index = "1", paramLabel = "HEIGHT")
        private String height;

        @Option(names = "--lift-to", description = LIFT_HELP)
        private Optional<String> liftTo = Optional.empty();

        @Option(names = "--on")
        private Optional<LocalDate> on = Optional.empty();

        @Override
        public Integer call() {
            LocalDate day = on.orElseGet(LocalDate::now);
            LineCrossingRow row;
            try (UnitOfWork work = work()) {
                row = work.lines()
                        .remeasure(
                                ident,
                                Inches.parse(height),
                                day,
                                liftTo.map(Inches::parse).orElse(null));
                work.commit();
            }
            say("measured", ident, row.getHeight(), day);
            return Main.DONE;
        }
    }

    @Command(name = "survey", description = "The van's list: low wires nobody has walked inside the cycle.")
    static final class Survey extends Action {

        @Option(names = "--day")
        private Optional<LocalDate> day = Optional.empty();

        @Option(names = "--under", description = "What counts as a low wire.")
        private Optional<String> ceiling = Optional.empty();

        @Override
        public Integer call() {
            LocalDate when = day.orElseGet(LocalDate::now);
            Inches height =
                    ceiling.filter(given -> !given.isEmpty()).map(Inches::parse).orElse(LineSurvey.LOW);
            try (UnitOfWork work = work()) {
                echo(LineSurvey.report(work.entityManager(), when, height));
            }
            return Main.DONE;
        }
    }

    @Command(name = "clearance", description = "What a load of this height meets on this written route.")
    static final class Clearance extends Action {

        @Parameters(index = "0", paramLabel = "ORIGIN")
        private String origin;

        @Parameters(index = "1..*", arity = "1..*", paramLabel = "SEGMENTS")
        private List<String> segments;

        @Option(names = "--height", required = true)
        private String height;

        @Option(names = "--travel", required = true)
        private LocalDate travel;

        @Option(names = "--filed")
        private Optional<LocalDate> filed = Optional.empty();

        @Override
        public Integer call() {
            Envelope envelope = Envelope.read("8'6", height, "70'");
            LocalDate asked = filed.orElseGet(LocalDate::now);
            LineReading found;
            try (UnitOfWork work = work()) {
                Itinerary itinerary = new Itinerary(Books.networkFrom(work.entityManager()), origin, segments);
                found = Lines.under(itinerary, envelope, Books.linesFrom(work.entityManager()), asked, travel);
            }
            for (Lift lift : found.lifts()) {
                say(
                        "lift",
                        lift.crossing().ident(),
                        lift.owner().name(),
                        lift.owner().telephone(),
                        lift.noticeBy());
            }
            for (BlockedLine blocked : found.blocked()) {
                say("blocked", blocked.crossing().ident(), blocked.toString());
            }
            if (found.clear() && found.lifts().isEmpty()) {
                say("clear", envelope.height());
            }
            return Main.DONE;
        }
    }
}
