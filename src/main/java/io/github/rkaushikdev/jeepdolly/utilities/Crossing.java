package io.github.rkaushikdev.jeepdolly.utilities;

import io.github.rkaushikdev.jeepdolly.Conflict;
import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.domain.Envelope;
import java.util.Optional;

/**
 * One wire over one segment, measured, with who owns it.
 *
 * <p>A line is measured with daylight on top. A load that is exactly the height of the line does
 * not clear it: wire sags in the heat, the road crowns, and the trailer bounces. A line the owner
 * can raise is a condition rather than a refusal; a line the owner cannot raise is a refusal and the
 * answer is a different route.
 *
 * @param place where on the segment the wire crosses, as a driver would know it
 * @param height what the wire hangs at as last measured
 * @param owner the ident of the owner on file
 * @param liftTo what the owner can raise it to, or empty when it cannot be lifted
 */
public record Crossing(
        String ident, String segment, String place, Inches height, String owner, Optional<Inches> liftTo, String note) {

    public Crossing {
        if (height.value() <= 0) {
            throw new Conflict("crossing " + ident + " has a measured height");
        }
        if (liftTo.isPresent() && liftTo.get().compareTo(height) <= 0) {
            throw new Conflict("crossing " + ident + " cannot be lifted to below where it hangs");
        }
    }

    /** A wire the owner cannot raise, with no note. */
    public Crossing(String ident, String segment, String place, Inches height, String owner) {
        this(ident, segment, place, height, owner, Optional.empty(), "");
    }

    /** A wire the owner can raise to {@code liftTo}, with no note. */
    public Crossing(String ident, String segment, String place, Inches height, String owner, Inches liftTo) {
        this(ident, segment, place, height, owner, Optional.of(liftTo), "");
    }

    public boolean liftable() {
        return liftTo.isPresent();
    }

    /** Whether this load goes under as the wire hangs today, daylight included. */
    public boolean clears(Envelope envelope) {
        return envelope.height().plus(Lines.SAFETY).compareTo(height) <= 0;
    }

    /** Whether it goes under once the owner has raised it. Never, for a wire that cannot be raised. */
    public boolean clearsLifted(Envelope envelope) {
        return liftTo.map(raised -> envelope.height().plus(Lines.SAFETY).compareTo(raised) <= 0)
                .orElse(false);
    }

    /** What the survey card says: {@code L-1  US201-2 at Fairfield village  18 ft, lifts to 24 ft}. */
    @Override
    public String toString() {
        String lift = liftTo.map(raised -> ", lifts to " + raised).orElse(", cannot be lifted");
        return ident + "  " + segment + " at " + place + "  " + height + lift;
    }
}
