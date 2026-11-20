package io.github.rkaushikdev.jeepdolly.enforcement;

import io.github.rkaushikdev.jeepdolly.Dollars;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** What one carrier owes on citations, and whether any of it holds them. */
public record Account(int usdot, List<Owed> unpaid) {

    public Account {
        unpaid = List.copyOf(unpaid);
    }

    /** A carrier that owes nothing, which is the ordinary case. */
    public Account(int usdot) {
        this(usdot, List.of());
    }

    /** Everything still unpaid, added up. */
    public Dollars owing() {
        Dollars found = Dollars.of(0);
        for (Owed one : unpaid) {
            found = found.plus(one.amount());
        }
        return found;
    }

    /** The citations that hold the carrier on that day. */
    public List<Owed> pastDue(LocalDate day) {
        return unpaid.stream().filter(one -> one.pastDueOn(day)).toList();
    }

    /** The refusal line, or empty when the carrier is clear to load. */
    public Optional<String> hold(LocalDate day) {
        List<Owed> found = pastDue(day);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Owed oldest = found.stream().min(Comparator.comparing(Owed::due)).orElseThrow();
        return Optional.of("citation " + oldest.number() + " has been due since " + oldest.due() + " and "
                + found.size() + " unpaid citations hold this carrier");
    }

    @Override
    public String toString() {
        if (unpaid.isEmpty()) {
            return usdot + " owes nothing";
        }
        return usdot + " owes " + owing() + " on " + unpaid.size() + " citations";
    }
}
