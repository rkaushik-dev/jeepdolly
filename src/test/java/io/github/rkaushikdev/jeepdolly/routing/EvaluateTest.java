package io.github.rkaushikdev.jeepdolly.routing;

import static io.github.rkaushikdev.jeepdolly.support.RoadLog.roadLog;
import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.THURSDAY;
import static java.time.DayOfWeek.TUESDAY;
import static java.time.DayOfWeek.WEDNESDAY;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.SeasonBook;
import io.github.rkaushikdev.jeepdolly.SeasonKind;
import io.github.rkaushikdev.jeepdolly.SeasonOrder;
import io.github.rkaushikdev.jeepdolly.domain.Curfew;
import io.github.rkaushikdev.jeepdolly.domain.Envelope;
import io.github.rkaushikdev.jeepdolly.domain.Restriction;
import io.github.rkaushikdev.jeepdolly.domain.RestrictionBook;
import io.github.rkaushikdev.jeepdolly.domain.RestrictionKind;
import io.github.rkaushikdev.jeepdolly.limits.StatutoryBook;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Walking a route with a load on a day, and what comes back in the way. */
class EvaluateTest {

    private static final LocalDate MARCH = LocalDate.of(2025, 3, 3);
    private static final LocalDate APRIL = LocalDate.of(2025, 4, 8);
    private static final List<String> ACROSS = List.of("US201-1", "US201-2", "US2-1", "SA148-1", "SR43-1");
    private static final Envelope LOAD = Envelope.read("12'", "13'9", "82'");
    private static final Pounds GROSS = new Pounds(94000);

    private static Itinerary trip() {
        return trip("waterville", ACROSS);
    }

    private static Itinerary trip(String origin, List<String> idents) {
        return new Itinerary(roadLog(), origin, idents);
    }

    private static SeasonBook thawBook() {
        SeasonBook book = new SeasonBook();
        book.file(new SeasonOrder(
                "25-04", "district 4", SeasonKind.THAW, LocalDate.of(2025, 3, 20), LocalDate.of(2025, 5, 2), 65));
        return book;
    }

    /** The arguments every reading in this file is taken with, overridden one at a time. */
    private static final class Run {
        private RestrictionBook restrictions = new RestrictionBook();
        private SeasonBook seasons = new SeasonBook();
        private Envelope envelope = LOAD;
        private Pounds gross = GROSS;
        private LocalDate day = MARCH;
        private Optional<LocalTime> departure = Optional.empty();
        private Optional<TurnBook> turns = Optional.empty();

        Run restrictions(RestrictionBook value) {
            restrictions = value;
            return this;
        }

        Run seasons(SeasonBook value) {
            seasons = value;
            return this;
        }

        Run envelope(Envelope value) {
            envelope = value;
            return this;
        }

        Run gross(Pounds value) {
            gross = value;
            return this;
        }

        Run day(LocalDate value) {
            day = value;
            return this;
        }

        Run departure(LocalTime value) {
            departure = Optional.of(value);
            return this;
        }

        Run turns(TurnBook value) {
            turns = Optional.of(value);
            return this;
        }

        RouteReading reading() {
            return Evaluate.evaluate(
                    trip(), envelope, gross, day, restrictions, seasons, StatutoryBook.standard(), departure, turns);
        }
    }

    private static Run run() {
        return new Run();
    }

    private static RouteReading run(RestrictionBook book) {
        return run().restrictions(book).reading();
    }

    private static Restriction clearance(String ident, String segment, String note, Inches height) {
        return Restriction.builder(ident, segment, RestrictionKind.CLEARANCE, note)
                .height(height)
                .build();
    }

    private static Restriction springPosting() {
        return Restriction.builder("R-5", "SA148-1", RestrictionKind.SEASONAL, "spring posting")
                .region("district 4")
                .build();
    }

    private static Restriction rushHour() {
        return Restriction.builder("R-6", "US201-1", RestrictionKind.CURFEW, "Waterville rush hour")
                .curfew(new Curfew(
                        Set.of(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY), LocalTime.of(6, 30), LocalTime.of(8, 30)))
                .build();
    }

    @Test
    void aRouteWithNothingOnItComesBackClear() {
        RouteReading reading = run().reading();
        assertThat(reading.clear()).isTrue();
        assertThat(reading.obstructions()).isEmpty();
        assertThat(reading).hasToString("route is clear");
    }

    @Test
    void aLoadThatFitsUnderAClearanceIsNotAnObstruction() {
        RestrictionBook book = new RestrictionBook();
        book.add(clearance("R-1", "US2-1", "railroad bridge", Inches.of(14)));
        assertThat(run(book).clear()).isTrue();
    }

    @Test
    void aLoadThatDoesNotFitStopsTheRouteAndNamesBothNumbers() {
        RestrictionBook book = new RestrictionBook();
        book.add(clearance("R-1", "US2-1", "railroad bridge", Inches.of(13, 6)));
        RouteReading reading = run(book);
        assertThat(reading.clear()).isFalse();
        assertThat(reading.segmentsInTheWay()).containsExactly("US2-1");
        Obstruction trouble = reading.fatal().get(0);
        assertThat(trouble.needed()).isEqualTo("13 ft 9 in");
        assertThat(trouble.allowed()).isEqualTo("13 ft 6 in");
        assertThat(trouble.toString()).contains("no permit");
    }

    @Test
    void aClosureStopsEverythingWhateverTheLoadMeasures() {
        RestrictionBook book = new RestrictionBook();
        book.add(new Restriction("R-2", "SA148-1", RestrictionKind.CLOSURE, "culvert work"));
        RouteReading reading = run().restrictions(book)
                .envelope(Envelope.read("8'", "10'", "40'"))
                .gross(new Pounds(30000))
                .reading();
        assertThat(reading.clear()).isFalse();
        assertThat(reading.fatal().get(0).kind()).isEqualTo("closure");
        assertThat(reading).hasToString("route is blocked at SA148-1");
    }

    @Test
    void aNarrowStructureIsReadAgainstTheWidthAndNothingElse() {
        RestrictionBook book = new RestrictionBook();
        book.add(Restriction.builder("R-3", "SR43-1", RestrictionKind.WIDTH, "stone arch")
                .width(Inches.of(11))
                .build());
        assertThat(run(book).clear()).isFalse();
        assertThat(run().restrictions(book)
                        .envelope(Envelope.read("10'", "13'", "60'"))
                        .reading()
                        .clear())
                .isTrue();
    }

    @Test
    void aPostedStructureIsReadAgainstTheGross() {
        RestrictionBook book = new RestrictionBook();
        book.add(Restriction.builder("R-4", "SA148-1", RestrictionKind.WEIGHT, "Carrabassett bridge")
                .weight(new Pounds(60000))
                .build());
        RouteReading reading = run(book);
        assertThat(reading.clear()).isFalse();
        assertThat(reading.fatal().get(0).kind()).isEqualTo("posting");
        assertThat(run().restrictions(book).gross(new Pounds(58000)).reading().clear())
                .isTrue();
    }

    @Test
    void aTownWayInTheMiddleOfATripIsFatalOnItsOwn() {
        RouteReading reading = Evaluate.evaluate(
                trip("skowhegan", List.of("US201-3", "TR104-1", "SR8-1")),
                LOAD,
                GROSS,
                MARCH,
                new RestrictionBook(),
                new SeasonBook(),
                StatutoryBook.standard());
        assertThat(reading.clear()).isFalse();
        assertThat(reading.fatal().get(0).kind()).isEqualTo("jurisdiction");
        assertThat(reading.fatal().get(0).segment()).isEqualTo("TR104-1");
    }

    @Test
    void aSeasonalPostingSaysNothingWhileNoOrderIsRunning() {
        RestrictionBook book = new RestrictionBook();
        book.add(springPosting());
        assertThat(run().restrictions(book)
                        .seasons(thawBook())
                        .day(MARCH)
                        .reading()
                        .clear())
                .isTrue();
        assertThat(run().restrictions(book)
                        .seasons(thawBook())
                        .day(MARCH)
                        .reading()
                        .conditions())
                .isEmpty();
    }

    @Test
    void aSeasonalPostingUnderAThawOrderIsAConditionAndNotARefusal() {
        RestrictionBook book = new RestrictionBook();
        book.add(springPosting());
        RouteReading reading =
                run().restrictions(book).seasons(thawBook()).day(APRIL).reading();
        assertThat(reading.clear()).isTrue();
        assertThat(reading.conditions()).hasSize(1);
        Obstruction trouble = reading.conditions().get(0);
        assertThat(trouble.kind()).isEqualTo("posted season");
        assertThat(trouble.allowed()).isEqualTo("61,100 lb");
        assertThat(trouble.note()).contains("order 25-04");
        assertThat(reading).hasToString("route is clear with 1 conditions");
    }

    @Test
    void aLoadUnderTheThawedLimitPassesThePostingWithoutAWord() {
        RestrictionBook book = new RestrictionBook();
        book.add(springPosting());
        assertThat(run().restrictions(book)
                        .seasons(thawBook())
                        .day(APRIL)
                        .gross(new Pounds(60000))
                        .reading()
                        .conditions())
                .isEmpty();
    }

    @Test
    void aCurfewIsAConditionWhenNoDepartureTimeWasGiven() {
        RestrictionBook book = new RestrictionBook();
        book.add(rushHour());
        RouteReading reading = run(book);
        assertThat(reading.clear()).isTrue();
        assertThat(reading.conditions().get(0).kind()).isEqualTo("curfew");
    }

    @Test
    void aDepartureOutsideTheCurfewClearsItAndInsideItDoesNot() {
        RestrictionBook book = new RestrictionBook();
        book.add(rushHour());
        assertThat(run().restrictions(book)
                        .departure(LocalTime.of(10, 0))
                        .reading()
                        .conditions())
                .isEmpty();
        assertThat(run().restrictions(book)
                        .departure(LocalTime.of(7, 0))
                        .reading()
                        .conditions())
                .hasSize(1);
    }

    @Test
    void everyLegIsWalkedSoTwoTroublesBothComeBack() {
        RestrictionBook book = new RestrictionBook();
        book.add(clearance("R-1", "US2-1", "railroad bridge", Inches.of(13, 6)));
        book.add(new Restriction("R-2", "SR43-1", RestrictionKind.CLOSURE, "washout"));
        assertThat(run(book).segmentsInTheWay()).containsExactly("SR43-1", "US2-1");
    }

    @Test
    void anOfficeWithNoCardIndexReadsNoCorners() {
        assertThat(run().envelope(Envelope.read("12'", "13'9", "150'"))
                        .reading()
                        .clear())
                .isTrue();
    }

    @Test
    void aLoadThatWillNotGetRoundTheSquareIsRefusedThere() {
        Envelope longOne = Envelope.read("12'", "13'9", "150'");
        RouteReading reading = run().envelope(longOne).turns(TurnBook.coded()).reading();
        List<Obstruction> corners = reading.obstructions().stream()
                .filter(one -> one.kind().equals("corner"))
                .toList();
        assertThat(corners).extracting(Obstruction::segment).containsExactly("US2-1", "SA148-1", "SR43-1");
        assertThat(corners).allMatch(Obstruction::fatal);
        assertThat(reading.clear()).isFalse();
    }

    @Test
    void theCornerSaysWhatItHasAndWhatTheLoadWanted() {
        Envelope longOne = Envelope.read("12'", "13'9", "150'");
        Obstruction first =
                Evaluate.cornerObstructions(trip(), longOne, TurnBook.coded()).get(0);
        assertThat(first.needed()).isEqualTo("115 ft radius");
        assertThat(first.allowed()).isEqualTo("65 ft radius");
        assertThat(first.note()).contains("island in the middle");
    }

    @Test
    void aCornerWithNothingWrittenAgainstItNamesThePlace() {
        Envelope load = Envelope.read("12'", "13'9", "120'");
        List<Obstruction> found =
                Evaluate.cornerObstructions(trip("fairfield", List.of("US201-2", "US201-3")), load, TurnBook.coded());
        assertThat(found.get(0).note()).isEqualTo("the turn at skowhegan");
    }

    @Test
    void theOrdinaryPermitLoadGetsRoundEveryCornerOnTheRoute() {
        assertThat(run().turns(TurnBook.coded()).reading().clear()).isTrue();
    }

    @Test
    void theCodedCardIndexIsTheOfficeListAndNotEmpty() {
        TurnBook book = TurnBook.coded();
        assertThat(book.size()).isEqualTo(10);
        assertThat(book.tight()).extracting(Corner::node).containsExactly("madison", "norridgewock");
    }

    @Test
    void aCornerTheLoadOnlyJustGetsRoundIsAConditionNotARefusal() {
        Envelope near = Envelope.read("12'", "13'9", "95'");
        RouteReading reading = run().envelope(near).turns(TurnBook.coded()).reading();
        List<Obstruction> corners = reading.obstructions().stream()
                .filter(one -> one.kind().equals("tight corner"))
                .toList();
        assertThat(corners).extracting(Obstruction::place).containsExactly("skowhegan");
        assertThat(corners).noneMatch(Obstruction::fatal);
        assertThat(reading.clear()).isTrue();
        assertThat(reading).hasToString("route is clear with 1 conditions");
    }

    @Test
    void theSameCornerIsNeverBothARefusalAndACondition() {
        Envelope longOne = Envelope.read("12'", "13'9", "150'");
        RouteReading reading = run().envelope(longOne).turns(TurnBook.coded()).reading();
        Set<String> refused = reading.obstructions().stream()
                .filter(one -> one.kind().equals("corner"))
                .map(Obstruction::place)
                .collect(Collectors.toSet());
        Set<String> noted = reading.obstructions().stream()
                .filter(one -> one.kind().equals("tight corner"))
                .map(Obstruction::place)
                .collect(Collectors.toSet());
        assertThat(refused).isNotEmpty();
        Set<String> both = new HashSet<>(refused);
        both.retainAll(noted);
        assertThat(both).isEmpty();
    }

    @Test
    void anObstructionThatIsNotACornerNamesNoPlace() {
        RestrictionBook book = new RestrictionBook();
        book.add(new Restriction("R-9", "SA148-1", RestrictionKind.CLOSURE, "culvert work"));
        assertThat(run(book).obstructions())
                .allSatisfy(one -> assertThat(one.place()).isEmpty());
    }
}
