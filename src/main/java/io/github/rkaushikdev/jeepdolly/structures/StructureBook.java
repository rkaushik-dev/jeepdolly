package io.github.rkaushikdev.jeepdolly.structures;

import io.github.rkaushikdev.jeepdolly.Conflict;
import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.NotOnFile;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The structure inventory: every structure on file, asked for by segment and by ident.
 *
 * <p>This is the list the whole office is afraid of, because a structure is the one thing a permit
 * cannot be argued about afterwards.
 */
public final class StructureBook {

    private final Map<String, Structure> byIdent = new LinkedHashMap<>();
    private final Map<String, List<String>> bySegment = new LinkedHashMap<>();

    /** @throws Conflict when a structure with that ident is already on file */
    public Structure add(Structure structure) {
        if (byIdent.containsKey(structure.ident())) {
            throw new Conflict("structure " + structure.ident() + " is already on file");
        }
        byIdent.put(structure.ident(), structure);
        bySegment
                .computeIfAbsent(structure.segment(), segment -> new ArrayList<>())
                .add(structure.ident());
        return structure;
    }

    /** @throws NotOnFile when no structure with that ident is on file */
    public Structure get(String ident) {
        Structure found = byIdent.get(ident);
        if (found == null) {
            throw new NotOnFile("no structure " + ident, Facts.of("structure", ident));
        }
        return found;
    }

    /** The structures of one segment, by ident. */
    public List<Structure> onSegment(String segment) {
        return bySegment.getOrDefault(segment, List.of()).stream()
                .sorted()
                .map(byIdent::get)
                .toList();
    }

    /** The structures of several segments, segment by segment in the order given. */
    public List<Structure> onSegments(List<String> segments) {
        List<Structure> found = new ArrayList<>();
        for (String segment : segments) {
            found.addAll(onSegment(segment));
        }
        return List.copyOf(found);
    }

    /** The posted structures, by ident, which is the list the router cares about. */
    public List<Structure> posted() {
        return byIdent.values().stream()
                .filter(Structure::isPosted)
                .sorted(Comparator.comparing(Structure::ident))
                .toList();
    }

    /** Structures built before a year, oldest first, for the inspection programme. */
    public List<Structure> olderThan(int year) {
        return byIdent.values().stream()
                .filter(one -> one.built() != 0 && one.built() < year)
                .sorted(Comparator.comparingInt(Structure::built))
                .toList();
    }

    public int size() {
        return byIdent.size();
    }

    public boolean contains(String ident) {
        return byIdent.containsKey(ident);
    }
}
