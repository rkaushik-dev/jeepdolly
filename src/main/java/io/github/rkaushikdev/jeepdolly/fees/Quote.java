package io.github.rkaushikdev.jeepdolly.fees;

import io.github.rkaushikdev.jeepdolly.Dollars;
import java.util.List;
import java.util.Optional;

/**
 * What a permit costs and what each part of it was for.
 *
 * <p>The lines come out in a fixed order: base, mileage by class, weight, extras. That order is the
 * order they are printed on the receipt and the order they appear in the nightly revenue report.
 *
 * @param schedule the schedule it was priced off, as {@link FeeSchedule#toString} names it
 */
public record Quote(List<FeeLine> lines, String schedule) {

    public Quote {
        lines = List.copyOf(lines);
    }

    public Dollars total() {
        return Dollars.total(lines.stream().map(FeeLine::amount).toList());
    }

    /** Whether the permit was written up to the minimum fee. */
    public boolean minimumApplied() {
        return lines.stream().anyMatch(line -> line.description().startsWith("minimum"));
    }

    /** The first line with exactly this description, if there is one. */
    public Optional<FeeLine> line(String description) {
        return lines.stream()
                .filter(line -> line.description().equals(description))
                .findFirst();
    }

    /** How many lines the quote has. */
    public int size() {
        return lines.size();
    }

    @Override
    public String toString() {
        return lines.size() + " lines, " + total();
    }
}
