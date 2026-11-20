package io.github.rkaushikdev.jeepdolly.api;

/**
 * A request whose key is missing, unknown or revoked, answered 401.
 *
 * <p>It is not a refusal. A refusal is the office saying no to a load; this is the door saying it
 * does not know who is knocking, and it says nothing about whether the key was wrong or revoked.
 */
public class NotAuthenticated extends RuntimeException {

    public NotAuthenticated(String detail) {
        super(detail);
    }
}
