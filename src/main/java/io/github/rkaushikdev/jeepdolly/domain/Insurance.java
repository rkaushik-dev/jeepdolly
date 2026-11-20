package io.github.rkaushikdev.jeepdolly.domain;

import io.github.rkaushikdev.jeepdolly.Dollars;
import io.github.rkaushikdev.jeepdolly.Impossible;
import java.time.LocalDate;

/**
 * A certificate of liability insurance as the office holds it.
 *
 * <p>A permit may not be issued for travel that falls outside it. It is not enough for the
 * certificate to be good today: it has to be good on every day the permit is valid for, which is
 * why {@link #covers} takes a span and not a date.
 */
public record Insurance(String policy, String insurer, Dollars liability, LocalDate effective, LocalDate expires) {

    public Insurance {
        if (!expires.isAfter(effective)) {
            throw new Impossible("certificate " + policy + " expires before it takes effect");
        }
        if (liability.cents() <= 0) {
            throw new Impossible("certificate " + policy + " carries no limit");
        }
    }

    public boolean inForce(LocalDate day) {
        return !day.isBefore(effective) && !day.isAfter(expires);
    }

    /** Good for every day of a span, which is what a permit needs. */
    public boolean covers(LocalDate start, LocalDate end) {
        return inForce(start) && inForce(end);
    }

    @Override
    public String toString() {
        return policy + " " + insurer + " to " + expires;
    }
}
