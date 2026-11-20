package io.github.rkaushikdev.jeepdolly.api;

/**
 * A known caller below the rung a route needs, answered 403 with what was needed and what was
 * held.
 */
public class NotPermitted extends RuntimeException {

    public NotPermitted(String detail) {
        super(detail);
    }
}
