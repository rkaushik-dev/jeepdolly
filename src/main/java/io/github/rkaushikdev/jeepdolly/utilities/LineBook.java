package io.github.rkaushikdev.jeepdolly.utilities;

import io.github.rkaushikdev.jeepdolly.Conflict;
import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.NotOnFile;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Every crossing the office has measured, and every owner of one. */
public final class LineBook {

    private final Map<String, LineOwner> owners = new LinkedHashMap<>();
    private final Map<String, Crossing> crossings = new LinkedHashMap<>();

    /** @throws Conflict when an owner with that ident is already on file */
    public LineOwner addOwner(LineOwner owner) {
        if (owners.containsKey(owner.ident())) {
            throw new Conflict("line owner " + owner.ident() + " is already on file");
        }
        owners.put(owner.ident(), owner);
        return owner;
    }

    /** @throws NotOnFile when no owner with that ident is on file */
    public LineOwner owner(String ident) {
        LineOwner found = owners.get(ident);
        if (found == null) {
            throw new NotOnFile("no line owner " + ident, Facts.of("ident", ident));
        }
        return found;
    }

    /** Every owner, by ident. */
    public List<LineOwner> owners() {
        return owners.values().stream()
                .sorted(Comparator.comparing(LineOwner::ident))
                .toList();
    }

    /**
     * Put a crossing on file against an owner already on file.
     *
     * @throws Conflict when a crossing with that ident is already on file
     * @throws NotOnFile when its owner is not on file
     */
    public Crossing add(Crossing crossing) {
        if (crossings.containsKey(crossing.ident())) {
            throw new Conflict("crossing " + crossing.ident() + " is already on file");
        }
        owner(crossing.owner());
        crossings.put(crossing.ident(), crossing);
        return crossing;
    }

    /** @throws NotOnFile when no crossing with that ident is on file */
    public Crossing get(String ident) {
        Crossing found = crossings.get(ident);
        if (found == null) {
            throw new NotOnFile("no crossing " + ident, Facts.of("ident", ident));
        }
        return found;
    }

    /** The crossings of one segment, by ident. */
    public List<Crossing> onSegment(String segment) {
        return crossings.values().stream()
                .filter(one -> one.segment().equals(segment))
                .sorted(Comparator.comparing(Crossing::ident))
                .toList();
    }

    /** Every crossing, by ident. */
    public List<Crossing> crossings() {
        return crossings.values().stream()
                .sorted(Comparator.comparing(Crossing::ident))
                .toList();
    }

    /** The lowest wire on file, the first by ident when two hang at one height. */
    public Optional<Crossing> lowest() {
        return crossings.values().stream()
                .min(Comparator.comparing(Crossing::height).thenComparing(Crossing::ident));
    }

    /** How many crossings are on file. */
    public int size() {
        return crossings.size();
    }
}
