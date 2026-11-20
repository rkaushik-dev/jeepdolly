package io.github.rkaushikdev.jeepdolly.structures;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Every structure on one route against one load, in the order they are driven.
 *
 * <p>The order is the order the load reaches them, not the order of the idents and not worst
 * first. A hauler reads the conditions on a permit in the cab as he goes, and a condition about the
 * Kennebec bridge printed after one about a culvert forty miles later is a condition he will miss.
 *
 * <p>One structure refusing is the whole route refused. There is no partial answer and no
 * averaging: a route with nineteen fine structures and one that will not take the load is a route
 * the load does not take.
 */
public record StructureReading(List<StructureFinding> findings) {

    public StructureReading {
        findings = List.copyOf(findings);
    }

    /** The structures that will not take the load, in driven order. */
    public List<StructureFinding> refusals() {
        return findings.stream().filter(one -> one.verdict() == Verdict.REFUSED).toList();
    }

    /** The structures the load crosses only under conditions, in driven order. */
    public List<StructureFinding> conditional() {
        return findings.stream()
                .filter(one -> one.verdict() == Verdict.CONDITIONAL)
                .toList();
    }

    /** Whether no structure on the route refuses the load. */
    public boolean clear() {
        return refusals().isEmpty();
    }

    /** Every crossing condition the route needs, in driven order. */
    public List<String> conditions() {
        List<String> found = new ArrayList<>();
        for (StructureFinding finding : findings) {
            found.addAll(finding.conditions());
        }
        return List.copyOf(found);
    }

    /**
     * The structure with the lowest factor, which is the one to worry about. A structure posted
     * below the load is not read as the lowest factor: it is a refusal of a different kind. When
     * every structure is posted out, the first one driven is the answer.
     */
    public Optional<StructureFinding> worst() {
        List<StructureFinding> rated =
                findings.stream().filter(one -> !one.rating().postedOut()).toList();
        if (rated.isEmpty()) {
            return findings.stream().findFirst();
        }
        return rated.stream().min(Comparator.comparingInt(one -> one.rating().factor()));
    }

    /** The idents of the structures that refuse the load, in driven order. */
    public List<String> structuresInTheWay() {
        return refusals().stream().map(one -> one.structure().ident()).toList();
    }

    /** How many structures were rated. */
    public int size() {
        return findings.size();
    }

    /** The one line the worksheet prints for the whole route. */
    @Override
    public String toString() {
        if (findings.isEmpty()) {
            return "no structures on the route";
        }
        if (!refusals().isEmpty()) {
            return "refused at " + String.join(", ", structuresInTheWay());
        }
        if (!conditional().isEmpty()) {
            return conditional().size() + " structures crossed under conditions";
        }
        return findings.size() + " structures clear";
    }
}
