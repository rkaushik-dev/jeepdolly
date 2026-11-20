package io.github.rkaushikdev.jeepdolly.permits;

import io.github.rkaushikdev.jeepdolly.Valued;

/**
 * Where an application stands.
 *
 * <p>Draft is being typed. Submitted is in the queue. In review is with an engineer. Issued and
 * refused are the ends of it, and withdrawn is the sixth, which nobody counts because the hauler
 * rang up and cancelled.
 */
public enum ApplicationState implements Valued {
    DRAFT("draft"),
    SUBMITTED("submitted"),
    IN_REVIEW("in review"),
    ISSUED("issued"),
    REFUSED("refused"),
    WITHDRAWN("withdrawn");

    private final String value;

    ApplicationState(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** The state stored under {@code value}. */
    public static ApplicationState of(String value) {
        return Valued.of(ApplicationState.class, value);
    }

    /** Whether the file is still open: drafted, queued or with an engineer. */
    public boolean open() {
        return this == DRAFT || this == SUBMITTED || this == IN_REVIEW;
    }

    @Override
    public String toString() {
        return value;
    }
}
