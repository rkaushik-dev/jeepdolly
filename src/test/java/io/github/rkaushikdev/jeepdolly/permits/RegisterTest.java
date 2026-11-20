package io.github.rkaushikdev.jeepdolly.permits;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.jeepdolly.Conflict;
import io.github.rkaushikdev.jeepdolly.Miles;
import io.github.rkaushikdev.jeepdolly.NotOnFile;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.Unreadable;
import io.github.rkaushikdev.jeepdolly.conditions.ConditionLibrary;
import io.github.rkaushikdev.jeepdolly.domain.Envelope;
import io.github.rkaushikdev.jeepdolly.fees.Quote;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/** The permit register and the four questions the telephone asks it. */
class RegisterTest {

    private static final ConditionLibrary LIBRARY = ConditionLibrary.standard();
    private static final LocalDate ISSUED = LocalDate.of(2025, 4, 7);
    private static final LocalDate START = LocalDate.of(2025, 4, 8);
    private static final LocalDate END = LocalDate.of(2025, 4, 10);

    private static Permit.Builder permit(int sequence, int usdot, Series series) {
        return Permit.builder()
                .number(new PermitNumber(2025, series, sequence))
                .application(String.format(Locale.ROOT, "25-%05d", sequence))
                .usdot(usdot)
                .carrierName("Bickford Heavy Haul")
                .issued(ISSUED)
                .start(START)
                .end(END)
                .origin("waterville")
                .destination("pittsfield")
                .route(List.of("US201-1", "US201-2"))
                .narrative("from Waterville, US 201 to Skowhegan")
                .gross(new Pounds(94000))
                .envelope(Envelope.read("12'", "13'6", "82'"))
                .distance(Miles.of(17))
                .conditions(List.of(LIBRARY.render("R-01")))
                .quote(new Quote(List.of(), "schedule of 2024-01-01"));
    }

    private static Permit.Builder permit(int sequence) {
        return permit(sequence, 1204471, Series.SINGLE);
    }

    private static Permit aPermit() {
        return permit(4471).build();
    }

    private static PermitRegister filled() {
        PermitRegister register = new PermitRegister();
        register.file(aPermit());
        register.file(permit(4472).end(LocalDate.of(2025, 4, 20)).build());
        register.file(permit(4473, 2299001, Series.SINGLE)
                .issued(LocalDate.of(2025, 4, 1))
                .build());
        register.file(permit(1, 1204471, Series.SUPERLOAD).build());
        return register;
    }

    @Test
    void aPermitIsFoundByItsNumberHoweverItWasWrittenDown() {
        PermitRegister register = filled();
        assertThat(register.get("25-P-004471").application()).isEqualTo("25-04471");
        assertThat(register.get(new PermitNumber(2025, Series.SINGLE, 4471)).usdot())
                .isEqualTo(1204471);
        assertThat(register.has("25-P-004471")).isTrue();
        assertThat(register.contains("25-P-004471")).isTrue();
        assertThat(register.size()).isEqualTo(4);
    }

    @Test
    void aNumberThatCannotBeReadIsRefusedBeforeTheLookup() {
        assertThatThrownBy(() -> filled().get("permit four four seven one")).isInstanceOf(Unreadable.class);
    }

    @Test
    void aNumberNobodyIssuedSaysSo() {
        NotOnFile refusal = catchThrowableOfType(NotOnFile.class, () -> filled().get("25-P-009999"));
        assertThat(refusal.facts().get("number")).isEqualTo("25-P-009999");
        assertThat(filled().has("25-P-009999")).isFalse();
    }

    @Test
    void theSamePermitIsNotFiledTwice() {
        PermitRegister register = filled();
        assertThatThrownBy(() -> register.file(aPermit())).isInstanceOf(Conflict.class);
    }

    @Test
    void aCarrierGetsItsOwnPermitsNewestFirst() {
        List<Permit> found = filled().forCarrier(1204471);
        assertThat(found).hasSize(3);
        assertThat(found.get(0).number().toString()).startsWith("25-");
        assertThat(found).allSatisfy(one -> assertThat(one.usdot()).isEqualTo(1204471));
        assertThat(filled().forCarrier(9999)).isEmpty();
    }

    @Test
    void whatIsOnTheRoadTodayIsAskedByDay() {
        PermitRegister register = filled();
        assertThat(register.onTheRoad(START)).hasSize(4);
        assertThat(register.onTheRoad(LocalDate.of(2025, 4, 15))).hasSize(1);
        assertThat(register.onTheRoad(LocalDate.of(2025, 5, 1))).isEmpty();
    }

    @Test
    void aVoidPermitIsNotOnTheRoadAndIsStillOnFile() {
        PermitRegister register = filled();
        register.get("25-P-004471").makeVoid(START, "load cancelled");
        assertThat(register.onTheRoad(START)).hasSize(3);
        assertThat(register.size()).isEqualTo(4);
        assertThat(register.inState(PermitState.VOID)).hasSize(1);
    }

    @Test
    void thePermitsIssuedOnADayAreTheDaysWork() {
        PermitRegister register = filled();
        assertThat(register.issuedOn(ISSUED)).hasSize(3);
        assertThat(register.issuedOn(LocalDate.of(2025, 4, 1))).hasSize(1);
    }

    @Test
    void theExpiringListIsWhatTheOfficeRingsAboutOnFriday() {
        PermitRegister register = filled();
        List<Permit> expiring = register.expiring(LocalDate.of(2025, 4, 9), 2);
        assertThat(expiring)
                .extracting(one -> one.number().toString())
                .containsExactly("25-P-004471", "25-P-004473", "25-S-000001");
        List<Permit> longer = register.expiring(LocalDate.of(2025, 4, 9), 20);
        assertThat(longer.get(longer.size() - 1).end()).isEqualTo(LocalDate.of(2025, 4, 20));
    }

    @Test
    void anExpiredPermitIsNotExpiringAnyMore() {
        assertThat(filled().expiring(LocalDate.of(2025, 4, 15), 2)).isEmpty();
    }

    @Test
    void theSuperloadSeriesCanBeListedOnItsOwn() {
        assertThat(filled().ofSeries(Series.SUPERLOAD))
                .extracting(one -> one.number().toString())
                .containsExactly("25-S-000001");
    }

    @Test
    void theChainOfReplacementsCanBeWalkedFromEitherEnd() {
        PermitRegister register = new PermitRegister();
        Permit first = register.file(aPermit());
        Permit second = register.file(permit(1, 1204471, Series.REPLACEMENT)
                .supersedes(first.number())
                .build());
        first.supersede(START, second.number());
        assertThat(register.chain("25-P-004471"))
                .extracting(one -> one.number().toString())
                .containsExactly("25-P-004471", "25-R-000001");
        assertThat(register.chain("25-R-000001")).containsExactly(second);
    }

    @Test
    void suspendingACarrierVoidsEverythingItHolds() {
        PermitRegister register = filled();
        List<Permit> voided = register.voidAllFor(1204471, START, "carrier suspended");
        assertThat(voided).hasSize(3);
        assertThat(voided).allSatisfy(one -> assertThat(one.state()).isSameAs(PermitState.VOID));
        assertThat(register.get("25-P-004473").state()).isSameAs(PermitState.ISSUED);
        assertThat(register.voidAllFor(1204471, START, "again")).isEmpty();
    }
}
