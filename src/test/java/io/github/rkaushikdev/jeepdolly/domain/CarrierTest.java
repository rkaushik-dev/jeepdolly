package io.github.rkaushikdev.jeepdolly.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.jeepdolly.Conflict;
import io.github.rkaushikdev.jeepdolly.Dollars;
import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.NotOnFile;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Carriers, their paper, and the order the office reports trouble in. */
class CarrierTest {

    private static final LocalDate JAN = LocalDate.of(2025, 1, 1);
    private static final LocalDate MARCH = LocalDate.of(2025, 3, 3);
    private static final LocalDate DEC = LocalDate.of(2025, 12, 31);

    private static Insurance cover() {
        return cover(JAN, DEC);
    }

    private static Insurance cover(LocalDate effective, LocalDate expires) {
        return new Insurance("CA-88214", "Acadia Mutual", Dollars.of(1000000), effective, expires);
    }

    private static Bond bond() {
        return bond(JAN, DEC);
    }

    private static Bond bond(LocalDate effective, LocalDate expires) {
        return new Bond("SB-4471", "Penobscot Surety", Dollars.of(25000), effective, expires);
    }

    private static Carrier.Builder hauler() {
        return hauler(1204471, "Bickford Heavy Haul", "ME");
    }

    private static Carrier.Builder hauler(int usdot, String name, String state) {
        return Carrier.builder(usdot, name, "Newport", state);
    }

    @Test
    void aCarrierPrintsWithItsNumberBecauseNamesRepeat() {
        assertThat(hauler().build()).hasToString("Bickford Heavy Haul (1204471)");
    }

    @Test
    void aNumberThatIsNotAUsdotNumberIsRefused() {
        assertThatThrownBy(() -> hauler(0, "Bickford Heavy Haul", "ME").build()).isInstanceOf(Impossible.class);
        assertThatThrownBy(() -> hauler(123456789, "Bickford Heavy Haul", "ME").build())
                .isInstanceOf(Impossible.class);
    }

    @Test
    void aCarrierNeedsANameAndAStateThatLooksLikeOne() {
        assertThatThrownBy(() -> hauler(1204471, "  ", "ME").build()).isInstanceOf(Impossible.class);
        assertThatThrownBy(() -> hauler(1204471, "Bickford Heavy Haul", "Maine").build())
                .isInstanceOf(Impossible.class);
        assertThatThrownBy(() -> hauler(1204471, "Bickford Heavy Haul", "me").build())
                .isInstanceOf(Impossible.class);
    }

    @Test
    void aCertificateCoversASpanAndNotJustADay() {
        Insurance paper = cover(JAN, LocalDate.of(2025, 6, 30));
        assertThat(paper.inForce(MARCH)).isTrue();
        assertThat(paper.covers(MARCH, LocalDate.of(2025, 6, 1))).isTrue();
        assertThat(paper.covers(MARCH, LocalDate.of(2025, 7, 1))).isFalse();
        assertThat(paper).hasToString("CA-88214 Acadia Mutual to 2025-06-30");
    }

    @Test
    void aCertificateThatExpiresBeforeItStartsIsRefused() {
        assertThatThrownBy(() -> cover(DEC, JAN)).isInstanceOf(Impossible.class);
    }

    @Test
    void aCertificateWithNoLimitOnItIsNotACertificate() {
        assertThatThrownBy(() -> new Insurance("CA-1", "Acadia Mutual", new Dollars(0), JAN, DEC))
                .isInstanceOf(Impossible.class);
    }

    @Test
    void aCarrierWithCurrentPaperIsInGoodStanding() {
        Carrier carrier = hauler().insurance(cover()).build();
        assertThat(carrier.inGoodStanding(MARCH, MARCH)).isTrue();
        assertThat(carrier.standing(MARCH, MARCH)).isEmpty();
    }

    @Test
    void aCarrierWithNoCertificateAtAllIsStopped() {
        assertThat(hauler().build().standing(MARCH, MARCH)).contains("no certificate of insurance on file");
    }

    @Test
    void aPermitRunningPastTheCertificateIsStoppedByTheSpan() {
        Carrier carrier =
                hauler().insurance(cover(JAN, LocalDate.of(2025, 3, 10))).build();
        assertThat(carrier.inGoodStanding(MARCH, LocalDate.of(2025, 3, 9))).isTrue();
        assertThat(carrier.inGoodStanding(MARCH, LocalDate.of(2025, 3, 20))).isFalse();
        assertThat(carrier.standing(MARCH, LocalDate.of(2025, 3, 20)).orElseThrow())
                .contains("does not cover the whole trip");
    }

    @Test
    void aBondIsOnlyWantedOnceTheLoadIsHeavy() {
        Carrier carrier = hauler().insurance(cover()).build();
        assertThat(carrier.needsBond(119000)).isFalse();
        assertThat(carrier.needsBond(130000)).isTrue();
        assertThat(carrier.inGoodStanding(MARCH, MARCH, 119000)).isTrue();
        assertThat(carrier.standing(MARCH, MARCH, 130000).orElseThrow()).startsWith("no bond on file");
    }

    @Test
    void aBondedCarrierClearsTheHeavyCheck() {
        Carrier carrier = hauler().insurance(cover()).bond(bond()).build();
        assertThat(carrier.inGoodStanding(MARCH, MARCH, 180000)).isTrue();
    }

    @Test
    void aBondThatLapsesInsideTheTripStopsItToo() {
        Carrier carrier = hauler().insurance(cover())
                .bond(bond(JAN, LocalDate.of(2025, 3, 5)))
                .build();
        assertThat(carrier.standing(MARCH, LocalDate.of(2025, 3, 20), 180000).orElseThrow())
                .contains("bond SB-4471 does not cover");
    }

    @Test
    void aBondThatExpiresBeforeItTakesEffectIsRefused() {
        assertThatThrownBy(() -> bond(DEC, JAN)).isInstanceOf(Impossible.class);
    }

    @Test
    void aSuspensionBeatsEveryOtherFinding() {
        Carrier carrier = hauler().build();
        carrier.suspend("failed to pay four permits", LocalDate.of(2025, 2, 1));
        assertThat(carrier.insurance()).isEmpty();
        assertThat(carrier.standing(MARCH, MARCH).orElseThrow()).startsWith("suspended 2025-02-01 to further notice");
    }

    @Test
    void aSuspensionWithAnEndLiftsOnItsOwn() {
        Carrier carrier = hauler().insurance(cover()).build();
        carrier.suspend("late affidavit", LocalDate.of(2025, 2, 1), LocalDate.of(2025, 2, 28));
        assertThat(carrier.suspensionOn(LocalDate.of(2025, 2, 15))).isPresent();
        assertThat(carrier.suspensionOn(MARCH)).isEmpty();
        assertThat(carrier.inGoodStanding(MARCH, MARCH)).isTrue();
    }

    @Test
    void aSuspensionThatLiftsBeforeItStartsIsRefused() {
        assertThatThrownBy(
                        () -> hauler().build().suspend("nothing", LocalDate.of(2025, 3, 1), LocalDate.of(2025, 2, 1)))
                .isInstanceOf(Impossible.class);
    }

    @Test
    void aSuspensionPrintsTheReasonBecauseTheLetterNeedsIt() {
        Suspension order = hauler().build()
                .suspend("failed to pay four permits", LocalDate.of(2025, 2, 1), LocalDate.of(2025, 4, 1));
        assertThat(order).hasToString("suspended 2025-02-01 to 2025-04-01: failed to pay four permits");
    }

    @Test
    void theRegisterRefusesTheSameUsdotTwice() {
        CarrierRegister register = new CarrierRegister();
        register.add(hauler().build());
        Conflict refusal = catchThrowableOfType(
                Conflict.class,
                () -> register.add(hauler(1204471, "Bickford Trucking", "ME").build()));
        assertThat(refusal.facts().get("usdot")).isEqualTo(1204471);
        assertThat(register.size()).isEqualTo(1);
    }

    @Test
    void askingForACarrierNobodyRegisteredSaysTheNumberBack() {
        NotOnFile refusal = catchThrowableOfType(NotOnFile.class, () -> new CarrierRegister().get(9999));
        assertThat(refusal.facts().get("usdot")).isEqualTo(9999);
    }

    @Test
    void theCounterSearchesByAnyPartOfTheName() {
        CarrierRegister register = new CarrierRegister();
        register.add(hauler().build());
        register.add(hauler(2299001, "Aroostook Crane Service", "ME").build());
        register.add(hauler(3311002, "Bickford Sons Rigging", "ME").build());
        assertThat(register.search("bickford")).extracting(Carrier::usdot).containsExactly(1204471, 3311002);
        assertThat(register.search("CRANE")).extracting(Carrier::name).containsExactly("Aroostook Crane Service");
        assertThat(register.search("   ")).isEmpty();
        assertThat(register.contains(2299001)).isTrue();
    }

    @Test
    void theSuspendedListIsWhatTheSupervisorReadsOnMonday() {
        CarrierRegister register = new CarrierRegister();
        Carrier good = register.add(
                hauler().insurance(cover()).billing(Billing.ACCOUNT).build());
        Carrier bad =
                register.add(hauler(2299001, "Aroostook Crane Service", "ME").build());
        bad.suspend("no certificate", LocalDate.of(2025, 2, 1));
        assertThat(register.suspendedOn(MARCH)).extracting(Carrier::name).containsExactly("Aroostook Crane Service");
        assertThat(good.billing()).isSameAs(Billing.ACCOUNT);
        assertThat(good.billing()).hasToString("account");
    }
}
