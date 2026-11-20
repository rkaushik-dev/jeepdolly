package io.github.rkaushikdev.jeepdolly.utilities;

import java.util.List;

/** What one load meets overhead on one route: the lifts to arrange and the wires in the way. */
public record LineReading(List<Lift> lifts, List<BlockedLine> blocked) {

    public LineReading {
        lifts = List.copyOf(lifts);
        blocked = List.copyOf(blocked);
    }

    /** Whether nothing overhead refuses the load. A lift to arrange is not a refusal. */
    public boolean clear() {
        return blocked.isEmpty();
    }

    /** Every wire in the way, as the refusal letter writes it. */
    public List<String> refusals() {
        return blocked.stream().map(BlockedLine::toString).toList();
    }

    @Override
    public String toString() {
        if (!blocked.isEmpty()) {
            return blocked.size() + " lines in the way";
        }
        if (!lifts.isEmpty()) {
            return lifts.size() + " lifts to arrange";
        }
        return "nothing overhead";
    }
}
