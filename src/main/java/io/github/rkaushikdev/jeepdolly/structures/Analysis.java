package io.github.rkaushikdev.jeepdolly.structures;

import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.domain.AxleStream;
import io.github.rkaushikdev.jeepdolly.routing.Itinerary;
import io.github.rkaushikdev.jeepdolly.routing.Leg;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Every structure on a route against one load, in one pass.
 *
 * <p>The pieces before this each answer part of the question. The inventory says which structures
 * are on the route and when each was last looked at. The loading says what each of them feels. The
 * rating says what that means on the day of travel. This puts them together and hands back one
 * answer for the whole trip, because that is the shape the counter and the permit both want.
 *
 * <p>Structures that carry no traffic are skipped rather than rated. An overpass over the road is a
 * clearance question and the route evaluator has already asked it.
 */
public final class Analysis {

    private Analysis() {}

    /** Rate a load over a list of structures in order, on its weight alone, every inspection taken as current. */
    public static StructureReading analyse(List<Structure> structures, AxleStream stream) {
        return analyse(structures, stream, Optional.empty());
    }

    /**
     * Rate a load over a list of structures in the order given, on the day it travels when that is
     * known, so a structure past its inspection is held back from clearing it.
     */
    public static StructureReading analyse(List<Structure> structures, AxleStream stream, Optional<LocalDate> on) {
        return analyse(structures, stream, on, Optional.empty());
    }

    /**
     * Rate a load over a list of structures in the order given, on its day of travel and at its
     * width when those are known, so an overdue inspection or a narrow deck holds a crossing back.
     */
    public static StructureReading analyse(
            List<Structure> structures, AxleStream stream, Optional<LocalDate> on, Optional<Inches> width) {
        List<StructureFinding> findings = new ArrayList<>();
        for (Structure structure : structures) {
            if (!structure.kind().carriesTraffic()) {
                continue;
            }
            LoadWindow window = Loading.loadOn(structure, stream);
            Rating rating = Rating.rate(structure, window.weight());
            if (on.isPresent()) {
                rating = width.isPresent()
                        ? Rating.rate(structure, window.weight(), on.get(), width.get())
                        : Rating.rate(structure, window.weight(), on.get());
            }
            findings.add(new StructureFinding(structure, window, rating));
        }
        return new StructureReading(findings);
    }

    /** Rate a load over every structure of a trip in driven order, on its weight alone, inspections as current. */
    public static StructureReading analyseRoute(StructureBook book, Itinerary itinerary, AxleStream stream) {
        return analyse(driven(book, itinerary), stream, Optional.empty());
    }

    /** Rate a load over every structure of a trip, in driven order, on the day it travels. */
    public static StructureReading analyseRoute(
            StructureBook book, Itinerary itinerary, AxleStream stream, LocalDate on) {
        return analyse(driven(book, itinerary), stream, Optional.of(on));
    }

    /** Rate a load of this width over every structure of a trip, in driven order, on the day it travels. */
    public static StructureReading analyseRoute(
            StructureBook book, Itinerary itinerary, AxleStream stream, LocalDate on, Inches width) {
        return analyse(driven(book, itinerary), stream, Optional.of(on), Optional.of(width));
    }

    private static List<Structure> driven(StructureBook book, Itinerary itinerary) {
        List<Structure> ordered = new ArrayList<>();
        for (Leg leg : itinerary) {
            ordered.addAll(book.onSegment(leg.segment().ident()));
        }
        return ordered;
    }
}
