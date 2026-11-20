package io.github.rkaushikdev.jeepdolly.structures;

import static io.github.rkaushikdev.jeepdolly.support.RoadLog.roadLog;
import static io.github.rkaushikdev.jeepdolly.support.Streams.make;
import static io.github.rkaushikdev.jeepdolly.support.Streams.tractorLowboy;
import static io.github.rkaushikdev.jeepdolly.support.Structures.structureBook;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.jeepdolly.domain.AxleStream;
import io.github.rkaushikdev.jeepdolly.routing.Itinerary;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Every structure on a route against one load, in driven order. */
class AnalysisTest {

    private static final StructureBook BOOK = structureBook();
    private static final List<String> ACROSS = List.of("US201-1", "US201-2", "US2-1", "SA148-1", "SR43-1");

    private static Itinerary trip() {
        return trip(ACROSS);
    }

    private static Itinerary trip(List<String> idents) {
        return new Itinerary(roadLog(), "waterville", idents);
    }

    private static AxleStream light() {
        return make(List.of(12000, 14000, 14000), List.of(200, 54));
    }

    private static AxleStream middling() {
        return make(List.of(12000, 19000, 19000, 25000, 25000, 25000), List.of(200, 54, 180, 52, 52));
    }

    @Test
    void aLightLoadClearsEveryStructureOnTheRoute() {
        StructureReading reading = Analysis.analyseRoute(BOOK, trip(), light());
        assertThat(reading.clear()).isTrue();
        assertThat(reading.size()).isEqualTo(3);
        assertThat(reading.conditions()).isEmpty();
        assertThat(reading).hasToString("3 structures clear");
    }

    @Test
    void theFindingsComeBackInTheOrderTheyAreDriven() {
        StructureReading reading = Analysis.analyseRoute(BOOK, trip(), light());
        assertThat(reading.findings())
                .extracting(one -> one.structure().ident())
                .containsExactly("1247", "1533", "3310");
    }

    @Test
    void aStructureThatCarriesNoTrafficIsSkippedAndNotRated() {
        StructureReading reading = Analysis.analyseRoute(BOOK, trip(List.of("US201-1", "US201-2", "US2-1")), light());
        assertThat(reading.findings())
                .extracting(one -> one.structure().ident())
                .containsExactly("1247");
    }

    @Test
    void onePostedStructureRefusesTheWholeRoute() {
        StructureReading reading = Analysis.analyseRoute(BOOK, trip(), tractorLowboy());
        assertThat(reading.clear()).isFalse();
        assertThat(reading.structuresInTheWay()).containsExactly("1533");
        assertThat(reading).hasToString("refused at 1533");
    }

    @Test
    void theOthersAreStillRatedSoTheLetterCanSayEverything() {
        StructureReading reading = Analysis.analyseRoute(BOOK, trip(), tractorLowboy());
        StructureFinding kennebec = reading.findings().get(0);
        assertThat(kennebec.verdict()).isSameAs(Verdict.CLEARS);
        assertThat(kennebec.rating().factor()).isEqualTo(140);
        assertThat(kennebec).hasToString("Kennebec River: 94,000 lb of it, 140%");
    }

    @Test
    void aShortSpanIsRatedOnTheWindowAndNotTheScaleTicket() {
        StructureReading reading = Analysis.analyseRoute(BOOK, trip(), tractorLowboy());
        StructureFinding culvert = reading.findings().get(2);
        assertThat(culvert.window().weight().value()).isEqualTo(48000);
        assertThat(culvert.rating().factor()).isEqualTo(187);
    }

    @Test
    void aMiddlingLoadCrossesUnderConditionsAndSaysWhich() {
        StructureReading reading = Analysis.analyseRoute(BOOK, trip(List.of("US201-1", "US201-2")), middling());
        assertThat(reading.clear()).isTrue();
        assertThat(reading.conditional()).hasSize(1);
        List<String> conditions = reading.conditions();
        assertThat(conditions).hasSize(3);
        assertThat(conditions).allSatisfy(one -> assertThat(one).contains("Kennebec River"));
        assertThat(reading).hasToString("1 structures crossed under conditions");
    }

    @Test
    void theWorstStructureIsTheOneWithTheLowestFactor() {
        StructureReading reading = Analysis.analyseRoute(BOOK, trip(), light());
        StructureFinding worst = reading.worst().orElseThrow();
        assertThat(worst.structure().ident()).isEqualTo("1533");
    }

    @Test
    void aPostedRefusalIsNotReadAsTheLowestFactor() {
        StructureReading reading = Analysis.analyseRoute(BOOK, trip(), tractorLowboy());
        StructureFinding worst = reading.worst().orElseThrow();
        assertThat(worst.structure().ident()).isEqualTo("1247");
    }

    @Test
    void aRouteWithNoStructuresOnItSaysSo() {
        StructureReading reading = Analysis.analyseRoute(BOOK, trip(List.of("US201-1")), tractorLowboy());
        assertThat(reading.size()).isEqualTo(0);
        assertThat(reading.clear()).isTrue();
        assertThat(reading.worst()).isEmpty();
        assertThat(reading).hasToString("no structures on the route");
    }

    @Test
    void aBareListOfStructuresCanBeRatedWithoutARoute() {
        StructureReading reading = Analysis.analyse(List.of(BOOK.get("1247"), BOOK.get("3310")), tractorLowboy());
        assertThat(reading.size()).isEqualTo(2);
        assertThat(reading.clear()).isTrue();
    }
}
