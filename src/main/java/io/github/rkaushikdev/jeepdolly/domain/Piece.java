package io.github.rkaushikdev.jeepdolly.domain;

import io.github.rkaushikdev.jeepdolly.Impossible;
import java.util.List;
import java.util.Optional;

/** One thing on the trailer, because a permit may name several. */
public record Piece(Commodity commodity, String description, Optional<String> serial, List<String> tags) {

    public Piece {
        if (description.isBlank()) {
            throw new Impossible("a piece is described or it is not on the permit");
        }
        tags = List.copyOf(tags);
    }

    /** A piece with no serial and no tags. */
    public Piece(Commodity commodity, String description) {
        this(commodity, description, Optional.empty(), List.of());
    }

    /** A piece with a serial number on it and no tags. */
    public Piece(Commodity commodity, String description, String serial) {
        this(commodity, description, Optional.of(serial), List.of());
    }

    /** The description, and the serial in brackets when there is one. */
    @Override
    public String toString() {
        return serial.filter(one -> !one.isEmpty())
                .map(one -> description + " (" + one + ")")
                .orElse(description);
    }
}
