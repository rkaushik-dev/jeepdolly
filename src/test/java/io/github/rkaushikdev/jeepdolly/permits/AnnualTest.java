package io.github.rkaushikdev.jeepdolly.permits;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.jeepdolly.Dollars;
import io.github.rkaushikdev.jeepdolly.NotAllowed;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.conditions.ConditionLibrary;
import io.github.rkaushikdev.jeepdolly.domain.CommodityBook;
import io.github.rkaushikdev.jeepdolly.domain.Envelope;
import io.github.rkaushikdev.jeepdolly.fees.FeeSchedule;
import java.time.LocalDate;
import java.util.List;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

/** The annual permit, its envelope, and what it does not excuse. */
class AnnualTest {

    private static final CommodityBook BOOK = CommodityBook.standard();
    private static final ConditionLibrary LIBRARY = ConditionLibrary.standard();
    private static final FeeSchedule SCHEDULE = FeeSchedule.standard();
    private static final LocalDate NOVEMBER = LocalDate.of(2025, 11, 3);

    private static Envelope load() {
        return load("11'");
    }

    private static Envelope load(String width) {
        return Envelope.read(width, "13'6", "90'", "0", "6'");
    }

    private static Permit write() {
        return write(load(), 110000);
    }

    private static Permit write(Envelope envelope, int gross) {
        return Annual.issue(
                new Numbering(),
                LIBRARY,
                SCHEDULE,
                "25-F-000001",
                1204471,
                "Bickford Heavy Haul",
                envelope,
                new Pounds(gross),
                BOOK.get("CRNE"),
                NOVEMBER);
    }

    @Test
    void aCraneInsideTheEnvelopeIsEligible() {
        Eligibility found = Annual.eligible(load(), new Pounds(110000), BOOK.get("CRNE"));
        assertThat(found.allowed()).isTrue();
        assertThat(found).hasToString("eligible for an annual permit");
    }

    @Test
    void aLoadPastTheEnvelopeIsNotAndSaysWhichMeasure() {
        Eligibility found = Annual.eligible(load("14'"), new Pounds(110000), BOOK.get("CRNE"));
        assertThat(found.allowed()).isFalse();
        assertThat(found.reasons()).containsExactly("width is past the annual envelope");
    }

    @Test
    void aLoadOverTheCapIsNotEligible() {
        Eligibility found = Annual.eligible(load(), new Pounds(130000), BOOK.get("CRNE"));
        assertThat(found.reasons().get(0)).contains("over the annual cap of 120,000 lb");
        assertThat(Annual.GROSS.value()).isEqualTo(120000);
    }

    @Test
    void aDivisibleLoadIsNotEligibleAndNeitherIsAnAllowanceLoad() {
        assertThat(Annual.eligible(load(), new Pounds(90000), BOOK.get("GRVL")).allowed())
                .isFalse();
        Eligibility wood = Annual.eligible(load(), new Pounds(90000), BOOK.get("WOOD"));
        assertThat(wood.allowed()).isFalse();
        assertThat(wood.reasons().get(0)).contains("moves under an allowance");
    }

    @Test
    void anAnnualRunsAYearFromTheDayItWasIssued() {
        Permit permit = write();
        assertThat(permit.start()).isEqualTo(NOVEMBER);
        assertThat(permit.end()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(permit.days()).isEqualTo(365);
    }

    @Test
    void anAnnualHasNoRouteAndSaysWhatItCovers() {
        Permit permit = write();
        assertThat(permit.route()).containsExactly(Annual.EVERYWHERE);
        assertThat(permit.origin()).isEqualTo(Annual.EVERYWHERE);
        assertThat(permit.narrative()).endsWith("except as posted or closed");
    }

    @Test
    void anAnnualTakesANumberOutOfItsOwnSeries() {
        assertThat(write().number().toString()).startsWith("25-A-");
    }

    @Test
    void anAnnualIsOneLineAndOneNumber() {
        Permit permit = write();
        assertThat(permit.quote().lines()).hasSize(1);
        assertThat(permit.quote().total()).isEqualTo(Dollars.of(500));
        assertThat(Annual.price(SCHEDULE).lines().get(0).description()).isEqualTo("annual permit");
    }

    @Test
    void anAnnualCarriesTheConditionsThatSayWhatItDoesNotExcuse() {
        List<String> codes = write().conditionCodes();
        assertThat(codes).contains("R-01");
        assertThat(codes).contains("T-02");
        assertThat(codes).contains("G-03");
        assertThat(codes).doesNotContain("E-01");
    }

    @Test
    void theDatesArePrintedOnTheFirstCondition() {
        Permit permit = write();
        assertThat(permit.conditions().get(0).code()).isEqualTo("T-01");
        assertThat(permit.conditions().get(0).text()).contains("2025-11-03 to 2026-11-02");
    }

    @Test
    void writingAnAnnualForSomethingTooBigIsRefusedWithTheReasons() {
        NotAllowed refusal = catchThrowableOfType(NotAllowed.class, () -> write(load("16'"), 130000));
        assertThat(refusal.facts().get("reasons"))
                .asInstanceOf(InstanceOfAssertFactories.LIST)
                .hasSize(2);
    }

    @Test
    void theEnvelopeOnThePermitIsTheOneThatWasAskedFor() {
        Permit permit = write(load("12'"), 110000);
        assertThat(permit.envelope().width().value()).isEqualTo(144);
        assertThat(permit.gross()).isEqualTo(new Pounds(110000));
    }
}
