package io.github.rkaushikdev.jeepdolly.limits;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.NotOnFile;
import io.github.rkaushikdev.jeepdolly.domain.Envelope;
import io.github.rkaushikdev.jeepdolly.domain.HighwayClass;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The three boxes: legal, routine, and the one that needs a person. */
class SizeTest {

    private static final HighwayClass ARTERIAL = HighwayClass.ARTERIAL;
    private static final HighwayClass STATE_AID = HighwayClass.STATE_AID;

    private static Envelope load() {
        return load("12'", "13'6", "80'", "0");
    }

    private static Envelope load(String width) {
        return load(width, "13'6", "80'", "0");
    }

    private static Envelope load(String width, String height, String length, String rear) {
        return Envelope.read(width, height, length, "0", rear);
    }

    @Test
    void aLoadInsideTheLegalBoxIsNotThisOfficesBusiness() {
        SizeTable table = SizeBook.standard().on(ARTERIAL);
        assertThat(table.classify(load("8'", "13'6", "70'", "0"))).isSameAs(SizeClass.LEGAL);
        assertThat(table.overLegal(load("8'", "13'6", "70'", "0")).any()).isFalse();
    }

    @Test
    void aWideExcavatorIsAnOrdinaryPermitOverTheCounter() {
        SizeTable table = SizeBook.standard().on(ARTERIAL);
        assertThat(table.classify(load())).isSameAs(SizeClass.ROUTINE);
        assertThat(table.overLegal(load()).over()).containsExactly("width", "length");
        assertThat(table.reasonsForReview(load())).isEmpty();
    }

    @Test
    void aLoadPastTheRoutineBoxGoesToAPerson() {
        SizeTable table = SizeBook.standard().on(ARTERIAL);
        Envelope wide = load("18'");
        assertThat(table.classify(wide)).isSameAs(SizeClass.REVIEW);
        assertThat(table.reasonsForReview(wide)).containsExactly("width");
        assertThat(table.overRoutine(wide).width()).isEqualTo(new Inches(24));
    }

    @Test
    void theReviewReasonsNameEveryMeasureThatPutItThere() {
        SizeTable table = SizeBook.standard().on(ARTERIAL);
        Envelope big = load("18'", "17'", "140'", "20'");
        assertThat(table.reasonsForReview(big)).containsExactly("width", "height", "length", "rear_overhang");
    }

    @Test
    void theSecondaryRoadsHoldATighterRoutineBox() {
        SizeBook book = SizeBook.standard();
        Envelope fifteen = load("15'");
        assertThat(book.on(ARTERIAL).classify(fifteen)).isSameAs(SizeClass.ROUTINE);
        assertThat(book.on(STATE_AID).classify(fifteen)).isSameAs(SizeClass.REVIEW);
    }

    @Test
    void aTripOverSeveralClassesIsHeldToTheTightestOfEach() {
        SizeTable table = SizeBook.standard().tightest(List.of(ARTERIAL, STATE_AID));
        assertThat(table.legal().length()).isEqualTo(Inches.of(68));
        assertThat(table.routine().width()).isEqualTo(Inches.of(14));
        assertThat(table.routine().height()).isEqualTo(Inches.of(14, 6));
        assertThat(table.classify(load("15'"))).isSameAs(SizeClass.REVIEW);
    }

    @Test
    void theTightestOfOneClassIsThatClass() {
        SizeTable table = SizeBook.standard().tightest(List.of(ARTERIAL));
        assertThat(table.routine().width()).isEqualTo(Inches.of(16));
    }

    @Test
    void aTripOverNoRoadsAtAllIsRefused() {
        assertThatThrownBy(() -> SizeBook.standard().tightest(List.of())).isInstanceOf(Impossible.class);
    }

    @Test
    void askingForAClassWithNoTableSaysWhichOne() {
        NotOnFile refusal = catchThrowableOfType(NotOnFile.class, () -> new SizeBook().on(ARTERIAL));
        assertThat(refusal.facts().get("highway_class")).isEqualTo("arterial");
    }

    @Test
    void aRoutineBoxTighterThanTheLegalOneIsATypingMistake() {
        Envelope legal = Envelope.read("8'6", "13'6", "75'", "3'", "4'");
        assertThatThrownBy(() -> new SizeTable(ARTERIAL, legal, Envelope.read("8'", "16'", "120'", "10'", "15'")))
                .isInstanceOf(Impossible.class);
        assertThatThrownBy(() -> new SizeTable(ARTERIAL, legal, Envelope.read("16'", "13'", "120'", "10'", "15'")))
                .isInstanceOf(Impossible.class);
        assertThatThrownBy(() -> new SizeTable(ARTERIAL, legal, Envelope.read("16'", "16'", "70'", "10'", "15'")))
                .isInstanceOf(Impossible.class);
    }

    @Test
    void theBookCoversEveryClassOfRoad() {
        SizeBook book = SizeBook.standard();
        assertThat(book.size()).isEqualTo(4);
        assertThat(book.on(HighwayClass.TOWN_WAY)).hasToString("town way size table");
        assertThat(SizeClass.REVIEW).hasToString("review");
    }

    @Test
    void aLoadExactlyOnTheRoutineEdgeIsStillRoutine() {
        SizeTable table = SizeBook.standard().on(ARTERIAL);
        assertThat(table.classify(load("16'"))).isSameAs(SizeClass.ROUTINE);
        assertThat(table.classify(load("16'1"))).isSameAs(SizeClass.REVIEW);
    }
}
