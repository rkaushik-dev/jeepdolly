package io.github.rkaushikdev.jeepdolly.structures;

import java.time.LocalDate;

/** One thing that happened to a review item, with who and when. */
public record Move(LocalDate on, String by, ReviewState state, String note) {

    /** A move with nothing written beside it. */
    public Move(LocalDate on, String by, ReviewState state) {
        this(on, by, state, "");
    }

    /** As the history prints it: {@code 2025-07-07 counter waiting: opened}. */
    @Override
    public String toString() {
        String written = note.isEmpty() ? "" : ": " + note;
        return on + " " + by + " " + state + written;
    }
}
