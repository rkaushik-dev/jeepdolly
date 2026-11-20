package io.github.rkaushikdev.jeepdolly.escorts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.jeepdolly.Conflict;
import io.github.rkaushikdev.jeepdolly.NotOnFile;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The register of who is allowed to pilot a load. */
class EscortRegisterTest {

    private static final LocalDate JUNE = LocalDate.of(2025, 6, 10);
    private static final LocalDate MARCH = LocalDate.of(2025, 3, 3);
    private static final LocalDate APRIL_2026 = LocalDate.of(2026, 4, 30);

    private static Company aCompany() {
        return aCompany("PC-1", "Kennebec Pilot Car", false);
    }

    private static Company aCompany(String ident, String name, boolean suspended) {
        return new Company(ident, name, "Waterville", "207 555 0142", suspended);
    }

    private static Company aSuspendedCompany() {
        return aCompany("PC-1", "Kennebec Pilot Car", true);
    }

    private static Operator anOperator() {
        return anOperator("C-1001", "R Dubois", "PC-1", APRIL_2026, Set.of(), false);
    }

    private static Operator anOperator(
            String card, String name, String company, LocalDate expires, Set<Endorsement> endorsements, boolean off) {
        return new Operator(card, name, company, LocalDate.of(2023, 5, 1), expires, endorsements, off);
    }

    private static Operator card(String card) {
        return anOperator(card, "R Dubois", "PC-1", APRIL_2026, Set.of(), false);
    }

    private static Operator expiring(String card, LocalDate expires) {
        return anOperator(card, "R Dubois", "PC-1", expires, Set.of(), false);
    }

    private static Operator suspended() {
        return anOperator("C-1001", "R Dubois", "PC-1", APRIL_2026, Set.of(), true);
    }

    private static EscortRegister aRegister(Company company, List<Operator> operators) {
        EscortRegister register = new EscortRegister();
        register.addCompany(company);
        operators.forEach(register::certify);
        return register;
    }

    private static EscortRegister aRegister(List<Operator> operators) {
        return aRegister(aCompany(), operators);
    }

    @Test
    void aCompanyPrintsTheWayTheCounterReadsIt() {
        assertThat(aCompany()).hasToString("PC-1  Kennebec Pilot Car, Waterville");
        assertThat(aSuspendedCompany().toString()).endsWith("(suspended)");
    }

    @Test
    void aCompanyWithNoIdentIsNotACompany() {
        assertThatThrownBy(() -> new Company("  ", "Nobody", "Nowhere", "")).isInstanceOf(Conflict.class);
    }

    @Test
    void aCardThatExpiresBeforeItWasIssuedIsRefused() {
        assertThatThrownBy(() -> expiring("C-1001", LocalDate.of(2022, 1, 1))).isInstanceOf(Conflict.class);
    }

    @Test
    void aCardPrintsItsCompanyAndItsRunOutDate() {
        assertThat(anOperator()).hasToString("C-1001  R Dubois  PC-1  to 2026-04-30");
    }

    @Test
    void anEndorsementShowsOnTheCardLine() {
        String printed = anOperator("C-1001", "R Dubois", "PC-1", APRIL_2026, Set.of(Endorsement.HEIGHT_POLE), false)
                .toString();
        assertThat(printed).endsWith("[height pole]");
    }

    @Test
    void aCompanyIsOnlyFiledOnce() {
        EscortRegister register = aRegister(List.of());
        assertThatThrownBy(() -> register.addCompany(aCompany())).isInstanceOf(Conflict.class);
    }

    @Test
    void aCompanyNobodyHasIsARefusalThatNamesIt() {
        assertThatThrownBy(() -> new EscortRegister().company("PC-9")).isInstanceOf(NotOnFile.class);
    }

    @Test
    void aCardCannotBeCertifiedWithoutACompanyBehindIt() {
        EscortRegister register = new EscortRegister();
        register.addCompany(aCompany());
        assertThatThrownBy(
                        () -> register.certify(anOperator("C-1001", "R Dubois", "PC-2", APRIL_2026, Set.of(), false)))
                .isInstanceOf(NotOnFile.class);
    }

    @Test
    void theSameCardIsNotCertifiedTwice() {
        EscortRegister register = aRegister(List.of(anOperator()));
        assertThatThrownBy(() -> register.certify(anOperator())).isInstanceOf(Conflict.class);
    }

    @Test
    void aCardNobodyHasIsARefusalThatNamesIt() {
        assertThatThrownBy(() -> aRegister(List.of()).operator("C-9999")).isInstanceOf(NotOnFile.class);
    }

    @Test
    void aGoodCardOnTheDayOfTravelIsAvailable() {
        EscortRegister register = aRegister(List.of(anOperator()));
        assertThat(register.standing("C-1001", JUNE)).isSameAs(Standing.GOOD);
        assertThat(register.standing("C-1001", JUNE).available()).isTrue();
    }

    @Test
    void aCardThatRunsOutBeforeTheMoveIsExpiredOnThatDay() {
        EscortRegister register = aRegister(List.of(expiring("C-1001", LocalDate.of(2025, 5, 31))));
        assertThat(register.standing("C-1001", JUNE)).isSameAs(Standing.EXPIRED);
        assertThat(register.standing("C-1001", MARCH)).isSameAs(Standing.GOOD);
    }

    @Test
    void aCardIsGoodOnTheDayItExpiresAndNotTheDayAfter() {
        EscortRegister register = aRegister(List.of(expiring("C-1001", JUNE)));
        assertThat(register.standing("C-1001", JUNE)).isSameAs(Standing.GOOD);
        assertThat(register.standing("C-1001", LocalDate.of(2025, 6, 11))).isSameAs(Standing.EXPIRED);
    }

    @Test
    void aSuspendedOperatorIsSuspendedWhateverTheDate() {
        EscortRegister register = aRegister(List.of(suspended()));
        assertThat(register.standing("C-1001", MARCH)).isSameAs(Standing.SUSPENDED);
    }

    @Test
    void aSuspendedCompanyTakesEveryGoodCardWithIt() {
        EscortRegister register = aRegister(aSuspendedCompany(), List.of(anOperator()));
        assertThat(register.standing("C-1001", JUNE)).isSameAs(Standing.COMPANY_SUSPENDED);
        assertThat(register.standing("C-1001", JUNE).available()).isFalse();
    }

    @Test
    void theOperatorsOwnSuspensionIsReadBeforeTheCompanys() {
        EscortRegister register = aRegister(aSuspendedCompany(), List.of(suspended()));
        assertThat(register.standing("C-1001", JUNE)).isSameAs(Standing.SUSPENDED);
    }

    @Test
    void everybodyAvailableThatDayComesBackInCardOrder() {
        EscortRegister register = aRegister(List.of(
                anOperator("C-1003", "A Poulin", "PC-1", APRIL_2026, Set.of(), false),
                card("C-1001"),
                anOperator("C-1002", "M Levesque", "PC-1", LocalDate.of(2025, 1, 1), Set.of(), false)));
        assertThat(register.available(JUNE)).extracting(Operator::card).containsExactly("C-1001", "C-1003");
    }

    @Test
    void anEndorsementNarrowsWhoIsAvailable() {
        EscortRegister register = aRegister(List.of(
                card("C-1001"),
                anOperator("C-1002", "R Dubois", "PC-1", APRIL_2026, Set.of(Endorsement.HEIGHT_POLE), false)));
        List<Operator> found = register.available(JUNE, Endorsement.HEIGHT_POLE);
        assertThat(found).extracting(Operator::card).containsExactly("C-1002");
        assertThat(found.get(0).carries(Endorsement.HEIGHT_POLE)).isTrue();
        assertThat(found.get(0).carries(Endorsement.POLICE_LIAISON)).isFalse();
    }

    @Test
    void aCompanyWithNobodyAvailableComesBackEmpty() {
        EscortRegister register = aRegister(aSuspendedCompany(), List.of(anOperator()));
        assertThat(register.available(JUNE)).isEmpty();
    }

    @Test
    void theOperatorsOfOneCompanyCanBeAskedForOnTheirOwn() {
        EscortRegister register = aRegister(List.of(anOperator()));
        register.addCompany(aCompany("PC-2", "Somerset Escort", false));
        register.certify(anOperator("C-2001", "J Hebert", "PC-2", APRIL_2026, Set.of(), false));
        assertThat(register.operators("PC-2")).extracting(Operator::card).containsExactly("C-2001");
        assertThat(register.operators()).hasSize(2);
    }

    @Test
    void theCardsRunningOutInAWindowComeBackSoonestFirst() {
        EscortRegister register = aRegister(List.of(
                expiring("C-1001", LocalDate.of(2025, 9, 30)),
                expiring("C-1002", LocalDate.of(2025, 7, 15)),
                expiring("C-1003", LocalDate.of(2027, 1, 1))));
        List<Operator> found = register.expiring(JUNE, LocalDate.of(2025, 12, 31));
        assertThat(found).extracting(Operator::card).containsExactly("C-1002", "C-1001");
    }

    @Test
    void aSuspendedCardIsLeftOffTheRenewalList() {
        EscortRegister register =
                aRegister(List.of(anOperator("C-1001", "R Dubois", "PC-1", LocalDate.of(2025, 7, 15), Set.of(), true)));
        assertThat(register.expiring(JUNE, LocalDate.of(2025, 12, 31))).isEmpty();
    }

    @Test
    void theRegisterCountsCardsAndSaysWhetherItHoldsOne() {
        EscortRegister register = aRegister(List.of(anOperator()));
        assertThat(register.size()).isEqualTo(1);
        assertThat(register.contains("C-1001")).isTrue();
        assertThat(register.contains("C-9999")).isFalse();
    }

    @Test
    void theCompaniesComeBackInIdentOrder() {
        EscortRegister register = aRegister(List.of());
        register.addCompany(aCompany("PC-0", "Androscoggin Pilot", false));
        assertThat(register.companies()).extracting(Company::ident).containsExactly("PC-0", "PC-1");
    }
}
