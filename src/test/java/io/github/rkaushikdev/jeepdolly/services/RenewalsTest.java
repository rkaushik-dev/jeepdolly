package io.github.rkaushikdev.jeepdolly.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.jeepdolly.Dollars;
import io.github.rkaushikdev.jeepdolly.NotAllowed;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.api.Deps;
import io.github.rkaushikdev.jeepdolly.domain.Envelope;
import io.github.rkaushikdev.jeepdolly.enforcement.Account;
import io.github.rkaushikdev.jeepdolly.enforcement.EnforcementBook;
import io.github.rkaushikdev.jeepdolly.enforcement.Owed;
import io.github.rkaushikdev.jeepdolly.enforcement.Standing;
import io.github.rkaushikdev.jeepdolly.permits.Annual;
import io.github.rkaushikdev.jeepdolly.permits.Application;
import io.github.rkaushikdev.jeepdolly.permits.Numbering;
import io.github.rkaushikdev.jeepdolly.permits.Office;
import io.github.rkaushikdev.jeepdolly.permits.Permit;
import io.github.rkaushikdev.jeepdolly.permits.PermitState;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.persistence.models.AuditRow;
import io.github.rkaushikdev.jeepdolly.support.Applications;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import io.github.rkaushikdev.jeepdolly.support.Permits;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The renewal run, and the batch that is all nine or none of them. */
class RenewalsTest {

    private static final LocalDate ISSUED = LocalDate.of(2025, 1, 6);
    private static final LocalDate NEAR = LocalDate.of(2025, 12, 15);
    private static final LocalDate FAR = LocalDate.of(2025, 6, 1);

    private UnitOfWork work;

    @BeforeEach
    void open() {
        work = Databases.seeded().unitOfWork();
    }

    @AfterEach
    void close() {
        work.close();
    }

    private Permit anAnnual() {
        return anAnnual("25-04471", 1204471);
    }

    /** One annual permit on file, with the application behind it. */
    private Permit anAnnual(String reference, int usdot) {
        Application application =
                Applications.application(reference).usdot(usdot).build();
        work.applications().add(application);
        work.entityManager().flush();
        Office office = Deps.officeFrom(work, ISSUED);
        Permit made = Annual.issue(
                new Numbering(work.counter()),
                office.library(),
                office.schedules().on(ISSUED),
                reference,
                usdot,
                office.carriers().get(usdot).name(),
                Envelope.read("11'", "13'6", "70'"),
                new Pounds(99000),
                office.commodities().get(application.commodity()),
                ISSUED);
        work.permits().add(made);
        work.entityManager().flush();
        return made;
    }

    /** The office off the database, so the counter is the one on file. */
    private Office desk() {
        return Deps.officeFrom(work, NEAR);
    }

    private static String number(Permit permit) {
        return permit.number().toString();
    }

    @Test
    void nothingIsDueOnAQuietDay() {
        assertThat(Renewals.dueBy(work, FAR)).isEmpty();
    }

    @Test
    void anAnnualRunningOutInsideTheWindowIsDue() {
        Permit made = anAnnual();
        List<Renewals.Due> found = Renewals.dueBy(work, NEAR);
        assertThat(found).extracting(Renewals.Due::number).containsExactly(number(made));
        assertThat(found.get(0).carrier()).isEqualTo("Bickford Heavy Haul");
    }

    @Test
    void anAnnualRunningOutBeyondTheWindowIsNot() {
        anAnnual();
        assertThat(Renewals.dueBy(work, FAR)).isEmpty();
    }

    @Test
    void theWindowCanBeAskedForWider() {
        anAnnual();
        assertThat(Renewals.dueBy(work, FAR, 365)).hasSize(1);
    }

    @Test
    void aDuePermitSaysHowManyDaysAreLeft() {
        anAnnual();
        Renewals.Due found = Renewals.dueBy(work, NEAR).get(0);
        assertThat(found.daysLeft(NEAR)).isEqualTo(ChronoUnit.DAYS.between(NEAR, found.expires()));
        assertThat(found.toString()).startsWith("25-A-");
    }

    @Test
    void aVoidedAnnualIsNotDue() {
        Permit made = anAnnual();
        made.makeVoid(NEAR, "carrier sold the crane");
        work.permits().saveState(made);
        work.entityManager().flush();
        assertThat(Renewals.dueBy(work, NEAR)).isEmpty();
    }

    @Test
    void aSingleTripPermitIsNeverOnTheRenewalList() {
        Application application = Applications.anApplication();
        work.applications().add(application);
        work.entityManager().flush();
        work.permits().add(Permits.aPermit(application));
        work.entityManager().flush();
        assertThat(Renewals.dueBy(work, LocalDate.of(2025, 4, 8), 365)).isEmpty();
    }

    @Test
    void oneLetterPerCarrierHoweverManyPermits() {
        anAnnual("25-04471", 1204471);
        anAnnual("25-04472", 1204471);
        List<Renewals.Letter> found = Renewals.letters(work, NEAR);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).count()).isEqualTo(2);
        assertThat(found.get(0).usdot()).isEqualTo(1204471);
    }

    @Test
    void twoCarriersGetTwoLetters() {
        anAnnual("25-04471", 1204471);
        anAnnual("25-04480", 2299001);
        assertThat(Renewals.letters(work, NEAR)).hasSize(2);
    }

    @Test
    void theLetterReadsTheWayItIsPosted() {
        anAnnual();
        List<String> printed = Renewals.letters(work, NEAR).get(0).lines();
        assertThat(printed.get(0)).isEqualTo("Bickford Heavy Haul (1204471)");
        assertThat(printed.get(1)).startsWith("  25-A-");
        assertThat(printed.get(printed.size() - 1)).startsWith("  1 permits, first on");
    }

    @Test
    void theLetterSaysTheShortFormToo() {
        anAnnual();
        assertThat(Renewals.letters(work, NEAR).get(0).toString()).startsWith("Bickford Heavy Haul: 1 due by");
    }

    @Test
    void aQuietWindowWritesNoLetters() {
        assertThat(Renewals.letters(work, FAR)).isEmpty();
    }

    @Test
    void renewingOnePermitIssuesANewOne() {
        Permit made = anAnnual();
        Renewals.Renewed found = Renewals.renew(work, desk(), 1204471, List.of(number(made)), NEAR);
        assertThat(found.count()).isEqualTo(1);
        assertThat(found.issued().get(0).number()).isNotEqualTo(made.number());
        assertThat(found.issued().get(0).start()).isEqualTo(NEAR);
    }

    @Test
    void theOldPermitIsLeftToRunOutRatherThanSuperseded() {
        Permit made = anAnnual();
        Renewals.renew(work, desk(), 1204471, List.of(number(made)), NEAR);
        work.entityManager().flush();
        assertThat(work.permits().get(number(made)).state()).isSameAs(PermitState.ISSUED);
    }

    @Test
    void theNewPermitRunsAYearFromTheDayItWasRenewed() {
        Permit made = anAnnual();
        Renewals.Renewed found = Renewals.renew(work, desk(), 1204471, List.of(number(made)), NEAR);
        assertThat(found.issued().get(0).end()).isEqualTo(NEAR.plusDays(364));
    }

    @Test
    void renewingTwoGivesTwoNumbersInOrder() {
        Permit first = anAnnual("25-04471", 1204471);
        Permit second = anAnnual("25-04472", 1204471);
        Renewals.Renewed found = Renewals.renew(work, desk(), 1204471, List.of(number(first), number(second)), NEAR);
        assertThat(found.count()).isEqualTo(2);
        assertThat(found.issued()).extracting(RenewalsTest::number).isSorted();
    }

    @Test
    void renewingNothingIsRefused() {
        assertThatThrownBy(() -> Renewals.renew(work, desk(), 1204471, List.of(), NEAR))
                .isInstanceOf(NotAllowed.class);
    }

    @Test
    void aPermitBelongingToSomebodyElseStopsTheBatch() {
        Permit mine = anAnnual("25-04471", 1204471);
        Permit theirs = anAnnual("25-04480", 2299001);
        assertThatThrownBy(() -> Renewals.renew(work, desk(), 1204471, List.of(number(mine), number(theirs)), NEAR))
                .isInstanceOf(NotAllowed.class)
                .hasMessageContaining("does not belong");
    }

    @Test
    void aPermitThatIsNotLiveStopsTheBatch() {
        Permit good = anAnnual("25-04471", 1204471);
        Permit dead = anAnnual("25-04472", 1204471);
        dead.makeVoid(NEAR, "crane sold");
        work.permits().saveState(dead);
        work.entityManager().flush();
        assertThatThrownBy(() -> Renewals.renew(work, desk(), 1204471, List.of(number(good), number(dead)), NEAR))
                .isInstanceOf(NotAllowed.class);
    }

    @Test
    void aHeldCarrierCannotRenewAnything() {
        Permit made = anAnnual();
        EnforcementBook book = new EnforcementBook();
        LocalDate written = LocalDate.of(2025, 1, 20);
        book.add(new Account(
                1204471, List.of(new Owed("E-25-0100", written, Dollars.of(500), Standing.dueDay(written, 21)))));
        Office held = desk().withEnforcement(book);
        assertThatThrownBy(() -> Renewals.renew(work, held, 1204471, List.of(number(made)), NEAR))
                .isInstanceOf(NotAllowed.class)
                .hasMessageContaining("cannot renew");
    }

    @Test
    void aCarrierWithNoPaperCannotRenew() {
        Permit made = anAnnual("25-04480", 2299001);
        assertThatThrownBy(() -> Renewals.renew(work, desk(), 2299001, List.of(number(made)), NEAR))
                .isInstanceOf(NotAllowed.class);
    }

    @Test
    void aRenewalLandsOnTheTrailAgainstTheCarrier() {
        Permit made = anAnnual();
        Renewals.renew(work, desk(), 1204471, List.of(number(made)), NEAR, "M Cyr");
        work.entityManager().flush();
        List<AuditRow> found = Audit.forDocument(work.entityManager(), "carrier", "1204471");
        AuditRow last = found.get(found.size() - 1);
        assertThat(last.getAction()).isEqualTo("permit.renewed");
        assertThat(last.getDetail()).isEqualTo("1 annuals");
    }

    @Test
    void theBatchSaysWhatItReplaced() {
        Permit made = anAnnual();
        Renewals.Renewed found = Renewals.renew(work, desk(), 1204471, List.of(number(made)), NEAR);
        assertThat(found.replaced()).containsExactly(number(made));
        assertThat(found).hasToString("1204471: 1 renewed");
    }
}
