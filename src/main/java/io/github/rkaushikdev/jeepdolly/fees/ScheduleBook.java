package io.github.rkaushikdev.jeepdolly.fees;

import io.github.rkaushikdev.jeepdolly.Conflict;
import io.github.rkaushikdev.jeepdolly.NotOnFile;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Every fee schedule that has been in force, asked for by day. */
public final class ScheduleBook {

    private final List<FeeSchedule> schedules = new ArrayList<>();

    /** The coded schedule, effective from {@link FeeSchedule#STANDARD_EFFECTIVE}, alone in a book. */
    public static ScheduleBook standard() {
        return standard(FeeSchedule.STANDARD_EFFECTIVE);
    }

    /** The coded schedule, effective from {@code effective}, alone in a book. */
    public static ScheduleBook standard(LocalDate effective) {
        ScheduleBook book = new ScheduleBook();
        book.file(FeeSchedule.standard(effective));
        return book;
    }

    /** @throws Conflict when a schedule already takes effect on the same day */
    public FeeSchedule file(FeeSchedule schedule) {
        for (FeeSchedule existing : schedules) {
            if (existing.effective().equals(schedule.effective())) {
                throw new Conflict("a schedule already takes effect on " + schedule.effective());
            }
        }
        schedules.add(schedule);
        return schedule;
    }

    /**
     * The schedule in force on a day: the latest one that had started.
     *
     * @throws NotOnFile when no schedule had started yet
     */
    public FeeSchedule on(LocalDate day) {
        return schedules.stream()
                .filter(one -> !one.effective().isAfter(day))
                .max(Comparator.comparing(FeeSchedule::effective))
                .orElseThrow(() -> new NotOnFile("no fee schedule in force on " + day));
    }

    /** How many schedules are on file. */
    public int size() {
        return schedules.size();
    }
}
