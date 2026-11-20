package io.github.rkaushikdev.jeepdolly.routing;

import io.github.rkaushikdev.jeepdolly.SeasonBook;
import io.github.rkaushikdev.jeepdolly.domain.HighwayClass;
import io.github.rkaushikdev.jeepdolly.domain.Network;
import io.github.rkaushikdev.jeepdolly.domain.RestrictionBook;
import io.github.rkaushikdev.jeepdolly.domain.Segment;
import io.github.rkaushikdev.jeepdolly.limits.StatutoryBook;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;

/**
 * Finding a route for a load, rather than checking the one it was handed.
 *
 * <p>Haulers write their own route on the application and most of the time it is fine. When it is
 * not, the counter needs an answer better than no, and that answer is the shortest route this load
 * can actually take today.
 *
 * <p>The search is an ordinary shortest path with two differences. A segment is only usable if
 * nothing at all is found on it for this load on this day, so the clearance, the posting, the
 * closure and the class of road are all read before the segment is added to the frontier: routing
 * is the restriction reader run over a graph instead of over a list. And cost is not distance: it
 * is distance with a penalty per mile for the lesser roads, because a route that saves two miles by
 * sending a hundred and forty thousand pounds through a village is not a route the office wants to
 * have signed.
 *
 * <p>Ties are broken by place and then segment ident, so the same request gives the same route
 * twice. The state of the search is a place and the road it was reached on, not a place on its
 * own, because whether a load can leave Skowhegan on US 2 depends on which road it came in by.
 */
public final class Select {

    /** Extra hundredths of a mile charged per hundredth on the lesser roads. */
    public static final Map<HighwayClass, Integer> PENALTY = penalties();

    /** The frontier's order: cost, then place, then the road arrived on, as the original's heap. */
    private static final Comparator<Entry> FRONTIER =
            Comparator.comparingInt(Entry::cost).thenComparing(Entry::here).thenComparing(Entry::arrived);

    private Select() {}

    /** A place and the segment the load arrived on, which is empty at the origin. */
    private record State(String place, String arrived) {}

    /** How a state was reached: the state before it and the segment driven. */
    private record Step(State before, String ident) {}

    /** One entry on the frontier. */
    private record Entry(int cost, String here, String arrived) {}

    private static Map<HighwayClass, Integer> penalties() {
        Map<HighwayClass, Integer> table = new EnumMap<>(HighwayClass.class);
        table.put(HighwayClass.INTERSTATE, 0);
        table.put(HighwayClass.ARTERIAL, 0);
        table.put(HighwayClass.STATE_AID, 1);
        table.put(HighwayClass.TOWN_WAY, 4);
        return Collections.unmodifiableMap(table);
    }

    /** The cheapest route this load can take today with no corner card index, if there is one. */
    public static Optional<Itinerary> find(
            Network network,
            String origin,
            String destination,
            Search search,
            RestrictionBook restrictions,
            SeasonBook seasons,
            StatutoryBook statutes) {
        return find(network, origin, destination, search, restrictions, seasons, statutes, Optional.empty());
    }

    /** The cheapest route this load can take today, reading the corners off the card index. */
    public static Optional<Itinerary> find(
            Network network,
            String origin,
            String destination,
            Search search,
            RestrictionBook restrictions,
            SeasonBook seasons,
            StatutoryBook statutes,
            TurnBook turns) {
        return find(network, origin, destination, search, restrictions, seasons, statutes, Optional.of(turns));
    }

    /**
     * The cheapest route this load can take today, or empty if there is none or it is already there.
     *
     * @throws io.github.rkaushikdev.jeepdolly.NotOnFile when either end is not on the road log, before
     *     the search starts
     */
    public static Optional<Itinerary> find(
            Network network,
            String origin,
            String destination,
            Search search,
            RestrictionBook restrictions,
            SeasonBook seasons,
            StatutoryBook statutes,
            Optional<TurnBook> turns) {
        network.node(origin);
        network.node(destination);
        if (origin.equals(destination)) {
            return Optional.empty();
        }
        State start = new State(origin, "");
        Set<State> seen = new HashSet<>();
        Map<State, Integer> best = new HashMap<>();
        best.put(start, 0);
        Map<State, Step> came = new HashMap<>();
        PriorityQueue<Entry> frontier = new PriorityQueue<>(FRONTIER);
        frontier.add(new Entry(0, origin, ""));
        while (!frontier.isEmpty()) {
            Entry popped = frontier.poll();
            String here = popped.here();
            State standing = new State(here, popped.arrived());
            if (!seen.add(standing)) {
                continue;
            }
            if (here.equals(destination)) {
                return Optional.of(rebuild(network, origin, standing, came));
            }
            for (Segment segment : network.leaving(here)) {
                String beyond = segment.otherEnd(here);
                State onward = new State(beyond, segment.ident());
                if (seen.contains(onward)
                        || wontTurn(here, popped.arrived(), segment.ident(), search, turns)
                        || blocked(segment, search, restrictions, seasons, statutes)) {
                    continue;
                }
                int through = popped.cost() + cost(segment, search.penalties());
                Integer known = best.get(onward);
                if (known == null || through < known) {
                    best.put(onward, through);
                    came.put(onward, new Step(standing, segment.ident()));
                    frontier.add(new Entry(through, beyond, segment.ident()));
                }
            }
        }
        return Optional.empty();
    }

    /** Every place this load can get to from here today, with no corner card index. */
    public static List<String> reachable(
            Network network,
            String origin,
            Search search,
            RestrictionBook restrictions,
            SeasonBook seasons,
            StatutoryBook statutes) {
        return reachable(network, origin, search, restrictions, seasons, statutes, Optional.empty());
    }

    /** Every place this load can get to from here today, reading the corners off the card index. */
    public static List<String> reachable(
            Network network,
            String origin,
            Search search,
            RestrictionBook restrictions,
            SeasonBook seasons,
            StatutoryBook statutes,
            TurnBook turns) {
        return reachable(network, origin, search, restrictions, seasons, statutes, Optional.of(turns));
    }

    /**
     * Every place this load can get to from here today, sorted.
     *
     * <p>The counter uses this the other way round from the router: a hauler asks whether a mill is
     * reachable at all before anybody argues about the route. It walks the same states the router
     * does, for the same reason: a place reachable only through a corner the load will not get round
     * is not a place the load can get to, however many roads run into it.
     */
    public static List<String> reachable(
            Network network,
            String origin,
            Search search,
            RestrictionBook restrictions,
            SeasonBook seasons,
            StatutoryBook statutes,
            Optional<TurnBook> turns) {
        State start = new State(origin, "");
        TreeSet<String> found = new TreeSet<>();
        found.add(origin);
        Set<State> seen = new HashSet<>();
        seen.add(start);
        Deque<State> frontier = new ArrayDeque<>();
        frontier.push(start);
        while (!frontier.isEmpty()) {
            State standing = frontier.pop();
            String here = standing.place();
            for (Segment segment : network.leaving(here)) {
                String beyond = segment.otherEnd(here);
                State onward = new State(beyond, segment.ident());
                if (seen.contains(onward)
                        || wontTurn(here, standing.arrived(), segment.ident(), search, turns)
                        || blocked(segment, search, restrictions, seasons, statutes)) {
                    continue;
                }
                seen.add(onward);
                found.add(beyond);
                frontier.push(onward);
            }
        }
        return List.copyOf(found);
    }

    /** Anything found on the segment at all, a condition included, keeps the router off it. */
    private static boolean blocked(
            Segment segment, Search search, RestrictionBook restrictions, SeasonBook seasons, StatutoryBook statutes) {
        return !Evaluate.segmentObstructions(
                        segment,
                        search.envelope(),
                        search.gross(),
                        search.day(),
                        restrictions,
                        seasons,
                        statutes,
                        search.departure())
                .isEmpty();
    }

    /**
     * Whether this load cannot get from one road onto the other at this place.
     *
     * <p>A corner nobody measured is not in the way. That is deliberate: the card index covers the
     * corners somebody has had trouble at, and the office does not refuse a route because a junction
     * is missing from it.
     */
    private static boolean wontTurn(String node, String arrived, String onto, Search search, Optional<TurnBook> turns) {
        if (turns.isEmpty() || arrived.isEmpty()) {
            return false;
        }
        return turns.get()
                .between(node, arrived, onto)
                .filter(corner -> corner.radiusFeet() < Sweep.radiusWanted(search.envelope()))
                .isPresent();
    }

    private static int cost(Segment segment, Map<HighwayClass, Integer> penalties) {
        return segment.length().hundredths() * (1 + penalties.getOrDefault(segment.highwayClass(), 0));
    }

    private static Itinerary rebuild(Network network, String origin, State arrived, Map<State, Step> came) {
        List<String> idents = new ArrayList<>();
        State standing = arrived;
        while (!standing.arrived().isEmpty()) {
            Step step = came.get(standing);
            idents.add(step.ident());
            standing = step.before();
        }
        Collections.reverse(idents);
        return new Itinerary(network, origin, idents);
    }
}
