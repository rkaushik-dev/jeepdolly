package io.github.rkaushikdev.jeepdolly.routing;

import io.github.rkaushikdev.jeepdolly.SeasonBook;
import io.github.rkaushikdev.jeepdolly.domain.Network;
import io.github.rkaushikdev.jeepdolly.domain.RestrictionBook;
import io.github.rkaushikdev.jeepdolly.limits.StatutoryBook;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Repairing a route the hauler wrote, instead of throwing it away.
 *
 * <p>When a route fails, the counter has two answers. One is the shortest route the load can take,
 * which the router already gives and which sometimes bears no resemblance to the trip the hauler
 * planned. The other is the route he wrote with the broken part replaced, which is usually what he
 * wants, because he knows where the yard entrance is and the router does not.
 *
 * <p>So a repair keeps the itinerary up to the last place the load could still get to, and searches
 * from there to the same destination. A repair fails in two ways worth telling apart: there may be
 * no route at all, or the only route found may be the one already written. Both come back as
 * nothing, and the caller reads the reading rather than guessing.
 */
public final class Detour {

    private Detour() {}

    /** The index of the first leg this load cannot drive, if there is one. */
    public static OptionalInt firstTrouble(
            Itinerary itinerary,
            Search search,
            RestrictionBook restrictions,
            SeasonBook seasons,
            StatutoryBook statutes) {
        List<Leg> legs = itinerary.legs();
        for (int index = 0; index < legs.size(); index++) {
            boolean fatal = Evaluate.segmentObstructions(
                            legs.get(index).segment(),
                            search.envelope(),
                            search.gross(),
                            search.day(),
                            restrictions,
                            seasons,
                            statutes,
                            search.departure())
                    .stream()
                    .anyMatch(Obstruction::fatal);
            if (fatal) {
                return OptionalInt.of(index);
            }
        }
        return OptionalInt.empty();
    }

    /** Keep the good part of a route and search from where it stops, with no corner card index. */
    public static Optional<Repair> repair(
            Network network,
            Itinerary itinerary,
            Search search,
            RestrictionBook restrictions,
            SeasonBook seasons,
            StatutoryBook statutes) {
        return repair(network, itinerary, search, restrictions, seasons, statutes, Optional.empty());
    }

    /** Keep the good part of a route and search from where it stops, reading the corners. */
    public static Optional<Repair> repair(
            Network network,
            Itinerary itinerary,
            Search search,
            RestrictionBook restrictions,
            SeasonBook seasons,
            StatutoryBook statutes,
            TurnBook turns) {
        return repair(network, itinerary, search, restrictions, seasons, statutes, Optional.of(turns));
    }

    /**
     * Keep the good part of a route and search from where it stops.
     *
     * <p>The search starts at the first leg the load cannot drive and backs up a leg at a time until
     * the kept part and the new part join without driving any piece of road twice.
     *
     * @return the repair, or empty when nothing is wrong, nothing can be found, or the only route
     *     found is the one already written
     */
    public static Optional<Repair> repair(
            Network network,
            Itinerary itinerary,
            Search search,
            RestrictionBook restrictions,
            SeasonBook seasons,
            StatutoryBook statutes,
            Optional<TurnBook> turns) {
        OptionalInt trouble = firstTrouble(itinerary, search, restrictions, seasons, statutes);
        if (trouble.isEmpty()) {
            return Optional.empty();
        }
        List<String> written = itinerary.idents();
        for (int cut = trouble.getAsInt(); cut >= 0; cut--) {
            Optional<Itinerary> onward = Select.find(
                    network,
                    itinerary.legs().get(cut).enteredAt(),
                    itinerary.destination(),
                    search,
                    restrictions,
                    seasons,
                    statutes,
                    turns);
            if (onward.isEmpty()) {
                continue;
            }
            List<String> joined = new ArrayList<>(written.subList(0, cut));
            joined.addAll(onward.get().idents());
            if (new HashSet<>(joined).size() != joined.size()) {
                // Backing up to here would drive a piece of road twice, which is not a route
                // anybody would sign. Give up more of the written one.
                continue;
            }
            Itinerary repaired = new Itinerary(network, itinerary.origin(), joined);
            if (repaired.idents().equals(written)) {
                return Optional.empty();
            }
            List<String> dropped = written.stream()
                    .filter(one -> !repaired.idents().contains(one))
                    .toList();
            List<String> added = repaired.idents().stream()
                    .filter(one -> !written.contains(one))
                    .toList();
            return Optional.of(new Repair(itinerary, repaired, dropped, added));
        }
        return Optional.empty();
    }
}
