package io.github.rkaushikdev.jeepdolly.routing;

import static io.github.rkaushikdev.jeepdolly.support.RoadLog.roadLog;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.Miles;
import io.github.rkaushikdev.jeepdolly.NotOnFile;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.SeasonBook;
import io.github.rkaushikdev.jeepdolly.domain.Envelope;
import io.github.rkaushikdev.jeepdolly.domain.HighwayClass;
import io.github.rkaushikdev.jeepdolly.domain.Restriction;
import io.github.rkaushikdev.jeepdolly.domain.RestrictionBook;
import io.github.rkaushikdev.jeepdolly.domain.RestrictionKind;
import io.github.rkaushikdev.jeepdolly.limits.StatutoryBook;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Finding a route for a load, and finding none when there is none. */
class SelectTest {

    private static final LocalDate MARCH = LocalDate.of(2025, 3, 3);

    private static Search search() {
        return search("13'6", "82'", Select.PENALTY);
    }

    private static Search search(String height, String length, Map<HighwayClass, Integer> penalties) {
        return new Search(Envelope.read("12'", height, length), new Pounds(94000), MARCH, Optional.empty(), penalties);
    }

    private static Search height(String height) {
        return search(height, "82'", Select.PENALTY);
    }

    private static Search length(String length) {
        return search("13'6", length, Select.PENALTY);
    }

    private static Search penalties(Map<HighwayClass, Integer> penalties) {
        return search("13'6", "82'", penalties);
    }

    private static Optional<Itinerary> route(
            String origin, String destination, RestrictionBook restrictions, Search load, Optional<TurnBook> turns) {
        return Select.find(
                roadLog(), origin, destination, load, restrictions, new SeasonBook(), StatutoryBook.standard(), turns);
    }

    private static Optional<Itinerary> route() {
        return route("waterville", "pittsfield", new RestrictionBook(), search(), Optional.empty());
    }

    private static Optional<Itinerary> route(RestrictionBook restrictions) {
        return route("waterville", "pittsfield", restrictions, search(), Optional.empty());
    }

    private static RestrictionBook lowBridge() {
        RestrictionBook book = new RestrictionBook();
        book.add(Restriction.builder("R-1", "I95-2", RestrictionKind.CLEARANCE, "overpass")
                .height(Inches.parse("13'"))
                .build());
        return book;
    }

    private static RestrictionBook closed(String ident, String segment, String note) {
        RestrictionBook book = new RestrictionBook();
        book.add(new Restriction(ident, segment, RestrictionKind.CLOSURE, note));
        return book;
    }

    @Test
    void theRouterTakesTheInterstateWhenNothingIsInTheWay() {
        Itinerary found = route().orElseThrow();
        assertThat(found.idents()).containsExactly("I95-1", "I95-2");
        assertThat(found.distance()).isEqualTo(Miles.of(18, 80));
    }

    @Test
    void aClearanceOnTheInterstatePushesTheRouteOntoTheStateRoads() {
        Itinerary found = route(lowBridge()).orElseThrow();
        assertThat(found.idents()).containsExactly("I95-1", "US201-2", "SA148-2", "SR43-1");
        assertThat(found.distance()).isEqualTo(Miles.of(34, 70));
    }

    @Test
    void aLoadThatFitsUnderTheBridgeIsLeftOnTheInterstate() {
        Itinerary found = route("waterville", "pittsfield", lowBridge(), height("12'"), Optional.empty())
                .orElseThrow();
        assertThat(found.idents()).containsExactly("I95-1", "I95-2");
    }

    @Test
    void theRouterWillNotRouteOverATownWayAtAll() {
        Itinerary found = route("madison", "anson", new RestrictionBook(), search(), Optional.empty())
                .orElseThrow();
        assertThat(found.idents()).containsExactly("SR8-1", "US2-1", "US201-3");
    }

    @Test
    void aPlaceWithNothingButATownWayIntoItCanBeUnreachable() {
        RestrictionBook blocked = closed("R-2", "US201-3", "bridge out");
        assertThat(route("skowhegan", "anson", blocked, search(), Optional.empty()))
                .isEmpty();
    }

    @Test
    void askingForARouteToWhereYouAlreadyAreGetsNothing() {
        assertThat(route("skowhegan", "skowhegan", new RestrictionBook(), search(), Optional.empty()))
                .isEmpty();
    }

    @Test
    void aPlaceThatIsNotOnTheLogIsRefusedBeforeTheSearchStarts() {
        assertThatThrownBy(() -> route("waterville", "bangor", new RestrictionBook(), search(), Optional.empty()))
                .isInstanceOf(NotOnFile.class);
    }

    @Test
    void thePenaltiesAreDataAndZeroingThemTakesTheShortWay() {
        Map<HighwayClass, Integer> nothing = new EnumMap<>(HighwayClass.class);
        for (HighwayClass one : HighwayClass.values()) {
            nothing.put(one, 0);
        }
        Itinerary found = route("waterville", "pittsfield", lowBridge(), penalties(nothing), Optional.empty())
                .orElseThrow();
        assertThat(found.distance()).isEqualTo(Miles.of(34, 70));
    }

    @Test
    void aHeavyPenaltyOnTheInterstateKeepsTheLoadOffItEntirely() {
        Map<HighwayClass, Integer> dear = new EnumMap<>(Select.PENALTY);
        dear.put(HighwayClass.INTERSTATE, 20);
        Itinerary found = route("waterville", "pittsfield", new RestrictionBook(), penalties(dear), Optional.empty())
                .orElseThrow();
        assertThat(found.idents()).containsExactly("US201-1", "US201-2", "SA148-2", "SR43-1");
        assertThat(found.distance()).isEqualTo(Miles.of(35, 20));
    }

    @Test
    void theSameQuestionGivesTheSameAnswerTwice() {
        Itinerary first = route(lowBridge()).orElseThrow();
        Itinerary second = route(lowBridge()).orElseThrow();
        assertThat(first.idents()).isEqualTo(second.idents());
    }

    @Test
    void everywhereThisLoadCanGetToComesBackInOneList() {
        List<String> places = Select.reachable(
                roadLog(), "skowhegan", search(), new RestrictionBook(), new SeasonBook(), StatutoryBook.standard());
        assertThat(places).contains("anson", "madison", "newport", "waterville");
    }

    @Test
    void aClosureCutsOffEverythingBehindIt() {
        RestrictionBook blocked = closed("R-3", "US2-2", "bridge out");
        List<String> places =
                Select.reachable(roadLog(), "newport", search(), blocked, new SeasonBook(), StatutoryBook.standard());
        assertThat(places).containsExactly("newport");
    }

    @Test
    void theCardIndexLeavesARouteAloneWhenNothingHasTroubleOnIt() {
        Itinerary found = route(
                        "waterville", "pittsfield", new RestrictionBook(), search(), Optional.of(TurnBook.coded()))
                .orElseThrow();
        assertThat(found.idents()).containsExactly("I95-1", "I95-2");
    }

    @Test
    void aTightCornerSendsALongLoadRoundTheBackRatherThanRefusingIt() {
        Itinerary without = route("waterville", "madison", new RestrictionBook(), search(), Optional.empty())
                .orElseThrow();
        assertThat(without.idents()).containsExactly("I95-1", "US201-2", "US2-1", "SR8-1");
        Itinerary found = route("waterville", "madison", new RestrictionBook(), search(), Optional.of(TurnBook.coded()))
                .orElseThrow();
        assertThat(found.idents()).containsExactly("I95-1", "I95-2", "SR43-1", "SA148-1", "SR8-1");
    }

    @Test
    void aLoadInsideThePlainLengthStillGetsRoundThatCorner() {
        Itinerary found = route(
                        "waterville", "madison", new RestrictionBook(), length("70'"), Optional.of(TurnBook.coded()))
                .orElseThrow();
        assertThat(found.idents()).containsExactly("I95-1", "US201-2", "US2-1", "SR8-1");
    }

    @Test
    void theWayRoundCostsMoreAndTheRouterPaysIt() {
        Itinerary shortWay = route(
                        "waterville", "madison", new RestrictionBook(), length("70'"), Optional.of(TurnBook.coded()))
                .orElseThrow();
        Itinerary longWay = route(
                        "waterville", "madison", new RestrictionBook(), search(), Optional.of(TurnBook.coded()))
                .orElseThrow();
        assertThat(longWay.distance()).isGreaterThan(shortWay.distance());
    }

    @Test
    void theCornerIsReadOffTheRoadTheLoadArrivedOn() {
        // Into Norridgewock on US 2 is the square and it is too tight. Into Norridgewock on SA 148
        // is the other end of the village and it is not.
        TurnBook book = TurnBook.coded();
        Itinerary through = Select.find(
                        roadLog(),
                        "canaan",
                        "madison",
                        search(),
                        new RestrictionBook(),
                        new SeasonBook(),
                        StatutoryBook.standard(),
                        book)
                .orElseThrow();
        assertThat(through.idents()).containsExactly("SA148-1", "SR8-1");
    }

    @Test
    void aClosureAndACornerTogetherCanCutAPlaceOffEntirely() {
        RestrictionBook blocked = closed("R-4", "SA148-1", "culvert out");
        assertThat(route("waterville", "madison", blocked, search(), Optional.of(TurnBook.coded())))
                .isEmpty();
        assertThat(route("waterville", "madison", blocked, search(), Optional.empty()))
                .isPresent();
    }

    @Test
    void theReachQuestionReadsTheCornersTheSameWay() {
        RestrictionBook blocked = closed("R-5", "SA148-1", "culvert out");
        List<String> places = Select.reachable(
                roadLog(),
                "waterville",
                search(),
                blocked,
                new SeasonBook(),
                StatutoryBook.standard(),
                TurnBook.coded());
        assertThat(places).doesNotContain("madison");
        assertThat(places).contains("norridgewock", "anson");
    }

    @Test
    void withoutTheCardIndexTheReachQuestionAnswersAsItAlwaysDid() {
        RestrictionBook blocked = closed("R-6", "SA148-1", "culvert out");
        List<String> places = Select.reachable(
                roadLog(), "waterville", search(), blocked, new SeasonBook(), StatutoryBook.standard());
        assertThat(places).contains("madison");
    }

    @Test
    void theSameQuestionGivesTheSameAnswerTwiceWithCornersToo() {
        Itinerary first = route("waterville", "pittsfield", lowBridge(), search(), Optional.of(TurnBook.coded()))
                .orElseThrow();
        Itinerary second = route("waterville", "pittsfield", lowBridge(), search(), Optional.of(TurnBook.coded()))
                .orElseThrow();
        assertThat(first.idents()).isEqualTo(second.idents());
    }
}
