package io.github.rkaushikdev.jeepdolly.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.conditions.Category;
import io.github.rkaushikdev.jeepdolly.conditions.ConditionLibrary;
import io.github.rkaushikdev.jeepdolly.domain.Node;
import io.github.rkaushikdev.jeepdolly.domain.RestrictionKind;
import io.github.rkaushikdev.jeepdolly.persistence.models.ApplicationRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.CarrierRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.ConditionTextRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.LineCrossingRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.LineOwnerRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.NodeRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.RestrictionRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.StructureRow;
import io.github.rkaushikdev.jeepdolly.structures.Structure;
import io.github.rkaushikdev.jeepdolly.structures.StructureKind;
import io.github.rkaushikdev.jeepdolly.support.Applications;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import io.github.rkaushikdev.jeepdolly.utilities.Crossing;
import io.github.rkaushikdev.jeepdolly.utilities.LineOwner;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Holding the published books between requests, and letting go of them. */
class ReferenceCacheTest {

    private static final LocalDate APRIL = LocalDate.of(2025, 4, 8);
    private static final LocalDate JUNE = LocalDate.of(2025, 6, 10);

    private Database factory;
    private final List<FlushListener> watching = new ArrayList<>();

    @BeforeEach
    void seed() {
        factory = Databases.seeded();
    }

    @AfterEach
    void stopWatching() {
        watching.forEach(factory::stopListening);
    }

    private void watch(ReferenceCache cache) {
        watching.add(Reference.watch(factory, cache));
    }

    private static Structure aStructure() {
        return Structure.builder("9001", "Test culvert", "US2-1", StructureKind.CULVERT)
                .spansFeet(List.of(12))
                .built(1970)
                .operatingRating(new Pounds(90000))
                .inventoryRating(new Pounds(70000))
                .build();
    }

    private static Crossing aWire() {
        return new Crossing("L-1", "US201-2", "Fairfield", Inches.of(19), "CMP", Inches.of(26));
    }

    @Test
    void theSlowReadBuildsEveryPublishedBook() {
        try (UnitOfWork work = factory.unitOfWork()) {
            Reference found = Reference.read(work.entityManager(), APRIL);
            assertThat(found.network().segments()).hasSize(12);
            assertThat(found.commodities().size()).isEqualTo(17);
            assertThat(found.day()).isEqualTo(APRIL);
        }
    }

    @Test
    void aConditionRewordedByOrderPrintsTheNewWordsFromItsDayOn() {
        try (UnitOfWork work = factory.unitOfWork()) {
            work.add(new ConditionTextRow(
                    "T-02", LocalDate.of(2025, 5, 1), "travel", "Daylight only, thirty minutes either side.", 20));
            work.add(new ConditionTextRow(
                    "G-04", LocalDate.of(2025, 5, 1), "general", "Report any damage to the office the same day.", 40));
            work.flush();
            ConditionLibrary april = Reference.read(work.entityManager(), APRIL).library();
            ConditionLibrary june = Reference.read(work.entityManager(), JUNE).library();
            assertThat(april.render("T-02").text()).isEqualTo("Daylight hours only, half an hour either side.");
            assertThat(april.contains("G-04")).isFalse();
            assertThat(june.render("T-02").text()).isEqualTo("Daylight only, thirty minutes either side.");
            assertThat(june.render("G-04").category()).isSameAs(Category.GENERAL);
            assertThat(june.size()).isEqualTo(april.size() + 1);
        }
    }

    @Test
    void aBareDatabasePrintsTheShippedWording() {
        try (UnitOfWork work = factory.unitOfWork()) {
            ConditionLibrary found = Reference.libraryFor(work.entityManager(), JUNE);
            assertThat(found.codes()).isEqualTo(ConditionLibrary.standard().codes());
            assertThat(found.render("R-01").text()).isEqualTo("Route as written. Any deviation voids this permit.");
        }
    }

    @Test
    void theFirstAskIsAMissAndTheSecondIsAHit() {
        try (UnitOfWork work = factory.unitOfWork()) {
            ReferenceCache cache = new ReferenceCache();
            Reference first = cache.books(work.entityManager(), APRIL);
            Reference second = cache.books(work.entityManager(), APRIL);
            assertThat(first).isSameAs(second);
            assertThat(List.of(cache.hits(), cache.misses())).containsExactly(1, 1);
        }
    }

    @Test
    void aDifferentDayIsADifferentEntry() {
        try (UnitOfWork work = factory.unitOfWork()) {
            ReferenceCache cache = new ReferenceCache();
            cache.books(work.entityManager(), APRIL);
            cache.books(work.entityManager(), JUNE);
            assertThat(cache.days()).containsExactly(APRIL, JUNE);
            assertThat(cache.size()).isEqualTo(2);
            assertThat(cache.misses()).isEqualTo(2);
        }
    }

    @Test
    void clearingAnEmptyCacheIsNotCounted() {
        ReferenceCache cache = new ReferenceCache();
        cache.clear();
        assertThat(cache.emptied()).isZero();
    }

    @Test
    void clearingAFullOneIs() {
        try (UnitOfWork work = factory.unitOfWork()) {
            ReferenceCache cache = new ReferenceCache();
            cache.books(work.entityManager(), APRIL);
            cache.clear();
            assertThat(cache.emptied()).isEqualTo(1);
            assertThat(cache.size()).isZero();
        }
    }

    @Test
    void aNewPublishedRowIsATouch() {
        try (UnitOfWork work = factory.unitOfWork()) {
            work.add(NodeRow.of(new Node("solon", "Solon")));
            assertThat(Reference.touched(work.entityManager())).isTrue();
        }
    }

    @Test
    void aNewApplicationIsNotATouch() {
        try (UnitOfWork work = factory.unitOfWork()) {
            work.applications().add(Applications.anApplication());
            assertThat(Reference.touched(work.entityManager())).isFalse();
        }
    }

    @Test
    void aCarrierIsNotATouchBecauseCarriersAreReadFresh() {
        try (UnitOfWork work = factory.unitOfWork()) {
            work.add(new CarrierRow(3300111, "New Haulage", "Bangor", "ME"));
            assertThat(Reference.touched(work.entityManager())).isFalse();
        }
    }

    @Test
    void aRestrictionIsNotATouchBecauseClosuresChangeAllDay() {
        try (UnitOfWork work = factory.unitOfWork()) {
            work.add(new RestrictionRow("R-9", "US2-1", RestrictionKind.CLOSURE, "paving"));
            assertThat(Reference.touched(work.entityManager())).isFalse();
        }
    }

    @Test
    void aChangedStructureIsATouch() {
        try (UnitOfWork work = factory.unitOfWork()) {
            work.entityManager().find(StructureRow.class, "1533").setName("Carrabassett Stream bridge");
            assertThat(Reference.touched(work.entityManager())).isTrue();
        }
    }

    @Test
    void aDeletedPublishedRowIsATouch() {
        try (UnitOfWork work = factory.unitOfWork()) {
            work.delete(work.entityManager().find(StructureRow.class, "1533"));
            assertThat(Reference.touched(work.entityManager())).isTrue();
        }
    }

    @Test
    void theWatchedListCoversEveryBookTheCacheHolds() {
        List<String> names =
                Reference.WATCHED.stream().map(Class::getSimpleName).toList();
        assertThat(names).contains("NodeRow", "SegmentRow", "StructureRow", "CommodityRow");
        assertThat(names).contains("LineCrossingRow");
        assertThat(names).doesNotContain("PermitRow");
    }

    @Test
    void aWatchedWriteEmptiesTheCacheOnFlush() {
        ReferenceCache cache = new ReferenceCache();
        watch(cache);
        try (UnitOfWork work = factory.unitOfWork()) {
            cache.books(work.entityManager(), APRIL);
            work.add(StructureRow.of(aStructure()));
            work.flush();
            assertThat(cache.size()).isZero();
            assertThat(cache.emptied()).isEqualTo(1);
        }
    }

    @Test
    void anUnwatchedWriteLeavesTheCacheAlone() {
        ReferenceCache cache = new ReferenceCache();
        watch(cache);
        try (UnitOfWork work = factory.unitOfWork()) {
            cache.books(work.entityManager(), APRIL);
            work.applications().add(Applications.anApplication());
            work.flush();
            assertThat(cache.size()).isEqualTo(1);
            assertThat(cache.emptied()).isZero();
        }
    }

    @Test
    void theOfficeSeesItsOwnEditInsideTheSameTransaction() {
        ReferenceCache cache = new ReferenceCache();
        watch(cache);
        try (UnitOfWork work = factory.unitOfWork()) {
            Reference before = cache.books(work.entityManager(), APRIL);
            assertThat(before.structures().onSegment("US2-1")).hasSize(1);
            work.add(StructureRow.of(aStructure()));
            work.flush();
            Reference after = cache.books(work.entityManager(), APRIL);
            assertThat(after.structures().onSegment("US2-1")).hasSize(2);
        }
    }

    @Test
    void aWireWrittenEmptiesTheCacheToo() {
        ReferenceCache cache = new ReferenceCache();
        watch(cache);
        try (UnitOfWork work = factory.unitOfWork()) {
            work.add(LineOwnerRow.of(new LineOwner("CMP", "Central Maine Power", "207 555 0100", 5)));
            work.flush();
            cache.books(work.entityManager(), APRIL);
            work.add(LineCrossingRow.of(aWire()));
            work.flush();
            assertThat(cache.size()).isZero();
        }
    }

    @Test
    void aFlushThatFailsLeavesTheCacheAlone() {
        ReferenceCache cache = new ReferenceCache();
        watch(cache);
        try (UnitOfWork work = factory.unitOfWork()) {
            cache.books(work.entityManager(), APRIL);
            work.add(LineCrossingRow.of(aWire()));
            assertThatThrownBy(work::flush).isInstanceOf(IntegrityViolation.class);
            assertThat(cache.size()).isEqualTo(1);
        }
    }

    @Test
    void twoCachesOverOneFactoryAreBothEmptied() {
        ReferenceCache first = new ReferenceCache();
        ReferenceCache second = new ReferenceCache();
        watch(first);
        watch(second);
        try (UnitOfWork work = factory.unitOfWork()) {
            first.books(work.entityManager(), APRIL);
            second.books(work.entityManager(), JUNE);
            work.add(StructureRow.of(aStructure()));
            work.flush();
        }
        assertThat(first.size()).isZero();
        assertThat(second.size()).isZero();
    }

    @Test
    void aCarrierWrittenOnAFlushLeavesTheCacheAlone() {
        // The control for the flush hook: a write to a table the cache does not hold must not
        // empty it, or every test above would pass against a hook that empties on any flush.
        ReferenceCache cache = new ReferenceCache();
        watch(cache);
        try (UnitOfWork work = factory.unitOfWork()) {
            cache.books(work.entityManager(), APRIL);
            work.add(new CarrierRow(3300111, "New Haulage", "Bangor", "ME"));
            work.flush();
            assertThat(cache.size()).isEqualTo(1);
            assertThat(cache.emptied()).isZero();
        }
    }

    @Test
    void anApplicationRowReachingTheSessionIsStillNotATouch() {
        try (UnitOfWork work = factory.unitOfWork()) {
            work.add(new ApplicationRow("25-99999"));
            assertThat(Reference.touched(work.entityManager())).isFalse();
        }
    }
}
