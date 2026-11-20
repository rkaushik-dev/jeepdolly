package io.github.rkaushikdev.jeepdolly.support;

import io.github.rkaushikdev.jeepdolly.Miles;
import io.github.rkaushikdev.jeepdolly.domain.Jurisdiction;
import io.github.rkaushikdev.jeepdolly.domain.Network;
import io.github.rkaushikdev.jeepdolly.domain.Node;
import io.github.rkaushikdev.jeepdolly.domain.Route;
import io.github.rkaushikdev.jeepdolly.domain.Segment;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The test road log: nine places in the middle of the state with twelve segments between them.
 *
 * <p>That is enough to have two ways round most things and one way into Anson. It is not a map of
 * anywhere. It is a road log small enough that a person can check a route by hand and big enough
 * that the router has to work.
 */
public final class RoadLog {

    /** The county and district of one place in the test network. */
    public record Place(String county, int district) {}

    /** One segment of the test network, with its length in hundredths of a mile. */
    public record Road(String ident, String route, String start, String end, int hundredths, boolean oneWay) {}

    /** Town, county and district for each place in the test network, in road log order. */
    public static final Map<String, Place> PLACES = places();

    /** Ident, route, start, end, hundredths of a mile, one way. */
    public static final List<Road> ROADS = List.of(
            new Road("I95-1", "I-95", "waterville", "fairfield", 460, false),
            new Road("I95-2", "I-95", "fairfield", "pittsfield", 1420, false),
            new Road("US201-1", "US 201", "waterville", "fairfield", 510, false),
            new Road("US201-2", "US 201", "fairfield", "skowhegan", 1240, false),
            new Road("US201-3", "US 201", "skowhegan", "anson", 870, false),
            new Road("US2-1", "US 2", "skowhegan", "norridgewock", 620, false),
            new Road("US2-2", "US 2", "pittsfield", "newport", 890, false),
            new Road("SR8-1", "SR 8", "norridgewock", "madison", 730, false),
            new Road("SA148-1", "SA 148", "norridgewock", "canaan", 940, false),
            new Road("SA148-2", "SA 148", "canaan", "skowhegan", 760, false),
            new Road("SR43-1", "SR 43", "canaan", "pittsfield", 1010, false),
            new Road("TR104-1", "TR 104", "madison", "anson", 320, false));

    private RoadLog() {}

    private static Map<String, Place> places() {
        Map<String, Place> found = new LinkedHashMap<>();
        found.put("waterville", new Place("Kennebec", 3));
        found.put("fairfield", new Place("Somerset", 3));
        found.put("skowhegan", new Place("Somerset", 4));
        found.put("norridgewock", new Place("Somerset", 4));
        found.put("madison", new Place("Somerset", 4));
        found.put("anson", new Place("Somerset", 4));
        found.put("canaan", new Place("Somerset", 4));
        found.put("pittsfield", new Place("Somerset", 3));
        found.put("newport", new Place("Penobscot", 3));
        return Collections.unmodifiableMap(found);
    }

    /** A place name the way a road sign capitalises it. */
    public static String title(String name) {
        return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
    }

    /** The jurisdiction of one place in the test network. */
    public static Jurisdiction place(String name) {
        Place found = PLACES.get(name);
        return new Jurisdiction(found.county(), title(name), found.district());
    }

    /** Nine places, twelve segments, two ways round most things. */
    public static Network roadLog() {
        Network network = new Network();
        for (String name : PLACES.keySet()) {
            network.addNode(new Node(name, title(name)));
        }
        for (Road road : ROADS) {
            network.addSegment(new Segment(
                    road.ident(),
                    Route.parse(road.route()),
                    road.start(),
                    road.end(),
                    new Miles(road.hundredths()),
                    place(road.start()),
                    road.oneWay()));
        }
        return network;
    }
}
