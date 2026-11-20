package io.github.rkaushikdev.jeepdolly.conditions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.jeepdolly.Conflict;
import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.NotOnFile;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The condition library, its codes, and the slots that must be filled. */
class ConditionLibraryTest {

    private static final ConditionLibrary LIBRARY = ConditionLibrary.standard();

    @Test
    void theLibraryHoldsTheOfficeWordingUnderStableCodes() {
        assertThat(LIBRARY.size()).isEqualTo(35);
        assertThat(LIBRARY.contains("T-02")).isTrue();
        assertThat(LIBRARY.get("T-02").category()).isSameAs(Category.TRAVEL);
        assertThat(LIBRARY.get("T-02")).hasToString("T-02 (travel)");
    }

    @Test
    void aConditionWithNoSlotsRendersAsWritten() {
        Condition rendered = LIBRARY.render("R-01");
        assertThat(rendered.text()).isEqualTo("Route as written. Any deviation voids this permit.");
        assertThat(rendered).hasToString(rendered.text());
        assertThat(rendered.category()).isSameAs(Category.ROUTE);
    }

    @Test
    void aConditionWithSlotsIsFilledFromTheFactsGiven() {
        Condition rendered = LIBRARY.render("S-01", Map.of("structure", "Kennebec River bridge"));
        assertThat(rendered.text()).isEqualTo("Cross Kennebec River bridge alone with no other vehicle on it.");
    }

    @Test
    void aMissingFactIsARefusalAndNotAGapOnThePermit() {
        Impossible refusal = catchThrowableOfType(Impossible.class, () -> LIBRARY.render("S-01"));
        assertThat(refusal.facts()).isEqualTo(Map.of("code", "S-01", "wants", "structure"));
    }

    @Test
    void anUnknownCodeSaysWhichCode() {
        NotOnFile refusal = catchThrowableOfType(NotOnFile.class, () -> LIBRARY.render("Z-99"));
        assertThat(refusal.facts().get("code")).isEqualTo("Z-99");
    }

    @Test
    void aCategoryComesBackInTheOrderItPrints() {
        List<ConditionText> travel = LIBRARY.ofCategory(Category.TRAVEL);
        assertThat(travel)
                .extracting(ConditionText::code)
                .containsExactly("T-01", "T-02", "T-03", "T-04", "T-05", "T-06", "T-07");
        assertThat(travel.get(0).order()).isEqualTo(10);
    }

    @Test
    void theCategoriesPrintInTheOrderThePermitFaceUses() {
        List<Condition> rendered =
                List.of(LIBRARY.render("G-01"), LIBRARY.render("E-04"), LIBRARY.render("T-02"), LIBRARY.render("M-01"));
        assertThat(ConditionLibrary.sortConditions(rendered))
                .extracting(Condition::code)
                .containsExactly("T-02", "E-04", "M-01", "G-01");
    }

    @Test
    void twoConditionsOfOneCategorySortByTheOrderOnTheRecord() {
        List<Condition> rendered = List.of(LIBRARY.render("T-03"), LIBRARY.render("T-02"));
        assertThat(ConditionLibrary.sortConditions(rendered))
                .extracting(Condition::code)
                .containsExactly("T-02", "T-03");
    }

    @Test
    void aConditionWithNoCodeOrNoWordsIsRefused() {
        assertThatThrownBy(() -> new ConditionText("  ", Category.GENERAL, "something"))
                .isInstanceOf(Impossible.class);
        assertThatThrownBy(() -> new ConditionText("X-01", Category.GENERAL, "   "))
                .isInstanceOf(Impossible.class);
    }

    @Test
    void theLibraryRefusesTheSameCodeTwice() {
        ConditionLibrary library = new ConditionLibrary();
        library.add(new ConditionText("X-01", Category.GENERAL, "something"));
        assertThatThrownBy(() -> library.add(new ConditionText("X-01", Category.GENERAL, "something else")))
                .isInstanceOf(Conflict.class);
    }

    @Test
    void theGeneralConditionsAreTheOnesOnEveryPermit() {
        assertThat(LIBRARY.ofCategory(Category.GENERAL))
                .extracting(ConditionText::code)
                .containsExactly("G-01", "G-02", "G-03");
        assertThat(LIBRARY.get("G-01").text()).contains("produce it on demand");
    }

    @Test
    void theCodesComeBackSortedForThePrintedAppendix() {
        List<String> codes = LIBRARY.codes();
        assertThat(codes.get(0)).isEqualTo("E-01");
        assertThat(codes.get(codes.size() - 1)).isEqualTo("U-02");
    }
}
