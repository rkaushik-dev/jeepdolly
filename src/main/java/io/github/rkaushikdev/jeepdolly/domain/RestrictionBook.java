package io.github.rkaushikdev.jeepdolly.domain;

import io.github.rkaushikdev.jeepdolly.Conflict;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Every restriction on file, asked for by segment and by day. */
public final class RestrictionBook {

    private final Map<String, Restriction> byIdent = new LinkedHashMap<>();
    private final Map<String, List<String>> bySegment = new LinkedHashMap<>();

    /** @throws Conflict when a restriction with that ident is already on file */
    public Restriction add(Restriction restriction) {
        if (byIdent.containsKey(restriction.ident())) {
            throw new Conflict("restriction " + restriction.ident() + " is already on file");
        }
        byIdent.put(restriction.ident(), restriction);
        bySegment
                .computeIfAbsent(restriction.segment(), segment -> new ArrayList<>())
                .add(restriction.ident());
        return restriction;
    }

    /**
     * Take one off the book, which happens when the work finishes early.
     *
     * @throws Conflict when there is no such restriction to lift
     */
    public Restriction lift(String ident) {
        Restriction restriction = byIdent.remove(ident);
        if (restriction == null) {
            throw new Conflict("no restriction " + ident + " to lift");
        }
        bySegment.get(restriction.segment()).remove(ident);
        return restriction;
    }

    /** What is in force on one segment on one day, in the order it was filed. */
    public List<Restriction> onSegment(String segment, LocalDate day) {
        return bySegment.getOrDefault(segment, List.of()).stream()
                .map(byIdent::get)
                .filter(one -> one.appliesOn(day))
                .toList();
    }

    /** Every restriction of one kind in force on a day, by ident. */
    public List<Restriction> ofKind(RestrictionKind kind, LocalDate day) {
        return byIdent.values().stream()
                .filter(one -> one.kind() == kind && one.appliesOn(day))
                .sorted(Comparator.comparing(Restriction::ident))
                .toList();
    }

    public List<Restriction> closures(LocalDate day) {
        return ofKind(RestrictionKind.CLOSURE, day);
    }

    /** The segments with anything in force on a day, sorted, which is what the router avoids. */
    public List<String> segmentsRestricted(LocalDate day) {
        TreeSet<String> found = new TreeSet<>();
        byIdent.values().stream().filter(one -> one.appliesOn(day)).forEach(one -> found.add(one.segment()));
        return List.copyOf(found);
    }

    public int size() {
        return byIdent.size();
    }
}
