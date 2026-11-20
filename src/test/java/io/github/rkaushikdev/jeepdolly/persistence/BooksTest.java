package io.github.rkaushikdev.jeepdolly.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.domain.Carrier;
import io.github.rkaushikdev.jeepdolly.domain.CarrierRegister;
import io.github.rkaushikdev.jeepdolly.domain.Network;
import io.github.rkaushikdev.jeepdolly.domain.Restriction;
import io.github.rkaushikdev.jeepdolly.domain.RestrictionBook;
import io.github.rkaushikdev.jeepdolly.domain.RestrictionKind;
import io.github.rkaushikdev.jeepdolly.domain.Segment;
import io.github.rkaushikdev.jeepdolly.persistence.models.RestrictionRow;
import io.github.rkaushikdev.jeepdolly.structures.StructureBook;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Reading the rows back into the books the office decides with. */
class BooksTest {

    private static final LocalDate MARCH = LocalDate.of(2025, 3, 3);

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

    @Test
    void theRoadLogIsReadBackIntoANetwork() {
        Network network = Books.networkFrom(db);
        assertThat(network.size()).isEqualTo(12);
        assertThat(network.segment("US201-2").start()).isEqualTo("fairfield");
        assertThat(network.leaving("skowhegan").get(0).ident()).isEqualTo("SA148-2");
    }

    @Test
    void theStructuresAreReadBackIntoABook() {
        StructureBook book = Books.structuresFrom(db);
        assertThat(book.size()).isEqualTo(6);
        assertThat(book.get("1533").posted()).contains(new Pounds(48000));
    }

    @Test
    void theCarriersAreReadBackWithThePaperInForceThatDay() {
        CarrierRegister register = Books.carriersFrom(db, MARCH);
        assertThat(register.get(1204471).inGoodStanding(MARCH, MARCH)).isTrue();
        assertThat(register.get(2299001).inGoodStanding(MARCH, MARCH)).isFalse();
    }

    @Test
    void anEmptyRestrictionBookIsWhatACleanRoadLooksLike() {
        assertThat(Books.restrictionsFrom(db).size()).isZero();
    }

    @Test
    void aRestrictionRowComesBackAsTheRestrictionItWas() {
        RestrictionRow clearance = new RestrictionRow("R-1", "US2-1", RestrictionKind.CLEARANCE, "railroad bridge");
        clearance.setHeight(Inches.of(13, 9));
        work.add(clearance);
        work.commit();
        List<Restriction> found = Books.restrictionsFrom(db).onSegment("US2-1", MARCH);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).height()).contains(new Inches(165));
        assertThat(found.get(0).note()).isEqualTo("railroad bridge");
    }

    @Test
    void aCurfewRowComesBackAsACurfew() {
        RestrictionRow rush = new RestrictionRow("R-2", "US201-1", RestrictionKind.CURFEW, "rush hour");
        rush.setCurfewDays("01234");
        rush.setCurfewFrom(LocalTime.of(6, 30));
        rush.setCurfewTo(LocalTime.of(8, 30));
        work.add(rush);
        work.commit();
        Restriction found =
                Books.restrictionsFrom(db).onSegment("US201-1", MARCH).get(0);
        assertThat(found.curfew()).isPresent();
        assertThat(found.curfew().get().covers(LocalDate.of(2025, 3, 3), LocalTime.of(7, 0)))
                .isTrue();
        assertThat(found.curfew().get().covers(LocalDate.of(2025, 3, 8), LocalTime.of(7, 0)))
                .isFalse();
    }

    @Test
    void theNetworkComesBackWithEveryPlaceTheLogNames() {
        Network network = Books.networkFrom(db);
        assertThat(network.nodes()).hasSize(9);
        assertThat(network.node("skowhegan").town()).isEqualTo("Skowhegan");
    }

    @Test
    void aSegmentCanBeDrivenFromEitherEndAfterARoundTrip() {
        Segment segment = Books.networkFrom(db).segment("US201-2");
        assertThat(segment.runsFrom("fairfield")).isTrue();
        assertThat(segment.runsFrom("skowhegan")).isTrue();
    }

    @Test
    void aCarrierReadAtADayAfterThePaperLapsesIsNotInGoodStanding() {
        Carrier lapsed = Books.carriersFrom(db, LocalDate.of(2026, 6, 1)).get(1204471);
        assertThat(lapsed.standing(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2), 94000))
                .isPresent();
    }

    @Test
    void theStructureBookCarriesThePostingsAsTheyStand() {
        assertThat(Books.structuresFrom(db).get("1533").posted()).contains(new Pounds(48000));
    }

    @Test
    void readingTheBooksTwiceGivesTwoBooksThatDoNotShareAnything() {
        RestrictionBook first = Books.restrictionsFrom(db);
        RestrictionBook second = Books.restrictionsFrom(db);
        assertThat(first).isNotSameAs(second);
        work.add(new RestrictionRow("R-9", "US2-1", RestrictionKind.CLOSURE, "paving"));
        work.flush();
        assertThat(Books.restrictionsFrom(db).onSegment("US2-1", MARCH)).hasSize(1);
        assertThat(first.onSegment("US2-1", MARCH)).isEmpty();
    }
}
