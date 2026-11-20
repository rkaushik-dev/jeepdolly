package io.github.rkaushikdev.jeepdolly.permits;

import static io.github.rkaushikdev.jeepdolly.permits.Printing.WIDTH;
import static io.github.rkaushikdev.jeepdolly.support.Applications.anApplication;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.conditions.Condition;
import io.github.rkaushikdev.jeepdolly.conditions.ConditionLibrary;
import io.github.rkaushikdev.jeepdolly.support.Permits;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The printed permit, which lives on a dashboard for a week. */
class PrintingTest {

    private static final ConditionLibrary LIBRARY = ConditionLibrary.standard();
    private static final LocalDate START = LocalDate.of(2025, 4, 8);

    private static Permit permit() {
        return Permits.aPermit(anApplication());
    }

    private static Permit permit(List<Condition> conditions) {
        return Permits.permit(anApplication()).conditions(conditions).build();
    }

    @Test
    void theSheetIsEightyColumnsAndNeverMore() {
        assertThat(Printing.widest(permit())).isLessThanOrEqualTo(WIDTH);
        assertThat(Printing.printed(permit()))
                .allSatisfy(one -> assertThat(one.length()).isLessThanOrEqualTo(WIDTH));
    }

    @Test
    void theRulesAreTheFullWidth() {
        List<String> lines = Printing.printed(permit());
        assertThat(lines.get(0)).isEqualTo("=".repeat(WIDTH));
        assertThat(lines.get(lines.size() - 1)).isEqualTo("=".repeat(WIDTH));
    }

    @Test
    void theFirstSixLinesAreWhatAnOfficerLooksFor() {
        List<String> lines = Printing.printed(permit());
        assertThat(lines.get(1)).contains("MOTOR CARRIER PERMIT OFFICE");
        assertThat(lines.get(2)).contains("OVERSIZE AND OVERWEIGHT PERMIT");
        assertThat(lines.get(4)).startsWith("PERMIT 25-P-004471");
        assertThat(lines.get(4)).endsWith("ISSUED 2025-04-07");
        assertThat(lines.get(5)).startsWith("Bickford Heavy Haul (1204471)");
        assertThat(lines.get(5)).contains("VALID 2025-04-08 TO 2025-04-10");
    }

    @Test
    void theSizeAndTheWeightAreOnTheFace() {
        String written = Printing.sheet(permit());
        assertThat(written).contains("GROSS 94,000 lb");
        assertThat(written).contains("SIZE  12 ft wide, 13 ft 6 in high, 82 ft long");
        assertThat(written).contains("DISTANCE 35.20 mi");
    }

    @Test
    void theRouteIsPrintedAsTheDriverReadsIt() {
        String written = Printing.sheet(permit());
        assertThat(written).contains("ROUTE");
        assertThat(written).contains("from Waterville, US 201 to Skowhegan");
    }

    @Test
    void theConditionsAreNumberedSoAnOfficerCanNameOne() {
        List<String> numbered = Printing.printed(permit()).stream()
                .filter(one -> one.strip().startsWith("1.") || one.strip().startsWith("2."))
                .toList();
        assertThat(numbered).hasSize(2);
        assertThat(numbered.get(0).strip()).startsWith("1. Route as written");
    }

    @Test
    void aLongConditionWrapsAndStaysUnderTheMargin() {
        Condition longOne = LIBRARY.render("S-01", Facts.of("structure", "Kennebec River bridge at Skowhegan"));
        List<String> lines = Printing.printed(permit(List.of(LIBRARY.render("R-01"), longOne)));
        assertThat(lines).allSatisfy(one -> assertThat(one.length()).isLessThanOrEqualTo(WIDTH));
    }

    @Test
    void theFeesAddUpOnThePaper() {
        String written = Printing.sheet(permit());
        assertThat(written).contains("ordinary permit");
        assertThat(written).contains("TOTAL");
        assertThat(written).contains("$46.12");
    }

    @Test
    void aVoidPermitSaysSoAcrossTheBottom() {
        Permit one = permit();
        one.makeVoid(START, "load cancelled");
        assertThat(Printing.sheet(one)).contains("*** VOID ***");
    }

    @Test
    void aLivePermitSaysNothingAcrossTheBottom() {
        assertThat(Printing.sheet(permit())).doesNotContain("***");
    }

    @Test
    void aPermitWithNoConditionsCannotBePrinted() {
        assertThatThrownBy(() -> Printing.printed(permit(List.of()))).isInstanceOf(Impossible.class);
    }

    @Test
    void theHelpersDoWhatTheLayoutNeeds() {
        assertThat(Printing.centred("middle").length()).isLessThan(WIDTH);
        assertThat(Printing.twoUp("left", "right")).endsWith("right");
        assertThat(Printing.twoUp("left", "right")).hasSize(WIDTH);
        assertThat(Printing.wrapped("one two three", 76)).containsExactly("one", "two", "three");
    }

    @Test
    void theSheetEndsWithANewlineSoItCanBePiped() {
        assertThat(Printing.sheet(permit())).endsWith("\n");
    }
}
