package io.github.rkaushikdev.jeepdolly.reports;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.jeepdolly.Dollars;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.enforcement.Citation;
import io.github.rkaushikdev.jeepdolly.enforcement.Offence;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.services.Enforcement;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** What was written at the roadside, counted three ways for three people. */
class EnforcementReportTest {

    private static final LocalDate FIRST = LocalDate.of(2025, 1, 1);
    private static final LocalDate LAST = LocalDate.of(2025, 12, 31);
    private static final LocalDate WRITTEN = LocalDate.of(2025, 4, 8);
    private static final String CLERK = "M Cyr";
    private static final String TICKET = "E-25-0417";

    private UnitOfWork work;
    private EntityManager db;

    @BeforeEach
    void open() {
        work = Databases.seeded().unitOfWork();
        db = work.entityManager();
    }

    @AfterEach
    void close() {
        work.close();
    }

    private Citation fileOne() {
        return fileOne(TICKET, Offence.OVERWEIGHT, WRITTEN, 1204471);
    }

    private Citation fileOne(String number, LocalDate on) {
        return fileOne(number, Offence.OVERWEIGHT, on, 1204471);
    }

    private Citation fileOne(String number, Offence offence, LocalDate on, int usdot) {
        Citation found = Enforcement.fileCitation(
                work,
                number,
                usdot,
                "Sgt A Thibodeau",
                offence,
                "US201-2 at Fairfield",
                on,
                on,
                CLERK,
                offence.byWeight() ? Optional.of(new Pounds(12000)) : Optional.empty(),
                Optional.empty(),
                "");
        db.flush();
        return found;
    }

    private void contestAndDismiss(String number, LocalDate contested, LocalDate dismissed) {
        Enforcement.contest(work, number, contested, "grounds", CLERK);
        Enforcement.dismiss(work, number, dismissed, "scale", CLERK);
        db.flush();
    }

    @Test
    void aQuietWindowCountsNothing() {
        assertThat(EnforcementReport.written(db, FIRST, LAST)).isEmpty();
        assertThat(EnforcementReport.byOffence(db, FIRST, LAST)).isEmpty();
    }

    @Test
    void theCountByOffenceIsWhatTheLegislatureQuotes() {
        fileOne();
        fileOne("E-25-0500", Offence.NO_PERMIT, LocalDate.of(2025, 6, 2), 1204471);
        fileOne("E-25-0501", Offence.NO_PERMIT, LocalDate.of(2025, 6, 3), 1204471);
        assertThat(EnforcementReport.byOffence(db, FIRST, LAST))
                .isEqualTo(Map.of(Offence.OVERWEIGHT, 1, Offence.NO_PERMIT, 2));
    }

    @Test
    void aDismissedCitationIsStillAnEnforcementAction() {
        Citation one = fileOne();
        contestAndDismiss(one.number(), LocalDate.of(2025, 4, 20), LocalDate.of(2025, 5, 2));
        assertThat(EnforcementReport.byOffence(db, FIRST, LAST)).isEqualTo(Map.of(Offence.OVERWEIGHT, 1));
    }

    @Test
    void aCitationOutsideTheWindowIsNotCounted() {
        fileOne(TICKET, LocalDate.of(2024, 4, 8));
        assertThat(EnforcementReport.byOffence(db, FIRST, LAST)).isEmpty();
    }

    @Test
    void theMoneyStartsAsAllAssessedAndAllOwing() {
        fileOne();
        EnforcementReport.Money found = EnforcementReport.money(db, FIRST, LAST);
        assertThat(found.assessed()).isEqualTo(Dollars.of(580));
        assertThat(found.owing()).isEqualTo(Dollars.of(580));
        assertThat(found.taken()).isEqualTo(Dollars.of(0));
    }

    @Test
    void payingMovesItFromOwingToTaken() {
        fileOne();
        Enforcement.pay(work, TICKET, LocalDate.of(2025, 4, 22), CLERK);
        db.flush();
        EnforcementReport.Money found = EnforcementReport.money(db, FIRST, LAST);
        assertThat(found.taken()).isEqualTo(Dollars.of(580));
        assertThat(found.owing()).isEqualTo(Dollars.of(0));
    }

    @Test
    void aDismissedCitationIsAssessedAndNeverTaken() {
        fileOne();
        contestAndDismiss(TICKET, LocalDate.of(2025, 4, 20), LocalDate.of(2025, 5, 2));
        EnforcementReport.Money found = EnforcementReport.money(db, FIRST, LAST);
        assertThat(found.assessed()).isEqualTo(Dollars.of(580));
        assertThat(found.taken()).isEqualTo(Dollars.of(0));
        assertThat(found.owing()).isEqualTo(Dollars.of(0));
        assertThat(found.writtenOff()).isEqualTo(Dollars.of(580));
    }

    @Test
    void theMoneyPrintsTheFourAmounts() {
        fileOne();
        assertThat(EnforcementReport.money(db, FIRST, LAST).lines())
                .containsExactly("assessed  $580.00", "taken     $0.00", "owing     $580.00", "closed unpaid  $0.00");
    }

    @Test
    void theMoneySaysTheShortFormToo() {
        fileOne();
        assertThat(EnforcementReport.money(db, FIRST, LAST)).hasToString("$580.00 assessed, $0.00 taken, $580.00 out");
    }

    @Test
    void oneCitationDoesNotPutACarrierOnTheLetterList() {
        fileOne();
        assertThat(EnforcementReport.watchList(db, FIRST, LAST)).isEmpty();
    }

    @Test
    void twoCitationsDo() {
        fileOne();
        fileOne("E-25-0500", LocalDate.of(2025, 6, 2));
        List<EnforcementReport.Watched> found = EnforcementReport.watchList(db, FIRST, LAST);
        assertThat(found).extracting(EnforcementReport.Watched::usdot).containsExactly(1204471);
        assertThat(found.get(0).count()).isEqualTo(2);
        assertThat(found.get(0).name()).isEqualTo("Bickford Heavy Haul");
    }

    @Test
    void theLetterListCarriesWhatIsStillOut() {
        fileOne();
        fileOne("E-25-0500", LocalDate.of(2025, 6, 2));
        Enforcement.pay(work, TICKET, LocalDate.of(2025, 4, 22), CLERK);
        db.flush();
        // The second one is a repeat, so it is priced at twice the flat rate.
        assertThat(EnforcementReport.watchList(db, FIRST, LAST).get(0).owing()).isEqualTo(Dollars.of(1160));
    }

    @Test
    void aVoidedCitationDoesNotCountTowardsTheLetter() {
        fileOne();
        fileOne("E-25-0500", LocalDate.of(2025, 6, 2));
        Enforcement.makeVoid(work, "E-25-0500", LocalDate.of(2025, 6, 3), "wrong usdot", CLERK);
        db.flush();
        assertThat(EnforcementReport.watchList(db, FIRST, LAST)).isEmpty();
    }

    @Test
    void aDismissedCitationStillCountsTowardsTheLetter() {
        fileOne();
        fileOne("E-25-0500", LocalDate.of(2025, 6, 2));
        contestAndDismiss("E-25-0500", LocalDate.of(2025, 6, 10), LocalDate.of(2025, 6, 20));
        assertThat(EnforcementReport.watchList(db, FIRST, LAST)).hasSize(1);
    }

    @Test
    void theLineTheLetterListPrints() {
        fileOne();
        fileOne("E-25-0500", LocalDate.of(2025, 6, 2));
        assertThat(EnforcementReport.watchList(db, FIRST, LAST).get(0))
                .hasToString("1204471  Bickford Heavy Haul  2 citations  $1,740.00 out");
    }

    @Test
    void theBusiestCarrierIsAtTheTop() {
        fileOne();
        fileOne("E-25-0500", LocalDate.of(2025, 6, 2));
        fileOne("E-25-0501", LocalDate.of(2025, 7, 2));
        fileOne("E-25-0600", Offence.OVERWEIGHT, LocalDate.of(2025, 6, 2), 2299001);
        fileOne("E-25-0601", Offence.OVERWEIGHT, LocalDate.of(2025, 7, 2), 2299001);
        assertThat(EnforcementReport.watchList(db, FIRST, LAST))
                .extracting(EnforcementReport.Watched::usdot)
                .containsExactly(1204471, 2299001);
    }

    @Test
    void theLineCanBeDrawnSomewhereElse() {
        fileOne();
        assertThat(EnforcementReport.watchList(db, FIRST, LAST, 1)).hasSize(1);
    }

    @Test
    void aQuietQuarterSaysSoRatherThanPrintingAHeadingAlone() {
        assertThat(EnforcementReport.report(db, FIRST, LAST))
                .containsExactly("enforcement 2025-01-01 to 2025-12-31", "nothing was written in that window");
    }

    @Test
    void theWholeReportReadsTopToBottom() {
        fileOne();
        fileOne("E-25-0500", Offence.NO_PERMIT, LocalDate.of(2025, 6, 2), 1204471);
        List<String> lines = EnforcementReport.report(db, FIRST, LAST);
        assertThat(lines.get(0)).isEqualTo("enforcement 2025-01-01 to 2025-12-31");
        assertThat(lines).contains("no permit  1", "overweight  1", "carriers to write to");
        assertThat(lines.get(lines.size() - 1)).startsWith("  1204471  Bickford Heavy Haul  2 citations");
    }

    @Test
    void aReportWithNobodyOverTheLinePrintsNoLetterHeading() {
        fileOne();
        assertThat(EnforcementReport.report(db, FIRST, LAST)).doesNotContain("carriers to write to");
    }
}
