package io.github.rkaushikdev.jeepdolly.persistence;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The published books held between requests, keyed on the day, emptied by any watched write.
 *
 * <p>Correctness comes from two things and not from a clock. The key is the day, because the size
 * and fee tables differ by day and a cache that ignored that would price a Tuesday move off
 * Monday's schedule. The invalidation is a flush hook rather than an expiry ({@link
 * Reference#watch}): any flush that touched a watched table empties the whole cache, for every day,
 * at once. That is blunt and it is right; an office that cannot see its own edits is worse than a
 * slow one.
 *
 * <p>What this does not solve is a second process. A road log loaded by the command line does not
 * empty the service's cache, and the service serves the old log until it restarts or something
 * local writes. That is a known shortcoming; the fix when it is needed is a version row rather than
 * a longer expiry.
 */
public final class ReferenceCache {

    private final Map<LocalDate, Reference> books = new TreeMap<>();
    private int hits;
    private int misses;
    private int emptied;

    /** The books as they stand on a day, read once and then held. */
    public synchronized Reference books(EntityManager entityManager, LocalDate day) {
        Reference found = books.get(day);
        if (found != null) {
            hits++;
            return found;
        }
        misses++;
        Reference built = Reference.read(entityManager, day);
        books.put(day, built);
        return built;
    }

    /** Let go of every day. Emptying an empty cache is not counted. */
    public synchronized void clear() {
        if (!books.isEmpty()) {
            emptied++;
        }
        books.clear();
    }

    /** The days held, earliest first. */
    public synchronized List<LocalDate> days() {
        return List.copyOf(books.keySet());
    }

    /** How many days are held. */
    public synchronized int size() {
        return books.size();
    }

    public synchronized int hits() {
        return hits;
    }

    public synchronized int misses() {
        return misses;
    }

    /** How many times a full cache was emptied. */
    public synchronized int emptied() {
        return emptied;
    }
}
