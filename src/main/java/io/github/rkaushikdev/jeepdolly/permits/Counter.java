package io.github.rkaushikdev.jeepdolly.permits;

import io.github.rkaushikdev.jeepdolly.Conflict;
import java.util.HashMap;
import java.util.Map;

/**
 * Hands out the next sequence for a series and a year, one at a time, under a lock.
 *
 * <p>The in memory counter. The database counter does the same thing with a locked row rather than
 * a monitor.
 */
public final class Counter implements Sequences {

    /** One series in one year, which is what a sequence counts within. */
    public record Key(int year, Series series) {}

    private final Map<Key, Integer> next = new HashMap<>();

    /** A counter with every series starting at one. */
    public Counter() {
        this(Map.of());
    }

    /**
     * A counter started from where the paper ledger stopped.
     *
     * @param floors the next sequence to hand out, for each series and year that does not start at
     *     one
     */
    public Counter(Map<Key, Integer> floors) {
        next.putAll(floors);
    }

    @Override
    public synchronized int take(int year, Series series) {
        Key key = new Key(year, series);
        int sequence = next.getOrDefault(key, PermitNumber.FIRST_SEQUENCE);
        if (sequence > PermitNumber.LAST_SEQUENCE) {
            throw new Conflict("the " + series + " series is full for " + year);
        }
        next.put(key, sequence + 1);
        return sequence;
    }

    /** The sequence the next take will hand out, without taking it. */
    public synchronized int peek(int year, Series series) {
        return next.getOrDefault(new Key(year, series), PermitNumber.FIRST_SEQUENCE);
    }

    @Override
    public int issued(int year, Series series) {
        return peek(year, series) - PermitNumber.FIRST_SEQUENCE;
    }
}
