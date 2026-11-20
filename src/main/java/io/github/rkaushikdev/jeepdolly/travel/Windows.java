package io.github.rkaushikdev.jeepdolly.travel;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.PermitCalendar;
import io.github.rkaushikdev.jeepdolly.Window;
import io.github.rkaushikdev.jeepdolly.domain.Curfew;
import io.github.rkaushikdev.jeepdolly.domain.Envelope;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What is left of a week once every rule about when has been applied.
 *
 * <p>A permit is valid for a span of days and the hauler wants to know which of those days he can
 * actually move on, and between what hours. Four rules answer that and they are read in a fixed
 * order because they knock each other out.
 *
 * <ol>
 *   <li>A weekend restriction closes Saturday and Sunday for the loads that carry it, which is the
 *       superloads.
 *   <li>A daylight restriction cuts the day down to the window in the daylight table. A load that
 *       is not daylight restricted may run through the night.
 *   <li>A holiday ban closes the whole day, or the part of it the ban covers.
 *   <li>A curfew cuts a bite out of what is left. A curfew that swallows the whole remaining window
 *       closes the day, which happens to a daylight only load with an afternoon curfew in December
 *       and surprises everybody the first time.
 * </ol>
 */
public final class Windows {

    /** Past this width a load moves in daylight only. */
    public static final Inches DAYLIGHT_WIDTH = Inches.of(12);

    /** Past this height a load moves in daylight only. */
    public static final Inches DAYLIGHT_HEIGHT = Inches.of(14, 6);

    /** A load with a high centre stops when the wind reaches this. */
    public static final int WIND_HOLD_MPH = 30;

    private static final LocalTime MIDNIGHT = LocalTime.of(0, 0);
    private static final LocalTime LAST_MINUTE = LocalTime.of(23, 59);

    private Windows() {}

    /** The when rules of an ordinary load: none, unless its size puts it in daylight. */
    public static TravelLimits limitsFor(Envelope envelope) {
        return limitsFor(envelope, false, false);
    }

    /** Read the when rules off the load. */
    public static TravelLimits limitsFor(Envelope envelope, boolean superload, boolean highCentre) {
        List<String> reasons = new ArrayList<>();
        boolean daylightOnly = false;
        if (envelope.width().compareTo(DAYLIGHT_WIDTH) > 0) {
            daylightOnly = true;
            reasons.add("width over " + DAYLIGHT_WIDTH);
        }
        if (envelope.height().compareTo(DAYLIGHT_HEIGHT) > 0) {
            daylightOnly = true;
            reasons.add("height over " + DAYLIGHT_HEIGHT);
        }
        if (superload) {
            daylightOnly = true;
            reasons.add("superload");
        }
        return new TravelLimits(
                daylightOnly, superload, highCentre ? Optional.of(WIND_HOLD_MPH) : Optional.empty(), reasons);
    }

    /** What is left of one day with no curfew on the route. */
    public static TravelDay travelDay(LocalDate day, TravelLimits limits, PermitCalendar calendar) {
        return travelDay(day, limits, calendar, Optional.empty());
    }

    /** What is left of one day under a curfew on the route. */
    public static TravelDay travelDay(LocalDate day, TravelLimits limits, PermitCalendar calendar, Curfew curfew) {
        return travelDay(day, limits, calendar, Optional.of(curfew));
    }

    /** What is left of one day once every when rule has been applied. */
    public static TravelDay travelDay(
            LocalDate day, TravelLimits limits, PermitCalendar calendar, Optional<Curfew> curfew) {
        if (limits.noWeekend() && weekend(day)) {
            return TravelDay.closed(day, "no weekend travel");
        }
        LocalTime opens = MIDNIGHT;
        LocalTime closes = LAST_MINUTE;
        if (limits.daylightOnly()) {
            Daylight window = Daylight.on(day);
            opens = window.opens();
            closes = window.closes();
        }
        TravelDay afterBan = banTrim(day, calendar, opens, closes);
        if (!afterBan.open() || curfew.isEmpty() || !curfew.get().days().contains(day.getDayOfWeek())) {
            return afterBan;
        }
        return outsideCurfew(day, afterBan.opens().get(), afterBan.closes().get(), curfew.get());
    }

    private static boolean weekend(LocalDate day) {
        return day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    /** Take the curfew out of the window, keeping the longer remaining piece. */
    private static TravelDay outsideCurfew(LocalDate day, LocalTime opens, LocalTime closes, Curfew curfew) {
        if (curfew.end().isBefore(opens) || curfew.start().isAfter(closes)) {
            return TravelDay.window(day, opens, closes);
        }
        int before = span(opens, curfew.start());
        int after = span(curfew.end(), closes);
        if (before <= 0 && after <= 0) {
            return TravelDay.closed(day, "the curfew covers the whole window");
        }
        if (before >= after) {
            return TravelDay.window(day, opens, curfew.start());
        }
        return TravelDay.window(day, curfew.end(), closes);
    }

    private static int span(LocalTime first, LocalTime second) {
        return (second.getHour() * 60 + second.getMinute()) - (first.getHour() * 60 + first.getMinute());
    }

    /**
     * Cut a holiday ban out of the day, which may leave nothing at all.
     *
     * <p>A ban that starts at noon on the Friday leaves the Friday morning, and haulers do use it. A
     * ban that covers the whole day closes it.
     */
    private static TravelDay banTrim(LocalDate day, PermitCalendar calendar, LocalTime opens, LocalTime closes) {
        LocalDateTime first = LocalDateTime.of(day, opens);
        LocalDateTime last = LocalDateTime.of(day, closes);
        for (Window window : calendar.bans()) {
            if (!window.start().isAfter(first) && !last.isAfter(window.end())) {
                return TravelDay.closed(day, window.reason());
            }
            if (window.covers(last)) {
                return TravelDay.window(day, opens, window.start().toLocalTime());
            }
            if (window.covers(first)) {
                return TravelDay.window(day, window.end().toLocalTime(), closes);
            }
        }
        return TravelDay.window(day, opens, closes);
    }

    /** Every day of a permit span with no curfew on the route, open or closed, in order. */
    public static List<TravelDay> travelDays(
            LocalDate start, LocalDate end, TravelLimits limits, PermitCalendar calendar) {
        return travelDays(start, end, limits, calendar, Optional.empty());
    }

    /**
     * Every day of a permit span, open or closed, in order.
     *
     * @throws Impossible when the span ends before it starts
     */
    public static List<TravelDay> travelDays(
            LocalDate start, LocalDate end, TravelLimits limits, PermitCalendar calendar, Optional<Curfew> curfew) {
        if (end.isBefore(start)) {
            throw new Impossible("a permit span ends after it starts");
        }
        List<TravelDay> found = new ArrayList<>();
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            found.add(travelDay(day, limits, calendar, curfew));
        }
        return List.copyOf(found);
    }

    /** How many of the days the load may move on at all. */
    public static int openDays(List<TravelDay> days) {
        return (int) days.stream().filter(TravelDay::open).count();
    }
}
