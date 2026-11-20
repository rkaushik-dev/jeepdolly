package io.github.rkaushikdev.jeepdolly.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.jeepdolly.Dollars;
import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.NotAllowed;
import io.github.rkaushikdev.jeepdolly.NotOnFile;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.enforcement.Citation;
import io.github.rkaushikdev.jeepdolly.enforcement.CitationState;
import io.github.rkaushikdev.jeepdolly.enforcement.Offence;
import io.github.rkaushikdev.jeepdolly.permits.Series;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.persistence.models.AuditRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.CitationRow;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Filing a citation, and the four steps that used to be done by hand. */
class EnforcementServiceTest {

    private static final LocalDate WRITTEN = LocalDate.of(2025, 4, 8);
    private static final LocalDate FILED = LocalDate.of(2025, 4, 11);
    private static final String CLERK = "M Cyr";
    private static final String TICKET = "E-25-0417";

    private UnitOfWork work;

    @BeforeEach
    void open() {
        work = Databases.seeded().unitOfWork();
    }

    @AfterEach
    void close() {
        work.close();
    }

    private Citation fileOne() {
        return fileOne(TICKET, Offence.OVERWEIGHT, WRITTEN, 1204471);
    }

    private Citation fileOne(String number, LocalDate written) {
        return fileOne(number, Offence.OVERWEIGHT, written, 1204471);
    }

    private Citation fileOne(String number, Offence offence, LocalDate written, int usdot) {
        Optional<Pounds> over = offence.byWeight() ? Optional.of(new Pounds(12000)) : Optional.empty();
        return fileOne(number, offence, written, written.isAfter(FILED) ? written : FILED, usdot, over);
    }

    private Citation fileOne(
            String number, Offence offence, LocalDate written, LocalDate filed, int usdot, Optional<Pounds> over) {
        Citation found = Enforcement.fileCitation(
                work,
                number,
                usdot,
                "Sgt A Thibodeau",
                offence,
                "US201-2 at Fairfield",
                written,
                filed,
                CLERK,
                over,
                Optional.empty(),
                "");
        work.entityManager().flush();
        return found;
    }

    private void flush() {
        work.entityManager().flush();
    }

    @Test
    void aCitationIsPricedOffTheScheduleAndPutOnFile() {
        Citation one = fileOne();
        assertThat(one.amount()).isEqualTo(Dollars.of(580));
        assertThat(work.citations().get(TICKET).state()).isSameAs(CitationState.WRITTEN);
    }

    @Test
    void theCarrierNameIsReadOffTheRegisterRatherThanTyped() {
        assertThat(fileOne().carrierName()).isEqualTo("Bickford Heavy Haul");
    }

    @Test
    void aCarrierNobodyHasRegisteredIsARefusal() {
        assertThatThrownBy(() -> fileOne(TICKET, Offence.OVERWEIGHT, WRITTEN, 9999999))
                .isInstanceOf(NotOnFile.class);
    }

    @Test
    void aTicketFiledBeforeItWasWrittenIsRefused() {
        assertThatThrownBy(() -> fileOne(
                        TICKET,
                        Offence.OVERWEIGHT,
                        LocalDate.of(2025, 5, 1),
                        LocalDate.of(2025, 4, 1),
                        1204471,
                        Optional.of(new Pounds(12000))))
                .isInstanceOf(Impossible.class);
    }

    @Test
    void aSecondTicketInsideTheYearIsPricedAsARepeat() {
        fileOne("E-25-0100", LocalDate.of(2025, 1, 20));
        Citation second = fileOne(TICKET, WRITTEN);
        assertThat(second.assessed().multiplier()).isEqualTo(2);
        assertThat(second.amount()).isEqualTo(Dollars.of(1160));
    }

    @Test
    void aDismissedTicketDoesNotMakeTheNextOneARepeat() {
        fileOne("E-25-0100", LocalDate.of(2025, 1, 20));
        Enforcement.contest(work, "E-25-0100", LocalDate.of(2025, 2, 1), "the scale", CLERK);
        Enforcement.dismiss(work, "E-25-0100", LocalDate.of(2025, 2, 20), "scale had lapsed", CLERK);
        flush();
        assertThat(fileOne(TICKET, WRITTEN).assessed().multiplier()).isEqualTo(1);
    }

    @Test
    void anotherCarriersHistoryDoesNotPriceThisOnesTicket() {
        fileOne("E-25-0100", Offence.OVERWEIGHT, LocalDate.of(2025, 1, 20), 2299001);
        assertThat(fileOne(TICKET, WRITTEN).assessed().multiplier()).isEqualTo(1);
    }

    @Test
    void aFlatOffenceIgnoresAnOverageTheFormCarried() {
        Citation one = fileOne(TICKET, Offence.OFF_ROUTE, WRITTEN, FILED, 1204471, Optional.of(new Pounds(9000)));
        assertThat(one.over()).isEmpty();
        assertThat(one.amount()).isEqualTo(Dollars.of(250));
    }

    @Test
    void filingWritesTheOffenceAndTheMoneyOnTheTrail() {
        fileOne();
        List<AuditRow> found = Audit.forDocument(work.entityManager(), "citation", TICKET);
        assertThat(found).extracting(AuditRow::getAction).containsExactly("citation.filed");
        assertThat(found.get(0).getDetail()).isEqualTo("overweight $580.00");
    }

    @Test
    void contestingMovesItAndWritesTheGroundsOnTheTrail() {
        fileOne();
        Citation one = Enforcement.contest(work, TICKET, LocalDate.of(2025, 4, 20), "scale not certified", CLERK);
        flush();
        assertThat(one.state()).isSameAs(CitationState.CONTESTED);
        List<AuditRow> trail = Audit.forDocument(work.entityManager(), "citation", TICKET);
        assertThat(trail.get(trail.size() - 1).getDetail()).isEqualTo("scale not certified");
    }

    @Test
    void upholdingLeavesItOwing() {
        fileOne();
        Enforcement.contest(work, TICKET, LocalDate.of(2025, 4, 20), "grounds", CLERK);
        Citation one = Enforcement.uphold(work, TICKET, LocalDate.of(2025, 5, 2), CLERK);
        flush();
        assertThat(one.state()).isSameAs(CitationState.UPHELD);
        assertThat(work.citations().owing()).extracting(CitationRow::getNumber).containsExactly(TICKET);
    }

    @Test
    void dismissingWritesTheReasonOntoTheRowForTheLetter() {
        fileOne();
        Enforcement.contest(work, TICKET, LocalDate.of(2025, 4, 20), "grounds", CLERK);
        Enforcement.dismiss(work, TICKET, LocalDate.of(2025, 5, 2), "scale had lapsed", CLERK);
        flush();
        assertThat(work.citations().row(TICKET).getClosingReason()).isEqualTo("scale had lapsed");
    }

    @Test
    void payingItTakesItOffTheChaseList() {
        fileOne();
        Enforcement.pay(work, TICKET, LocalDate.of(2025, 4, 22), CLERK, "cheque 4471");
        flush();
        assertThat(work.citations().owing()).isEmpty();
    }

    @Test
    void voidingItWritesTheReasonAndTakesItOffTheList() {
        fileOne();
        Enforcement.makeVoid(work, TICKET, LocalDate.of(2025, 4, 14), "wrong usdot", CLERK);
        flush();
        assertThat(work.citations().row(TICKET).getState()).isSameAs(CitationState.VOID);
        assertThat(work.citations().owing()).isEmpty();
    }

    @Test
    void aPaidCitationCannotBeVoidedThroughTheServiceEither() {
        fileOne();
        Enforcement.pay(work, TICKET, LocalDate.of(2025, 4, 22), CLERK);
        flush();
        assertThatThrownBy(() -> Enforcement.makeVoid(work, TICKET, LocalDate.of(2025, 4, 23), "wrong usdot", CLERK))
                .isInstanceOf(NotAllowed.class);
    }

    @Test
    void aCitationNobodyHasIsARefusalAtEveryStep() {
        assertThatThrownBy(() -> Enforcement.pay(work, "E-25-9999", FILED, CLERK))
                .isInstanceOf(NotOnFile.class);
    }

    @Test
    void theNumbersComeOffTheSameCounterInTheirOwnSeries() {
        String first = Enforcement.nextNumber(work, WRITTEN);
        String second = Enforcement.nextNumber(work, WRITTEN);
        assertThat(first).isEqualTo("25-E-000001");
        assertThat(second).isEqualTo("25-E-000002");
        assertThat(work.counter().issued(2025, Series.CITATION)).isEqualTo(2);
    }

    @Test
    void theCitationSeriesDoesNotEatThePermitSeries() {
        Enforcement.nextNumber(work, WRITTEN);
        assertThat(work.counter().number(WRITTEN, Series.SINGLE)).hasToString("25-P-000001");
    }

    @Test
    void whatACarrierOwesAddsUpWhatIsStillOpen() {
        fileOne("E-25-0100", LocalDate.of(2025, 1, 20));
        fileOne(TICKET, WRITTEN);
        assertThat(Enforcement.owedBy(work, 1204471)).isEqualTo(Dollars.of(1740).cents());
    }

    @Test
    void aPaidCitationIsNotOwedAnyMore() {
        fileOne();
        Enforcement.pay(work, TICKET, LocalDate.of(2025, 4, 22), CLERK);
        flush();
        assertThat(Enforcement.owedBy(work, 1204471)).isZero();
    }

    @Test
    void aCarrierWithNothingAgainstItOwesNothing() {
        assertThat(Enforcement.owedBy(work, 2299001)).isZero();
    }

    @Test
    void theScheduleIsAskedForByDayRatherThanReachedFor() {
        assertThat(Enforcement.scheduleOn(WRITTEN).effective()).isEqualTo(LocalDate.of(2024, 1, 1));
    }
}
