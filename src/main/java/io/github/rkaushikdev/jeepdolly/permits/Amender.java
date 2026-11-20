package io.github.rkaushikdev.jeepdolly.permits;

import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.NotAllowed;
import io.github.rkaushikdev.jeepdolly.domain.Carrier;
import io.github.rkaushikdev.jeepdolly.fees.Pricing;
import io.github.rkaushikdev.jeepdolly.fees.Quote;
import io.github.rkaushikdev.jeepdolly.routing.Itinerary;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Amending a permit, which is issuing a new one and killing the old one.
 *
 * <p>Nobody edits a permit. The document has been printed, faxed to a customer and filed in a
 * truck, and a system that quietly changes what it says is a system that cannot tell an
 * enforcement officer what the driver is carrying. So an amendment is a replacement. The revised
 * application is assessed from scratch, exactly as a new one would be, a new number is issued in the
 * replacement series, and the old permit is marked superseded with a pointer to the new one.
 *
 * <p>Two rules keep this honest. The replacement is assessed against today's road, not the road as
 * it was when the first permit was written: a bridge posted last week is posted for the amendment,
 * and a hauler who amends a permit to add a day may find he has lost the route. That surprises
 * people and it is right, because the amendment is a new decision about a trip that has not happened
 * yet. And the price is the differential and never a refund: if the amended trip prices lower, the
 * office keeps the difference and charges the amendment fee anyway, because the work is the same
 * work.
 *
 * <p>What changed is worked out and recorded, because the letter that goes with the new permit says
 * what moved and the file has to show it.
 */
public final class Amender {

    private Amender() {}

    /**
     * What the revised application asks to move on an issued permit: dates, trip, gross, size and
     * route, in that order. A revision that names no route leaves the route alone.
     */
    public static List<Change> changesBetween(Permit permit, Application application) {
        List<Change> found = new ArrayList<>();
        if (!permit.start().equals(application.start()) || !permit.end().equals(application.end())) {
            found.add(new Change(
                    "dates", permit.start() + " to " + permit.end(), application.start() + " to " + application.end()));
        }
        if (!permit.origin().equals(application.origin())
                || !permit.destination().equals(application.destination())) {
            found.add(new Change(
                    "trip",
                    permit.origin() + " to " + permit.destination(),
                    application.origin() + " to " + application.destination()));
        }
        if (!permit.gross().equals(application.gross())) {
            found.add(new Change(
                    "gross", permit.gross().toString(), application.gross().toString()));
        }
        if (!permit.envelope().equals(application.combination().envelope())) {
            found.add(new Change(
                    "size",
                    permit.envelope().toString(),
                    application.combination().envelope().toString()));
        }
        if (!application.route().isEmpty() && !permit.route().equals(application.route())) {
            found.add(new Change("route", String.join(", ", permit.route()), String.join(", ", application.route())));
        }
        return List.copyOf(found);
    }

    /**
     * Assess the revision, issue a replacement, and supersede the permit it replaces.
     *
     * <p>Nothing about the permit changes unless the replacement can be issued: a revision the road
     * of the day refuses leaves the permit issued and as it was.
     *
     * @throws NotAllowed when the permit is not live, has run out, or the revision is refused (with
     *     every reason as the {@code reasons} fact)
     * @throws Impossible when the application changes nothing on the permit
     */
    public static Amendment amend(Office office, Permit permit, Application application, LocalDate on) {
        if (permit.state() != PermitState.ISSUED) {
            throw new NotAllowed(permit.number() + " is " + permit.state() + " and cannot be amended");
        }
        if (permit.expiredOn(on)) {
            throw new NotAllowed(permit.number() + " expired on " + permit.end());
        }
        List<Change> changes = changesBetween(permit, application);
        if (changes.isEmpty()) {
            throw new Impossible(permit.number() + " is not being changed by this application");
        }

        Assessment found = Assessor.assess(office, application, on);
        if (!found.permittable()) {
            throw new NotAllowed(
                    permit.number() + " cannot be amended as asked", Facts.of("reasons", found.refusals()));
        }
        if (found.itinerary().isEmpty() || found.quote().isEmpty()) {
            throw new Impossible("an amendment with no route or no price cannot be issued");
        }
        Itinerary itinerary = found.itinerary().get();
        Quote quote = found.quote().get();

        PermitNumber number = office.numbering().nextNumber(on, Series.REPLACEMENT);
        Carrier carrier = office.carriers().get(application.usdot());
        Permit replacement = Issuer.drawUp(number, application, carrier, itinerary, found, quote, on)
                .supersedes(permit.number())
                .build();
        permit.supersede(on, number);
        Quote charged = Pricing.amendment(office.schedules().on(on), permit.quote(), quote);
        return new Amendment(permit, replacement, changes, charged, found);
    }
}
