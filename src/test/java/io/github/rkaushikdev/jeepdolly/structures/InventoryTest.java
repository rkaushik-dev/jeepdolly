package io.github.rkaushikdev.jeepdolly.structures;

import static io.github.rkaushikdev.jeepdolly.support.Structures.structureBook;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.jeepdolly.Conflict;
import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.NotOnFile;
import io.github.rkaushikdev.jeepdolly.Pounds;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The structure inventory, and what a record has to carry to be one. */
class InventoryTest {

    private static final StructureKind BRIDGE = StructureKind.BRIDGE;

    /** A two span bridge with both ratings, which each test then changes one thing about. */
    private static Structure.Builder bridge(String ident) {
        return Structure.builder(ident, "Test Stream", "US2-1", BRIDGE)
                .spansFeet(List.of(60, 60))
                .built(1958)
                .operatingRating(new Pounds(96000))
                .inventoryRating(new Pounds(64000));
    }

    private static Structure.Builder bridge() {
        return bridge("9001");
    }

    @Test
    void theBookHoldsTheStructuresOfTheNetwork() {
        StructureBook book = structureBook();
        assertThat(book.size()).isEqualTo(6);
        assertThat(book.contains("1247")).isTrue();
        assertThat(book.get("1247").name()).isEqualTo("Kennebec River");
        assertThat(book.get("1247").kind()).isSameAs(BRIDGE);
    }

    @Test
    void aStructurePrintsWithItsPostingWhenItHasOne() {
        StructureBook book = structureBook();
        assertThat(book.get("1533")).hasToString("1533 Carrabassett Stream on SA148-1, posted 48,000 lb");
        assertThat(book.get("1247")).hasToString("1247 Kennebec River on US201-2");
    }

    @Test
    void theSpansGiveTheLengthAndTheLongestOfThem() {
        Structure kennebec = structureBook().get("1247");
        assertThat(kennebec.spanCount()).isEqualTo(3);
        assertThat(kennebec.longestSpan()).isEqualTo(120);
        assertThat(kennebec.lengthFeet()).isEqualTo(360);
    }

    @Test
    void aStructureThatCarriesTrafficNeedsSpansAndAnOperatingRating() {
        assertThatThrownBy(() -> Structure.builder("9002", "No Spans", "US2-1", BRIDGE)
                        .operatingRating(new Pounds(90000))
                        .build())
                .isInstanceOf(Impossible.class)
                .hasMessageContaining("has spans");
        assertThatThrownBy(() -> Structure.builder("9001", "Test Stream", "US2-1", BRIDGE)
                        .spansFeet(List.of(60, 60))
                        .built(1958)
                        .inventoryRating(new Pounds(64000))
                        .build())
                .isInstanceOf(Impossible.class);
        assertThatThrownBy(() -> bridge().spansFeet(List.of(60, 0)).build()).isInstanceOf(Impossible.class);
    }

    @Test
    void aStructureOverTheRoadNeedsAClearanceInstead() {
        assertThatThrownBy(() -> Structure.builder("9003", "Overpass", "US2-1", StructureKind.OVERPASS)
                        .build())
                .isInstanceOf(Impossible.class)
                .hasMessageContaining("has a clearance");
        Structure over = Structure.builder("9003", "Overpass", "US2-1", StructureKind.OVERPASS)
                .clearance(new Inches(170))
                .build();
        assertThat(over.kind().crossesOver()).isTrue();
        assertThat(over.kind().carriesTraffic()).isFalse();
    }

    @Test
    void aDailyRatingAboveThePermitRatingIsATypingMistake() {
        assertThatThrownBy(() -> bridge().inventoryRating(new Pounds(120000)).build())
                .isInstanceOf(Impossible.class)
                .hasMessageContaining("higher for daily traffic");
    }

    @Test
    void aStructureWithNoIdentIsNotAStructure() {
        assertThatThrownBy(() -> bridge("   ").build()).isInstanceOf(Impossible.class);
    }

    @Test
    void thePostedSignDecidesWhatMayGoOnAtAll() {
        StructureBook book = structureBook();
        Structure carrabassett = book.get("1533");
        assertThat(carrabassett.isPosted()).isTrue();
        assertThat(carrabassett.carries(new Pounds(48000))).isTrue();
        assertThat(carrabassett.carries(new Pounds(48001))).isFalse();
        assertThat(book.get("1247").carries(new Pounds(200000))).isTrue();
    }

    @Test
    void theStructuresOfASegmentComeBackTogether() {
        StructureBook book = structureBook();
        assertThat(book.onSegment("US201-2")).extracting(Structure::ident).containsExactly("1247");
        assertThat(book.onSegment("US201-1")).isEmpty();
        assertThat(book.onSegments(List.of("US201-2", "SA148-1", "SR43-1")))
                .extracting(Structure::ident)
                .containsExactly("1247", "1533", "3310");
    }

    @Test
    void thePostedStructuresAreTheListTheRouterCaresAbout() {
        assertThat(structureBook().posted()).extracting(Structure::ident).containsExactly("1533");
    }

    @Test
    void theOldOnesCanBeListedForTheInspectionProgramme() {
        List<Structure> old = structureBook().olderThan(1960);
        assertThat(old).extracting(Structure::ident).containsExactly("1247", "1533");
        assertThat(old.get(0).age(LocalDate.of(2025, 6, 1))).isEqualTo(88);
    }

    @Test
    void askingForAStructureThatIsNotOnFileSaysWhich() {
        NotOnFile refusal =
                catchThrowableOfType(NotOnFile.class, () -> structureBook().get("9999"));
        assertThat(refusal.facts().get("structure")).isEqualTo("9999");
    }

    @Test
    void theSameStructureMayNotBeFiledTwice() {
        StructureBook book = new StructureBook();
        book.add(bridge().build());
        assertThatThrownBy(() -> book.add(bridge().build())).isInstanceOf(Conflict.class);
    }
}
