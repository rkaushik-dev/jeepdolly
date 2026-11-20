package io.github.rkaushikdev.jeepdolly.escorts;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Checking the pilot cars a hauler names against the register. */
class EscortNamingTest {

    private static final LocalDate JUNE = LocalDate.of(2025, 6, 10);
    private static final LocalDate APRIL_2026 = LocalDate.of(2026, 4, 30);

    /** One card to put on the register: number, expiry, endorsements, suspended. */
    private record Card(String card, LocalDate expires, Set<Endorsement> endorsements, boolean off) {}

    private static EscortRegister register(List<Card> cards, boolean suspended) {
        EscortRegister made = new EscortRegister();
        made.addCompany(new Company("PC-1", "Kennebec Pilot Car", "Waterville", "207 555 0142", suspended));
        for (Card one : cards) {
            made.certify(new Operator(
                    one.card(),
                    "Operator " + one.card().charAt(one.card().length() - 1),
                    "PC-1",
                    LocalDate.of(2023, 5, 1),
                    one.expires(),
                    one.endorsements(),
                    one.off()));
        }
        return made;
    }

    private static EscortRegister register(List<Card> cards) {
        return register(cards, false);
    }

    private static EscortRegister twoGood() {
        return register(List.of(
                new Card("C-1001", APRIL_2026, Set.of(), false),
                new Card("C-1002", APRIL_2026, Set.of(Endorsement.HEIGHT_POLE), false)));
    }

    private static Escorts escorts(int front, int rear, boolean police, boolean pole) {
        return new Escorts(front, rear, police, pole);
    }

    private static Escorts escorts() {
        return escorts(1, 1, false, false);
    }

    private static Escorts wantingAPole() {
        return escorts(1, 1, false, true);
    }

    @Test
    void namingNothingIsAllowedAndSaysSoOnThePermit() {
        Naming found = Naming.check(twoGood(), List.of(), escorts(), JUNE);
        assertThat(found.acceptable()).isTrue();
        assertThat(found.complete()).isFalse();
        assertThat(found.line()).isEqualTo(Naming.NOT_NAMED);
        assertThat(found).hasToString("no pilot cars named");
    }

    @Test
    void twoGoodCardsCoverALoadThatWantsTwo() {
        Naming found = Naming.check(twoGood(), List.of("C-1001", "C-1002"), escorts(), JUNE);
        assertThat(found.acceptable()).isTrue();
        assertThat(found.complete()).isTrue();
        assertThat(found.line()).isEqualTo("Operator 1 (C-1001), Operator 2 (C-1002)");
        assertThat(found).hasToString("2 of 2 named");
    }

    @Test
    void oneGoodCardOnALoadThatWantsTwoIsShortButNotRefused() {
        Naming found = Naming.check(twoGood(), List.of("C-1001"), escorts(), JUNE);
        assertThat(found.acceptable()).isTrue();
        assertThat(found.complete()).isFalse();
        assertThat(found.shortfall()).isEqualTo(1);
    }

    @Test
    void namingMoreThanTheLoadNeedsIsNobodysBusinessButTheCarriers() {
        Naming found = Naming.check(twoGood(), List.of("C-1001", "C-1002"), escorts(1, 0, false, false), JUNE);
        assertThat(found.complete()).isTrue();
        assertThat(found.shortfall()).isEqualTo(0);
    }

    @Test
    void aCardNobodyHasIsRefusedAndTheRefusalQuotesIt() {
        Naming found = Naming.check(twoGood(), List.of("C-9999"), escorts(), JUNE);
        assertThat(found.acceptable()).isFalse();
        assertThat(found.refusals()).containsExactly("pilot car card C-9999 is not on the register");
        assertThat(found).hasToString("1 of 1 named cards are no good");
    }

    @Test
    void aCardThatHasRunOutByTheDayOfTravelIsRefused() {
        EscortRegister book = register(List.of(new Card("C-1001", LocalDate.of(2025, 5, 31), Set.of(), false)));
        Naming found = Naming.check(book, List.of("C-1001"), escorts(), JUNE);
        assertThat(found.refusals()).containsExactly("pilot car card C-1001 is expired on 2025-06-10");
    }

    @Test
    void aSuspendedCardIsRefusedAndTheRefusalSaysWhichKind() {
        EscortRegister book = register(List.of(new Card("C-1001", APRIL_2026, Set.of(), true)));
        assertThat(Naming.check(book, List.of("C-1001"), escorts(), JUNE)
                        .refusals()
                        .get(0))
                .contains("suspended");
    }

    @Test
    void aCardFromASuspendedCompanyIsRefusedToo() {
        EscortRegister book = register(List.of(new Card("C-1001", APRIL_2026, Set.of(), false)), true);
        assertThat(Naming.check(book, List.of("C-1001"), escorts(), JUNE)
                        .refusals()
                        .get(0))
                .contains("company suspended");
    }

    @Test
    void theSameCardNamedTwiceIsRefusedOnce() {
        Naming found = Naming.check(twoGood(), List.of("C-1001", "C-1001"), escorts(), JUNE);
        assertThat(found.refusals()).containsExactly("pilot car card C-1001 is named twice");
        assertThat(found.good()).hasSize(1);
    }

    @Test
    void aLoadWantingAHeightPoleNeedsSomebodyExaminedOnOne() {
        Naming found = Naming.check(twoGood(), List.of("C-1001"), wantingAPole(), JUNE);
        assertThat(found.refusals())
                .containsExactly("this load needs a height pole and no card named carries the endorsement");
    }

    @Test
    void theEndorsementOnAnyNamedCardSatisfiesThePole() {
        Naming found = Naming.check(twoGood(), List.of("C-1001", "C-1002"), wantingAPole(), JUNE);
        assertThat(found.acceptable()).isTrue();
    }

    @Test
    void aLoadWantingAPoleWithNobodyNamedIsNotRefusedYet() {
        Naming found = Naming.check(twoGood(), List.of(), wantingAPole(), JUNE);
        assertThat(found.acceptable()).isTrue();
        assertThat(found.complete()).isFalse();
    }

    @Test
    void theBadCardDoesNotTakeTheGoodOneDownWithIt() {
        Naming found = Naming.check(twoGood(), List.of("C-1001", "C-9999"), escorts(), JUNE);
        assertThat(found.good()).extracting(Operator::card).containsExactly("C-1001");
        assertThat(found.refusals()).hasSize(1);
    }

    @Test
    void aPoliceEscortWantsNoCardBecauseTheTrooperHoldsNone() {
        Naming found = Naming.check(twoGood(), List.of("C-1001", "C-1002"), escorts(1, 1, true, false), JUNE);
        assertThat(found.acceptable()).isTrue();
    }

    @Test
    void theShortfallNeverGoesBelowNothing() {
        Naming found = Naming.check(twoGood(), List.of("C-1001", "C-1002"), escorts(0, 0, false, false), JUNE);
        assertThat(found.shortfall()).isEqualTo(0);
    }
}
