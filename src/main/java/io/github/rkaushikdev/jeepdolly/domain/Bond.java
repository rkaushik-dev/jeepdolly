package io.github.rkaushikdev.jeepdolly.domain;

import io.github.rkaushikdev.jeepdolly.Dollars;
import io.github.rkaushikdev.jeepdolly.Impossible;
import java.time.LocalDate;

/**
 * A surety bond, held for the heavy end of the work.
 *
 * <p>A bond is only required above a weight, so most carriers do not carry one and the absence of a
 * bond is not by itself a problem.
 */
public record Bond(String number, String surety, Dollars amount, LocalDate effective, LocalDate expires) {

    public Bond {
        if (!expires.isAfter(effective)) {
            throw new Impossible("bond " + number + " expires before it takes effect");
        }
    }

    public boolean inForce(LocalDate day) {
        return !day.isBefore(effective) && !day.isAfter(expires);
    }

    public boolean covers(LocalDate start, LocalDate end) {
        return inForce(start) && inForce(end);
    }

    @Override
    public String toString() {
        return number + " " + surety + " " + amount;
    }
}
