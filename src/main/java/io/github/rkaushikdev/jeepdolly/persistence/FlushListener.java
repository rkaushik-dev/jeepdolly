package io.github.rkaushikdev.jeepdolly.persistence;

import java.util.Set;

/**
 * Something that wants to know, after a flush has succeeded, which kinds of row it wrote.
 *
 * <p>The reference cache is the reason this exists: a flush that touched a published table has to
 * empty it at once, in the same transaction, so a route read straight afterwards sees the segment
 * that was just added. A flush that fails on a constraint is never reported.
 */
@FunctionalInterface
public interface FlushListener {

    /**
     * A flush inserted, updated or deleted rows of these entity classes.
     *
     * @param changed never empty
     */
    void flushed(Set<Class<?>> changed);
}
