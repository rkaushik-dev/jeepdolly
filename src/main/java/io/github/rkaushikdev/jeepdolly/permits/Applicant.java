package io.github.rkaushikdev.jeepdolly.permits;

import io.github.rkaushikdev.jeepdolly.Impossible;

/**
 * Who to ring when something is wrong with an application.
 *
 * @param email an address to write to, or empty
 */
public record Applicant(String name, String phone, String email) {

    public Applicant {
        if (name.isBlank()) {
            throw new Impossible("an application names somebody to ring");
        }
        if (phone.isBlank()) {
            throw new Impossible("an application carries a telephone number");
        }
    }

    /** Somebody to ring, with no address to write to. */
    public Applicant(String name, String phone) {
        this(name, phone, "");
    }

    @Override
    public String toString() {
        return name + " " + phone;
    }
}
