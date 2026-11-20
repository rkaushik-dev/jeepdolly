package io.github.rkaushikdev.jeepdolly.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.domain.RestrictionKind;
import io.github.rkaushikdev.jeepdolly.persistence.Database;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.persistence.models.RestrictionRow;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The road calendar, which says what changed rather than what is. */
class CalendarReportTest {

    private static final LocalDate JUNE = LocalDate.of(2025, 6, 1);

    private Database factory;

    @BeforeEach
    void seed() {
        factory = Databases.seeded();
    }

    private void add(String ident, String segment, RestrictionKind kind, String note, Consumer<RestrictionRow> more) {
        RestrictionRow row = new RestrictionRow(ident, segment, kind, note);
        more.accept(row);
        try (UnitOfWork work = factory.unitOfWork()) {
            work.add(row);
            work.commit();
        }
    }

    private void closure(LocalDate effective, LocalDate expires) {
        add("R-1", "US2-1", RestrictionKind.CLOSURE, "culvert work", row -> {
            row.setEffective(effective);
            row.setExpires(expires);
        });
    }

    private <T> T read(Function<UnitOfWork, T> question) {
        try (UnitOfWork work = factory.unitOfWork()) {
            return question.apply(work);
        }
    }

    @Test
    void aPermanentRestrictionIsStandingOnAnyDay() {
        add("R-1", "US2-1", RestrictionKind.CLEARANCE, "railroad bridge", row -> row.setHeight(Inches.of(13, 9)));
        List<RoadCalendar.Standing> found = read(work -> RoadCalendar.standingOn(work.entityManager(), JUNE));
        assertThat(found).hasSize(1);
        assertThat(found.get(0).permanent()).isTrue();
        assertThat(found.get(0).limit()).isEqualTo("13 ft 9 in");
        assertThat(found.get(0).toString()).contains("permanent");
    }

    @Test
    void aDatedRestrictionIsOnlyStandingInsideItsDates() {
        closure(LocalDate.of(2025, 6, 10), LocalDate.of(2025, 6, 20));
        try (UnitOfWork work = factory.unitOfWork()) {
            assertThat(RoadCalendar.standingOn(work.entityManager(), JUNE)).isEmpty();
            assertThat(RoadCalendar.standingOn(work.entityManager(), LocalDate.of(2025, 6, 15)))
                    .hasSize(1);
            assertThat(RoadCalendar.standingOn(work.entityManager(), LocalDate.of(2025, 7, 1)))
                    .isEmpty();
        }
    }

    @Test
    void theLimitPrintedIsTheOneThatKindCarries() {
        add("R-2", "US2-1", RestrictionKind.WEIGHT, "bridge", row -> row.setWeight(new Pounds(48000)));
        add("R-3", "US201-1", RestrictionKind.CURFEW, "rush hour", row -> {
            row.setCurfewDays("01234");
            row.setCurfewFrom(LocalTime.of(6, 30));
            row.setCurfewTo(LocalTime.of(8, 30));
        });
        Map<String, String> found = read(work -> RoadCalendar.standingOn(work.entityManager(), JUNE).stream()
                .collect(Collectors.toMap(RoadCalendar.Standing::ident, RoadCalendar.Standing::limit)));
        assertThat(found.get("R-2")).isEqualTo("48,000 lb");
        assertThat(found.get("R-3")).isEqualTo("06:30 to 08:30");
    }

    @Test
    void theStandingListIsInRoadLogOrder() {
        add("R-2", "US201-1", RestrictionKind.CLOSURE, "culvert work", row -> {});
        add("R-1", "SA148-1", RestrictionKind.CLOSURE, "culvert work", row -> {});
        List<RoadCalendar.Standing> found = read(work -> RoadCalendar.standingOn(work.entityManager(), JUNE));
        assertThat(found).extracting(RoadCalendar.Standing::segment).containsExactly("SA148-1", "US201-1");
    }

    @Test
    void theCalendarSaysTheDayAClosureStarts() {
        closure(LocalDate.of(2025, 6, 3), LocalDate.of(2025, 6, 5));
        List<RoadCalendar.Day> days = read(work -> RoadCalendar.calendar(work.entityManager(), JUNE, 7));
        assertThat(days.stream().filter(one -> !one.started().isEmpty()).map(RoadCalendar.Day::day))
                .containsExactly(LocalDate.of(2025, 6, 3));
        assertThat(days.stream().filter(one -> !one.lifted().isEmpty()).map(RoadCalendar.Day::day))
                .containsExactly(LocalDate.of(2025, 6, 6));
    }

    @Test
    void aDayWithNothingHappeningIsQuiet() {
        closure(LocalDate.of(2025, 6, 3), LocalDate.of(2025, 6, 5));
        List<RoadCalendar.Day> days = read(work -> RoadCalendar.calendar(work.entityManager(), JUNE, 7));
        assertThat(days.get(0).quiet()).isTrue();
        assertThat(days.get(2).quiet()).isFalse();
        assertThat(days.get(2).lines().get(0)).startsWith("2025-06-03  closes  US2-1");
    }

    @Test
    void aPermanentRestrictionNeverShowsUpAsAChange() {
        add("R-1", "US2-1", RestrictionKind.CLEARANCE, "arch", row -> row.setHeight(Inches.of(13)));
        List<RoadCalendar.Day> days = read(work -> RoadCalendar.calendar(work.entityManager(), JUNE, 7));
        assertThat(days).allSatisfy(one -> assertThat(one.quiet()).isTrue());
    }

    @Test
    void theChangeListSaysSoWhenNothingChanges() {
        List<String> lines = read(work -> RoadCalendar.changes(work.entityManager(), JUNE, 7));
        assertThat(lines.get(0)).startsWith("road changes 2025-06-01");
        assertThat(lines.get(lines.size() - 1)).isEqualTo("nothing changes");
    }

    @Test
    void theChangeListPrintsBothEndsOfAClosure() {
        closure(LocalDate.of(2025, 6, 3), LocalDate.of(2025, 6, 5));
        List<String> lines = read(work -> RoadCalendar.changes(work.entityManager(), JUNE, 7));
        assertThat(lines).anySatisfy(one -> assertThat(one).contains("closes"));
        assertThat(lines).anySatisfy(one -> assertThat(one).contains("opens"));
    }

    @Test
    void aCalendarOfNoDaysIsRefused() {
        try (UnitOfWork work = factory.unitOfWork()) {
            assertThatThrownBy(() -> RoadCalendar.calendar(work.entityManager(), JUNE, 0))
                    .isInstanceOf(Impossible.class);
        }
    }
}
