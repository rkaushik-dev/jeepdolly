package io.github.rkaushikdev.jeepdolly.permits;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Miles;
import io.github.rkaushikdev.jeepdolly.NotAllowed;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.conditions.ConditionLibrary;
import io.github.rkaushikdev.jeepdolly.domain.Envelope;
import io.github.rkaushikdev.jeepdolly.fees.Quote;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The permit document, its states, and the copies it keeps of everything. */
class PermitTest {

    private static final ConditionLibrary LIBRARY = ConditionLibrary.standard();
    private static final LocalDate ISSUED = LocalDate.of(2025, 4, 7);
    private static final LocalDate START = LocalDate.of(2025, 4, 8);
    private static final LocalDate END = LocalDate.of(2025, 4, 10);

    private static Permit.Builder permit() {
        return Permit.builder()
                .number(new PermitNumber(2025, Series.SINGLE, 4471))
                .application("25-04471")
                .usdot(1204471)
                .carrierName("Bickford Heavy Haul")
                .issued(ISSUED)
                .start(START)
                .end(END)
                .origin("waterville")
                .destination("pittsfield")
                .route(List.of("US201-1", "US201-2", "SA148-2", "SR43-1"))
                .narrative("from Waterville, US 201 to Skowhegan, SA 148 to Canaan")
                .gross(new Pounds(94000))
                .envelope(Envelope.read("12'", "13'6", "82'"))
                .distance(Miles.of(35, 20))
                .conditions(List.of(LIBRARY.render("R-01"), LIBRARY.render("G-01")))
                .quote(new Quote(List.of(), "schedule of 2024-01-01"));
    }

    private static Permit aPermit() {
        return permit().build();
    }

    @Test
    void aNewPermitIsIssuedAndCarriesOneLineOfHistory() {
        Permit permit = aPermit();
        assertThat(permit.state()).isSameAs(PermitState.ISSUED);
        assertThat(permit.history()).containsExactly(new Permit.Entry(ISSUED, "issued to Bickford Heavy Haul"));
        assertThat(permit).hasToString("25-P-004471 Bickford Heavy Haul issued");
    }

    @Test
    void aPermitThatEndsBeforeItStartsIsRefused() {
        assertThatThrownBy(() -> permit().start(END).end(START).build()).isInstanceOf(Impossible.class);
    }

    @Test
    void aPermitValidBeforeItWasIssuedIsRefused() {
        assertThatThrownBy(() -> permit().start(LocalDate.of(2025, 4, 1)).build())
                .isInstanceOf(Impossible.class);
    }

    @Test
    void aPermitWithNoRouteOnItIsRefused() {
        assertThatThrownBy(() -> permit().route(List.of()).build()).isInstanceOf(Impossible.class);
    }

    @Test
    void theDaysAreCountedInclusiveOfBothEnds() {
        assertThat(aPermit().days()).isEqualTo(3);
        assertThat(permit().end(START).build().days()).isEqualTo(1);
    }

    @Test
    void aPermitIsGoodOnlyOnTheDaysItNames() {
        Permit permit = aPermit();
        assertThat(permit.validOn(LocalDate.of(2025, 4, 7))).isFalse();
        assertThat(permit.validOn(START)).isTrue();
        assertThat(permit.validOn(END)).isTrue();
        assertThat(permit.validOn(LocalDate.of(2025, 4, 11))).isFalse();
    }

    @Test
    void expiryIsAFactAboutTheCalendarAndNotAState() {
        Permit permit = aPermit();
        assertThat(permit.expiredOn(LocalDate.of(2025, 4, 11))).isTrue();
        assertThat(permit.state()).isSameAs(PermitState.ISSUED);
        assertThat(permit.statusOn(LocalDate.of(2025, 4, 11))).isEqualTo("expired");
        assertThat(permit.statusOn(ISSUED)).isEqualTo("issued, not yet in force");
        assertThat(permit.statusOn(START)).isEqualTo("in force");
    }

    @Test
    void voidingRecordsTheReasonAndKillsThePermit() {
        Permit permit = aPermit();
        permit.makeVoid(START, "load cancelled by the customer");
        assertThat(permit.state()).isSameAs(PermitState.VOID);
        assertThat(permit.validOn(START)).isFalse();
        assertThat(permit.statusOn(START)).isEqualTo("void: load cancelled by the customer");
        assertThat(permit.history().get(permit.history().size() - 1).note()).startsWith("void:");
    }

    @Test
    void voidingWithoutAReasonIsRefused() {
        assertThatThrownBy(() -> aPermit().makeVoid(START, "   ")).isInstanceOf(Impossible.class);
    }

    @Test
    void aVoidPermitCannotBeVoidedOrSupersededAgain() {
        Permit permit = aPermit();
        permit.makeVoid(START, "load cancelled");
        assertThatThrownBy(() -> permit.makeVoid(START, "again")).isInstanceOf(NotAllowed.class);
        assertThatThrownBy(() -> permit.supersede(START, new PermitNumber(2025, Series.REPLACEMENT, 1)))
                .isInstanceOf(NotAllowed.class);
    }

    @Test
    void anAmendmentSupersedesAndTheChainCanBeWalkedBothWays() {
        Permit first = aPermit();
        Permit second = permit().number(new PermitNumber(2025, Series.REPLACEMENT, 1))
                .supersedes(first.number())
                .build();
        first.supersede(START, second.number());
        assertThat(first.state()).isSameAs(PermitState.SUPERSEDED);
        assertThat(first.supersededBy()).contains(second.number());
        assertThat(second.supersedes()).contains(first.number());
        assertThat(first.statusOn(START)).isEqualTo("superseded by 25-R-000001");
    }

    @Test
    void theConditionsAreCopiedAndCanBeListedByCode() {
        Permit permit = aPermit();
        assertThat(permit.conditionCodes()).containsExactly("R-01", "G-01");
        assertThat(permit.conditions().get(0).text()).startsWith("Route as written");
    }

    @Test
    void thePrintedFaceIsWhatTheOfficerReadsFirst() {
        List<String> face = aPermit().face().lines().toList();
        assertThat(face.get(0)).isEqualTo("25-P-004471  Bickford Heavy Haul (1204471)");
        assertThat(face.get(1)).isEqualTo("Waterville to Pittsfield, 35.20 mi");
        assertThat(face.get(2)).isEqualTo("12 ft wide, 13 ft 6 in high, 82 ft long");
        assertThat(face.get(3)).isEqualTo("94,000 lb gross, 2025-04-08 to 2025-04-10");
    }
}
