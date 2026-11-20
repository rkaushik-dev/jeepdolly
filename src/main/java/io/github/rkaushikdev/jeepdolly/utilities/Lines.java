package io.github.rkaushikdev.jeepdolly.utilities;

import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.domain.Envelope;
import io.github.rkaushikdev.jeepdolly.routing.Itinerary;
import io.github.rkaushikdev.jeepdolly.routing.Leg;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Every wire on a route, sorted into the lifts to arrange and the wires in the way.
 *
 * <p>The office wants six inches of daylight between the load and the wire and does not negotiate
 * it. The notice is the part that catches people: a hauler who files on Monday for a Wednesday move
 * under a line wanting five working days is refused.
 */
public final class Lines {

    /** Daylight the office wants between the load and the wire. */
    public static final Inches SAFETY = Inches.of(0, 6);

    /** Saturday and Sunday, which no line crew works without an emergency. */
    public static final Set<DayOfWeek> WEEKEND = Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);

    private Lines() {}

    /** The clearance this load wants, which is its height plus the daylight. */
    public static Inches wanted(Envelope envelope) {
        return envelope.height().plus(SAFETY);
    }

    /**
     * Every crossing on the route, in driven order, sorted into lifts and refusals.
     *
     * @param filed the day the application was filed, which is what the notice is read against
     * @param travel the day the load moves under the wires
     */
    public static LineReading under(
            Itinerary itinerary, Envelope envelope, LineBook book, LocalDate filed, LocalDate travel) {
        List<Lift> lifts = new ArrayList<>();
        List<BlockedLine> blocked = new ArrayList<>();
        for (Leg leg : itinerary) {
            for (Crossing crossing : book.onSegment(leg.segment().ident())) {
                if (crossing.clears(envelope)) {
                    continue;
                }
                LineOwner owner = book.owner(crossing.owner());
                if (!crossing.clearsLifted(envelope)) {
                    blocked.add(new BlockedLine(crossing, owner, wanted(envelope)));
                    continue;
                }
                boolean inTime = owner.inTime(filed, travel);
                if (!inTime) {
                    blocked.add(new BlockedLine(crossing, owner, wanted(envelope), true));
                    continue;
                }
                lifts.add(new Lift(crossing, owner, owner.noticeBy(travel), inTime));
            }
        }
        return new LineReading(lifts, blocked);
    }
}
