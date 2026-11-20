package io.github.rkaushikdev.jeepdolly.persistence.repositories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.jeepdolly.Conflict;
import io.github.rkaushikdev.jeepdolly.Dollars;
import io.github.rkaushikdev.jeepdolly.NotOnFile;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.enforcement.Citation;
import io.github.rkaushikdev.jeepdolly.enforcement.CitationState;
import io.github.rkaushikdev.jeepdolly.enforcement.Offence;
import io.github.rkaushikdev.jeepdolly.enforcement.Penalty;
import io.github.rkaushikdev.jeepdolly.enforcement.PenaltySchedule;
import io.github.rkaushikdev.jeepdolly.permits.Application;
import io.github.rkaushikdev.jeepdolly.permits.Permit;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.persistence.models.CitationRow;
import io.github.rkaushikdev.jeepdolly.support.Applications;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import io.github.rkaushikdev.jeepdolly.support.Permits;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Citations into the rows and back out, and the count that drives a repeat. */
class CitationStoreTest {

    private static final PenaltySchedule SCHEDULE = PenaltySchedule.standard();
    private static final LocalDate WRITTEN = LocalDate.of(2025, 4, 8);

    private UnitOfWork work;

    @BeforeEach
    void open() {
        work = Databases.seeded().unitOfWork();
    }

    @AfterEach
    void close() {
        work.close();
    }

    /** The overweight excavator stop, with whatever the test changes. */
    private static Citation.Builder aCitation(String number, LocalDate written, Offence offence, int prior) {
        Optional<Pounds> over = offence.byWeight() ? Optional.of(new Pounds(12000)) : Optional.empty();
        return Citation.builder()
                .number(number)
                .written(written)
                .filed(written)
                .officer("Sgt A Thibodeau")
                .usdot(1204471)
                .carrierName("Bickford Heavy Haul")
                .offence(offence)
                .place("US201-2 at Fairfield")
                .assessed(Penalty.assess(SCHEDULE, offence, over, prior))
                .over(over.orElse(null));
    }

    private static Citation.Builder aCitation(String number, LocalDate written) {
        return aCitation(number, written, Offence.OVERWEIGHT, 0);
    }

    private static Citation.Builder aCitation() {
        return aCitation("E-25-0417", WRITTEN);
    }

    private Citation filed(Citation.Builder built) {
        Citation one = built.build();
        work.citations().add(one, SCHEDULE.effective());
        work.flush();
        return one;
    }

    private CitationRepository citations() {
        return work.citations();
    }

    @Test
    void aCitationGoesInAndComesBackTheSameCitation() {
        Citation one = filed(aCitation());
        assertThat(citations().get(one.number())).isEqualTo(one);
    }

    @Test
    void theMoneySurvivesTheRoundTripExactly() {
        filed(aCitation());
        Citation found = citations().get("E-25-0417");
        assertThat(found.amount()).isEqualTo(Dollars.of(580));
        assertThat(found.assessed().base()).isEqualTo(Dollars.of(580));
        assertThat(found.assessed().multiplier()).isEqualTo(1);
    }

    @Test
    void theOverageSurvivesTheRoundTrip() {
        filed(aCitation());
        assertThat(citations().get("E-25-0417").over()).contains(new Pounds(12000));
    }

    @Test
    void aFlatOffenceComesBackWithNoOverage() {
        filed(aCitation("E-25-0418", WRITTEN, Offence.NO_PERMIT, 0));
        assertThat(citations().get("E-25-0418").over()).isEmpty();
    }

    @Test
    void theSameNumberIsNotFiledTwice() {
        filed(aCitation());
        assertThatThrownBy(() -> citations().add(aCitation().build(), SCHEDULE.effective()))
                .isInstanceOf(Conflict.class);
    }

    @Test
    void aCitationNobodyHasIsARefusalThatNamesIt() {
        assertThatThrownBy(() -> citations().get("E-25-9999")).isInstanceOf(NotOnFile.class);
    }

    @Test
    void aCitationCanNameThePermitItWasWrittenUnder() {
        Application application = Applications.anApplication();
        work.applications().add(application);
        work.flush();
        Permit permit = Permits.aPermit(application);
        work.permits().add(permit);
        work.flush();
        filed(aCitation().permit(permit.number().toString()));
        assertThat(citations().get("E-25-0417").permit()).contains("25-P-004471");
    }

    @Test
    void theStateIsWrittenBackWithTheReasonWhenItCloses() {
        Citation one = filed(aCitation());
        one.contest(LocalDate.of(2025, 4, 20), "the scale was not certified");
        one.dismiss(LocalDate.of(2025, 5, 2), "scale certificate had lapsed");
        citations().saveState(one, "scale certificate had lapsed");
        work.flush();
        CitationRow row = citations().row("E-25-0417");
        assertThat(row.getState()).isSameAs(CitationState.DISMISSED);
        assertThat(row.getClosingReason()).isEqualTo("scale certificate had lapsed");
    }

    @Test
    void aStateWrittenBackWithoutAReasonLeavesTheOldOne() {
        Citation one = filed(aCitation());
        citations().saveState(one, "first reason");
        one.pay(LocalDate.of(2025, 4, 22));
        citations().saveState(one);
        assertThat(citations().row("E-25-0417").getClosingReason()).isEqualTo("first reason");
    }

    @Test
    void everythingAgainstOneCarrierComesBackNewestFirst() {
        filed(aCitation("E-25-0417", LocalDate.of(2025, 4, 8)));
        filed(aCitation("E-25-0500", LocalDate.of(2025, 6, 2)));
        assertThat(citations().forCarrier(1204471))
                .extracting(CitationRow::getNumber)
                .containsExactly("E-25-0500", "E-25-0417");
    }

    @Test
    void aCarrierWithNothingAgainstItComesBackEmpty() {
        assertThat(citations().forCarrier(2299001)).isEmpty();
    }

    @Test
    void theChaseListIsEverythingStillOwingOldestFirst() {
        filed(aCitation("E-25-0500", LocalDate.of(2025, 6, 2)));
        Citation one = filed(aCitation());
        one.pay(LocalDate.of(2025, 4, 22));
        citations().saveState(one);
        work.flush();
        assertThat(citations().owing()).extracting(CitationRow::getNumber).containsExactly("E-25-0500");
    }

    @Test
    void aContestedCitationIsStillOnTheChaseList() {
        Citation one = filed(aCitation());
        one.contest(LocalDate.of(2025, 4, 20), "grounds");
        citations().saveState(one);
        work.flush();
        assertThat(citations().owing()).extracting(CitationRow::getNumber).containsExactly("E-25-0417");
    }

    @Test
    void theCitationsOfAWindowComeBackInOrder() {
        filed(aCitation("E-25-0500", LocalDate.of(2025, 6, 2)));
        filed(aCitation("E-25-0417", LocalDate.of(2025, 4, 8)));
        assertThat(citations().writtenBetween(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 5, 1)))
                .extracting(CitationRow::getNumber)
                .containsExactly("E-25-0417");
    }

    @Test
    void aCarrierWithNoHistoryHasNoPriors() {
        assertThat(citations().priors(1204471, WRITTEN)).isZero();
    }

    @Test
    void aCitationWrittenEarlierInTheYearIsAPrior() {
        filed(aCitation("E-25-0100", LocalDate.of(2025, 1, 20)));
        assertThat(citations().priors(1204471, WRITTEN)).isEqualTo(1);
    }

    @Test
    void aCitationWrittenTheSameDayIsNotAPriorAgainstItself() {
        filed(aCitation());
        assertThat(citations().priors(1204471, WRITTEN)).isZero();
    }

    @Test
    void aCitationOlderThanTheWindowIsNotAPrior() {
        filed(aCitation("E-24-0100", LocalDate.of(2024, 1, 20)));
        assertThat(citations().priors(1204471, WRITTEN)).isZero();
    }

    @Test
    void aDismissedCitationIsNotAPriorOffence() {
        Citation one = filed(aCitation("E-25-0100", LocalDate.of(2025, 1, 20)));
        one.contest(LocalDate.of(2025, 2, 1), "grounds");
        one.dismiss(LocalDate.of(2025, 2, 20), "scale");
        citations().saveState(one);
        work.flush();
        assertThat(citations().priors(1204471, WRITTEN)).isZero();
    }

    @Test
    void aVoidedCitationIsNotAPriorOffence() {
        Citation one = filed(aCitation("E-25-0100", LocalDate.of(2025, 1, 20)));
        one.makeVoid(LocalDate.of(2025, 1, 25), "wrong usdot");
        citations().saveState(one);
        work.flush();
        assertThat(citations().priors(1204471, WRITTEN)).isZero();
    }

    @Test
    void aContestedCitationThatHasNotBeenHeardStillCounts() {
        Citation one = filed(aCitation("E-25-0100", LocalDate.of(2025, 1, 20)));
        one.contest(LocalDate.of(2025, 2, 1), "grounds");
        citations().saveState(one);
        work.flush();
        assertThat(citations().priors(1204471, WRITTEN)).isEqualTo(1);
    }

    @Test
    void anotherCarriersHistoryIsNotThisCarriers() {
        filed(aCitation("E-25-0100", LocalDate.of(2025, 1, 20)).usdot(2299001).carrierName("Aroostook Crane Service"));
        assertThat(citations().priors(1204471, WRITTEN)).isZero();
        assertThat(citations().priors(2299001, WRITTEN)).isEqualTo(1);
    }

    @Test
    void theWindowCanBeAskedForShorter() {
        filed(aCitation("E-25-0100", LocalDate.of(2025, 1, 20)));
        assertThat(citations().priors(1204471, WRITTEN, 30)).isZero();
    }

    @Test
    void theRepositoryCountsWhatItHolds() {
        filed(aCitation());
        assertThat(citations().count()).isEqualTo(1L);
    }

    @Test
    void theRowSaysWhatItIsWhenSomethingPrintsIt() {
        filed(aCitation());
        assertThat(work.entityManager().find(CitationRow.class, "E-25-0417")).hasToString("<CitationRow E-25-0417>");
    }
}
