package io.github.rkaushikdev.jeepdolly.domain;

import io.github.rkaushikdev.jeepdolly.Conflict;
import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.NotOnFile;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The road network as the office holds it: every segment on file, with the joins worked out once.
 *
 * <p>The state's road log is a list of segments, and every route check in this system is a walk
 * down a list of these. The network does not know anything about weight, height, or season. Those
 * are restrictions, they hang off segments, and they live in their own book, because they change
 * weekly and the road log changes twice a year.
 */
public final class Network {

    private static final Comparator<Segment> BY_IDENT = Comparator.comparing(Segment::ident);

    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private final Map<String, Segment> segments = new LinkedHashMap<>();
    private final Map<String, List<String>> touching = new LinkedHashMap<>();

    /**
     * Put a node on file.
     *
     * @throws Conflict when a node of that name is already on file
     */
    public Node addNode(Node node) {
        if (nodes.containsKey(node.name())) {
            throw new Conflict("node " + node.name() + " is already on file", Facts.of("node", node.name()));
        }
        nodes.put(node.name(), node);
        touching.put(node.name(), new ArrayList<>());
        return node;
    }

    /**
     * Put a segment on file between two nodes already on file.
     *
     * @throws Conflict when a segment with that ident is already on file
     * @throws NotOnFile when either end names a node that is not on file
     */
    public Segment addSegment(Segment segment) {
        if (segments.containsKey(segment.ident())) {
            throw new Conflict(
                    "segment " + segment.ident() + " is already on file", Facts.of("segment", segment.ident()));
        }
        for (String end : List.of(segment.start(), segment.end())) {
            if (!nodes.containsKey(end)) {
                throw new NotOnFile("segment " + segment.ident() + " names node " + end + ", which is not on file");
            }
        }
        segments.put(segment.ident(), segment);
        touching.get(segment.start()).add(segment.ident());
        touching.get(segment.end()).add(segment.ident());
        return segment;
    }

    /** @throws NotOnFile when no node of that name is on file */
    public Node node(String name) {
        Node found = nodes.get(name);
        if (found == null) {
            throw new NotOnFile("no node called " + name, Facts.of("node", name));
        }
        return found;
    }

    /** @throws NotOnFile when no segment with that ident is on file */
    public Segment segment(String ident) {
        Segment found = segments.get(ident);
        if (found == null) {
            throw new NotOnFile("no segment " + ident, Facts.of("segment", ident));
        }
        return found;
    }

    /**
     * Segments that can be driven away from this node, by ident.
     *
     * @throws NotOnFile when the node is not on file
     */
    public List<Segment> leaving(String node) {
        node(node);
        return touching.get(node).stream()
                .map(segments::get)
                .filter(one -> one.runsFrom(node))
                .sorted(BY_IDENT)
                .toList();
    }

    /** Segments joining two nodes directly, in either direction. */
    public List<Segment> between(String start, String end) {
        return leaving(start).stream()
                .filter(one -> one.otherEnd(start).equals(end))
                .toList();
    }

    public List<Segment> onRoute(Route route) {
        return segments.values().stream()
                .filter(one -> one.route().equals(route))
                .sorted(BY_IDENT)
                .toList();
    }

    public List<Segment> inDistrict(int district) {
        return segments.values().stream()
                .filter(one -> one.jurisdiction().district() == district)
                .sorted(BY_IDENT)
                .toList();
    }

    /** Every segment, by ident. */
    public List<Segment> segments() {
        return segments.values().stream().sorted(BY_IDENT).toList();
    }

    /** Every node, by name. */
    public List<Node> nodes() {
        return nodes.values().stream().sorted(Comparator.comparing(Node::name)).toList();
    }

    /** How many segments are on file. */
    public int size() {
        return segments.size();
    }

    /** Whether a segment with this ident is on file. */
    public boolean contains(String ident) {
        return segments.containsKey(ident);
    }
}
