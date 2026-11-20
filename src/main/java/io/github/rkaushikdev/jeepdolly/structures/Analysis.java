package io.github.rkaushikdev.jeepdolly.structures;

import io.github.rkaushikdev.jeepdolly.domain.AxleStream;
import io.github.rkaushikdev.jeepdolly.routing.Itinerary;
import io.github.rkaushikdev.jeepdolly.routing.Leg;
import java.util.ArrayList;
import java.util.List;

/**
 * Every structure on a route against one load, in one pass.
 *
 * <p>The three pieces before this each answer part of the question. The inventory says which
 * structures are on the route. The loading says what each of them feels. The rating says what that
 * means. This puts them together and hands back one answer for the whole trip, because that is the
 * shape the counter and the permit both want.
 *
 * <p>Structures that carry no traffic are skipped rather than rated. An overpass over the road is a
 * clearance question and the route evaluator has already asked it.
 */
public final class Analysis {

    private Analysis() {}

    /** Rate a load over a list of structures in the order given. */
    public static StructureReading analyse(List<Structure> structures, AxleStream stream) {
        List<StructureFinding> findings = new ArrayList<>();
        for (Structure structure : structures) {
            if (!structure.kind().carriesTraffic()) {
                continue;
            }
            LoadWindow window = Loading.loadOn(structure, stream);
            findings.add(new StructureFinding(structure, window, Rating.rate(structure, window.weight())));
        }
        return new StructureReading(findings);
    }

    /** Rate a load over every structure of a trip, in driven order. */
    public static StructureReading analyseRoute(StructureBook book, Itinerary itinerary, AxleStream stream) {
        List<Structure> ordered = new ArrayList<>();
        for (Leg leg : itinerary) {
            ordered.addAll(book.onSegment(leg.segment().ident()));
        }
        return analyse(ordered, stream);
    }
}
