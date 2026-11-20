package io.github.rkaushikdev.jeepdolly.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.jeepdolly.domain.RestrictionKind;
import io.github.rkaushikdev.jeepdolly.permits.Assessment;
import io.github.rkaushikdev.jeepdolly.permits.Assessor;
import io.github.rkaushikdev.jeepdolly.permits.Office;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.persistence.models.RestrictionRow;
import io.github.rkaushikdev.jeepdolly.support.Applications;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Building the office out of what is on file, and choosing what goes in it. */
class DepsTest {

    private static final LocalDate APRIL = LocalDate.of(2025, 4, 8);

    private UnitOfWork work;

    @BeforeEach
    void open() {
        work = Databases.seeded().unitOfWork();
    }

    @AfterEach
    void close() {
        work.close();
    }

    @Test
    void theWholeOfficeCanBeBuiltAndCanDecideSomething() {
        Office office = Deps.officeFrom(work, APRIL);
        Assessment found = Assessor.assess(office, Applications.anApplication(), APRIL);
        assertThat(found.permittable()).isTrue();
        assertThat(found.itinerary()).isPresent();
        assertThat(found.quote()).isPresent();
    }

    @Test
    void theOfficeCarriesTheTablesThatAreNotInTheDatabaseYet() {
        Office office = Deps.officeFrom(work, APRIL);
        assertThat(office.commodities().size()).isEqualTo(17);
        assertThat(office.schedules().size()).isEqualTo(1);
        assertThat(office.library().size()).isEqualTo(35);
        assertThat(office.numbering().issuedIn(2025)).isZero();
    }

    @Test
    void aClosureWrittenTodayIsInTheOfficeBuiltNext() {
        work.add(new RestrictionRow("R-3", "I95-2", RestrictionKind.CLOSURE, "bridge out"));
        work.commit();
        Office office = Deps.officeFrom(work, APRIL);
        Assessment found = Assessor.assess(office, Applications.anApplication(), APRIL);
        assertThat(found.itinerary()).isPresent();
        assertThat(found.itinerary().orElseThrow().idents()).doesNotContain("I95-2");
    }
}
