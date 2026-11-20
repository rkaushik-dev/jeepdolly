package io.github.rkaushikdev.jeepdolly.api;

import java.util.Optional;

/**
 * Who is asking, once the key has been read.
 *
 * <p>A carrier key carries its usdot number, and a route that reads permits shows that carrier only
 * its own. That is the one part of access that is not a ladder, and the route checks it, because
 * it is about the row and not about the person.
 *
 * @param ident the key's own name on file
 * @param person the name that goes on the permit and the audit trail
 * @param usdot the carrier a carrier's key belongs to, or empty for an office key
 */
public record Caller(String ident, String person, Role role, Optional<Integer> usdot) {

    /** An office key, which belongs to no carrier. */
    public Caller(String ident, String person, Role role) {
        this(ident, person, role, Optional.empty());
    }

    /** Whether this is a carrier's own key. */
    public boolean isCarrier() {
        return usdot.isPresent();
    }

    /** Whether this caller's rung reaches {@code wanted}. */
    public boolean may(Role wanted) {
        return role.reaches(wanted);
    }

    @Override
    public String toString() {
        return person + " (" + role + ")";
    }
}
