package io.github.rkaushikdev.jeepdolly.escorts;

import io.github.rkaushikdev.jeepdolly.Conflict;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One certified pilot car operator, with the card they hold.
 *
 * <p>A certificate belongs to a person and a person works for a company. Expiry is read against a
 * day, never against today: a permit written in March for a move in June needs an operator whose
 * card is good in June.
 *
 * @param card the certificate number, which is the operator's ident on the register
 * @param company the ident of the company the operator works for
 */
public record Operator(
        String card,
        String name,
        String company,
        LocalDate issued,
        LocalDate expires,
        Set<Endorsement> endorsements,
        boolean suspended) {

    public Operator {
        endorsements = Set.copyOf(endorsements);
        if (!expires.isAfter(issued)) {
            throw new Conflict("card " + card + " expires before it was issued");
        }
    }

    /** An ordinary card in good standing. */
    public Operator(String card, String name, String company, LocalDate issued, LocalDate expires) {
        this(card, name, company, issued, expires, Set.of(), false);
    }

    /** A card in good standing carrying endorsements. */
    public Operator(
            String card,
            String name,
            String company,
            LocalDate issued,
            LocalDate expires,
            Set<Endorsement> endorsements) {
        this(card, name, company, issued, expires, endorsements, false);
    }

    /** Whether the card has run out by that day. A card is good on the day it expires. */
    public boolean expiredOn(LocalDate day) {
        return day.isAfter(expires);
    }

    public boolean carries(Endorsement endorsement) {
        return endorsements.contains(endorsement);
    }

    /** Whether this operator can be named on a permit for that day, the company aside. */
    public Standing standing(LocalDate day) {
        return standing(day, false);
    }

    /** Whether this operator can be named on a permit for that day. */
    public Standing standing(LocalDate day, boolean companySuspended) {
        if (suspended) {
            return Standing.SUSPENDED;
        }
        if (companySuspended) {
            return Standing.COMPANY_SUSPENDED;
        }
        if (expiredOn(day)) {
            return Standing.EXPIRED;
        }
        return Standing.GOOD;
    }

    /** As the register lists it: {@code C-1001  R Dubois  PC-1  to 2026-04-30 [height pole]}. */
    @Override
    public String toString() {
        String marks = endorsements.stream().map(Endorsement::toString).sorted().collect(Collectors.joining(", "));
        String tail = marks.isEmpty() ? "" : " [" + marks + "]";
        return card + "  " + name + "  " + company + "  to " + expires + tail;
    }
}
