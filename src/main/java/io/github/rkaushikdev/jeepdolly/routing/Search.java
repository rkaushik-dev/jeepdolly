package io.github.rkaushikdev.jeepdolly.routing;

import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.domain.Envelope;
import io.github.rkaushikdev.jeepdolly.domain.HighwayClass;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.Optional;

/**
 * Everything the router needs to know about the load and the day.
 *
 * @param departure the time the load leaves, or empty when nobody said
 * @param penalties extra hundredths of a mile charged per hundredth on each class of road; a class
 *     not named costs nothing extra
 */
public record Search(
        Envelope envelope,
        Pounds gross,
        LocalDate day,
        Optional<LocalTime> departure,
        Map<HighwayClass, Integer> penalties) {

    public Search {
        penalties = Map.copyOf(penalties);
    }

    /** A search at the office's standard penalties, leaving at no time in particular. */
    public Search(Envelope envelope, Pounds gross, LocalDate day) {
        this(envelope, gross, day, Optional.empty(), Select.PENALTY);
    }

    public Search withDeparture(LocalTime leaving) {
        return new Search(envelope, gross, day, Optional.of(leaving), penalties);
    }

    public Search withPenalties(Map<HighwayClass, Integer> charged) {
        return new Search(envelope, gross, day, departure, charged);
    }
}
