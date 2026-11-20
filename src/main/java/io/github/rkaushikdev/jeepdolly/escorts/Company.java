package io.github.rkaushikdev.jeepdolly.escorts;

import io.github.rkaushikdev.jeepdolly.Conflict;

/**
 * A pilot car company, which is who the carrier telephones.
 *
 * <p>The company matters because it carries the liability insurance, and because a company can be
 * suspended in one go when its insurance lapses: a suspended company suspends every operator on its
 * books.
 */
public record Company(String ident, String name, String town, String telephone, boolean suspended, String note) {

    public Company {
        if (ident.isBlank()) {
            throw new Conflict("a pilot car company has an ident");
        }
    }

    /** A company in good standing with nothing noted against it. */
    public Company(String ident, String name, String town, String telephone) {
        this(ident, name, town, telephone, false, "");
    }

    /** A company with nothing noted against it, suspended or not. */
    public Company(String ident, String name, String town, String telephone, boolean suspended) {
        this(ident, name, town, telephone, suspended, "");
    }

    /** As the counter reads it: {@code PC-1  Kennebec Pilot Car, Waterville}. */
    @Override
    public String toString() {
        String mark = suspended ? " (suspended)" : "";
        return ident + "  " + name + ", " + town + mark;
    }
}
