package io.github.rkaushikdev.jeepdolly.persistence;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.hibernate.Hibernate;
import org.hibernate.Interceptor;
import org.hibernate.Transaction;
import org.hibernate.type.Type;

/**
 * One unit of work's record of which kinds of row it has written, reported after each flush.
 *
 * <p>A row is counted when it is persisted or removed, which is when the session first knows about
 * it, and when the flush finds it dirty. The count goes to the listeners only once the flush has
 * succeeded, so a flush that fails on a constraint tells nobody, and the end of the transaction
 * forgets whatever was never flushed.
 */
final class ChangeTracker implements Interceptor {

    private final List<FlushListener> listeners;
    private final Set<Class<?>> changed = new LinkedHashSet<>();

    ChangeTracker(List<FlushListener> listeners) {
        this.listeners = listeners;
    }

    @Override
    public boolean onPersist(Object entity, Object id, Object[] state, String[] propertyNames, Type[] types) {
        changed.add(Hibernate.getClass(entity));
        return false;
    }

    @Override
    public void onRemove(Object entity, Object id, Object[] state, String[] propertyNames, Type[] types) {
        changed.add(Hibernate.getClass(entity));
    }

    @Override
    public boolean onFlushDirty(
            Object entity,
            Object id,
            Object[] currentState,
            Object[] previousState,
            String[] propertyNames,
            Type[] types) {
        changed.add(Hibernate.getClass(entity));
        return false;
    }

    @Override
    public void postFlush(Iterator<Object> entities) {
        if (changed.isEmpty()) {
            return;
        }
        Set<Class<?>> written = Set.copyOf(changed);
        changed.clear();
        for (FlushListener listener : listeners) {
            listener.flushed(written);
        }
    }

    @Override
    public void afterTransactionCompletion(Transaction transaction) {
        changed.clear();
    }
}
