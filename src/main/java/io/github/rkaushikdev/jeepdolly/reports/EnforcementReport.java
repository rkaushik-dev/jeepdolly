package io.github.rkaushikdev.jeepdolly.reports;

import io.github.rkaushikdev.jeepdolly.Dollars;
import io.github.rkaushikdev.jeepdolly.enforcement.CitationState;
import io.github.rkaushikdev.jeepdolly.enforcement.Offence;
import io.github.rkaushikdev.jeepdolly.persistence.models.CitationRow;
import io.github.rkaushikdev.jeepdolly.persistence.repositories.CitationRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What was written at the roadside, and what came of it, for three different readers.
 *
 * <p>The legislature wants the count by offence for the year, everything written in the window,
 * dismissed ones included, because a dismissed ticket is still an enforcement action. Accounting
 * wants the money: what was assessed, what was taken, and what is still out, and those three do not
 * add up and are not supposed to. The supervisor wants the carriers with more than one, because that
 * is the list that gets a letter before it gets a suspension.
 *
 * <p>Nothing here decides anything. It counts rows; the deciding is in the citation service and the
 * repeat multiplier.
 */
public final class EnforcementReport {

    /** How many citations in the window put a carrier on the supervisor's list. */
    public static final int WATCH_AT = 2;

    private EnforcementReport() {}

    /** The three amounts, which do not add up and are not meant to. */
    public record Money(Dollars assessed, Dollars taken, Dollars owing) {

        /** What was assessed and will never be taken, which is not a debt. */
        public Dollars writtenOff() {
            return assessed.minus(taken).minus(owing);
        }

        public List<String> lines() {
            return List.of(
                    "assessed  " + assessed,
                    "taken     " + taken,
                    "owing     " + owing,
                    "closed unpaid  " + writtenOff());
        }

        @Override
        public String toString() {
            return assessed + " assessed, " + taken + " taken, " + owing + " out";
        }
    }

    /** One carrier with more than one citation in the window. */
    public record Watched(int usdot, String name, int count, Dollars owing) {

        @Override
        public String toString() {
            return usdot + "  " + name + "  " + count + " citations  " + owing + " out";
        }
    }

    /** Everything written in the window, whatever became of it. */
    public static List<CitationRow> written(EntityManager db, LocalDate start, LocalDate end) {
        return new CitationRepository(db).writtenBetween(start, end);
    }

    /** The count by offence, which is the number the legislature quotes. */
    public static Map<Offence, Integer> byOffence(EntityManager db, LocalDate start, LocalDate end) {
        Map<Offence, Integer> found = new LinkedHashMap<>();
        written(db, start, end).forEach(row -> found.merge(row.getOffence(), 1, Integer::sum));
        return found;
    }

    /** What was assessed, what was taken, and what is still out. */
    public static Money money(EntityManager db, LocalDate start, LocalDate end) {
        Dollars assessed = Dollars.ZERO;
        Dollars taken = Dollars.ZERO;
        Dollars owing = Dollars.ZERO;
        for (CitationRow row : written(db, start, end)) {
            assessed = assessed.plus(row.getAmount());
            if (row.getState() == CitationState.PAID) {
                taken = taken.plus(row.getAmount());
            } else if (row.getState().owing()) {
                owing = owing.plus(row.getAmount());
            }
        }
        return new Money(assessed, taken, owing);
    }

    /** Carriers with at least the usual number of citations in the window. */
    public static List<Watched> watchList(EntityManager db, LocalDate start, LocalDate end) {
        return watchList(db, start, end, WATCH_AT);
    }

    /**
     * Carriers with enough citations in the window to want a letter, the most stopped first.
     *
     * <p>Voided ones are left out and dismissed ones are not. A dismissed citation was still a
     * stop, and the letter is about how often this carrier is being stopped rather than about what
     * stuck.
     */
    public static List<Watched> watchList(EntityManager db, LocalDate start, LocalDate end, int at) {
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        Map<Integer, String> names = new LinkedHashMap<>();
        Map<Integer, Dollars> out = new LinkedHashMap<>();
        for (CitationRow row : written(db, start, end)) {
            if (row.getState() == CitationState.VOID) {
                continue;
            }
            counts.merge(row.getUsdot(), 1, Integer::sum);
            names.put(row.getUsdot(), row.getCarrierName());
            Dollars standing = out.getOrDefault(row.getUsdot(), Dollars.ZERO);
            if (row.getState().owing()) {
                standing = standing.plus(row.getAmount());
            }
            out.put(row.getUsdot(), standing);
        }
        List<Watched> found = new ArrayList<>();
        counts.forEach((usdot, count) -> {
            if (count >= at) {
                found.add(new Watched(usdot, names.get(usdot), count, out.get(usdot)));
            }
        });
        found.sort(Comparator.comparingInt(Watched::count).reversed().thenComparingInt(Watched::usdot));
        return List.copyOf(found);
    }

    /** The whole thing as lines, which is what the command prints. */
    public static List<String> report(EntityManager db, LocalDate start, LocalDate end) {
        List<String> lines = new ArrayList<>();
        lines.add("enforcement " + start + " to " + end);
        Map<Offence, Integer> counts = byOffence(db, start, end);
        if (counts.isEmpty()) {
            lines.add("nothing was written in that window");
            return List.copyOf(lines);
        }
        counts.keySet().stream()
                .sorted(Comparator.comparing(Offence::value))
                .forEach(offence -> lines.add(offence + "  " + counts.get(offence)));
        lines.addAll(money(db, start, end).lines());
        List<Watched> watched = watchList(db, start, end);
        if (!watched.isEmpty()) {
            lines.add("carriers to write to");
            watched.forEach(one -> lines.add("  " + one));
        }
        return List.copyOf(lines);
    }
}
