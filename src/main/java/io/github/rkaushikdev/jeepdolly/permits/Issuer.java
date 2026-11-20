package io.github.rkaushikdev.jeepdolly.permits;

import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.NotAllowed;
import io.github.rkaushikdev.jeepdolly.domain.Carrier;
import io.github.rkaushikdev.jeepdolly.fees.Quote;
import io.github.rkaushikdev.jeepdolly.limits.PermitKind;
import io.github.rkaushikdev.jeepdolly.routing.Itinerary;
import java.time.LocalDate;

/**
 * Turning a clean assessment into a numbered permit.
 *
 * <p>This is deliberately dull. Everything hard was decided by the assessment, and this writes it
 * down: a number off the counter, the carrier as it stands today, the route and the conditions
 * copied rather than referenced, and the price as it was quoted.
 *
 * <p>It refuses an assessment that was refused. That check is not redundant with the one in the
 * assessment: a caller that has an assessment in its hand can issue against it, and this is the
 * last place that can say no.
 */
public final class Issuer {

    private Issuer() {}

    /**
     * Turn a clean assessment into a numbered permit, and close the application as issued.
     *
     * <p>A superload takes its number out of its own series; everything else is a single trip.
     *
     * @throws NotAllowed when the assessment refused the application, with every reason as the
     *     {@code reasons} fact
     * @throws Impossible when the assessment has no route or no price to write down
     */
    public static Permit issue(Office office, Assessment assessment, LocalDate on) {
        Application application = assessment.application();
        if (!assessment.permittable()) {
            throw new NotAllowed(
                    application.reference() + " cannot be issued", Facts.of("reasons", assessment.refusals()));
        }
        if (assessment.itinerary().isEmpty() || assessment.quote().isEmpty()) {
            throw new Impossible("an assessment with no route or no price cannot be issued");
        }
        Series series = assessment.kind() == PermitKind.SUPERLOAD ? Series.SUPERLOAD : Series.SINGLE;
        PermitNumber number = office.numbering().nextNumber(on, series);
        Carrier carrier = office.carriers().get(application.usdot());
        application.issue(on, number.toString());
        return drawUp(
                        number,
                        application,
                        carrier,
                        assessment.itinerary().get(),
                        assessment,
                        assessment.quote().get(),
                        on)
                .build();
    }

    /**
     * A permit for an assessed application, with nothing on it but what the assessment decided.
     *
     * <p>The route, the narrative and the conditions are copied off the assessment, so a later change
     * to the road log does not reach back into a permit already printed.
     */
    static Permit.Builder drawUp(
            PermitNumber number,
            Application application,
            Carrier carrier,
            Itinerary itinerary,
            Assessment assessment,
            Quote quote,
            LocalDate on) {
        return Permit.builder()
                .number(number)
                .application(application.reference())
                .usdot(application.usdot())
                .carrierName(carrier.name())
                .issued(on)
                .start(application.start())
                .end(application.end())
                .origin(application.origin())
                .destination(application.destination())
                .route(itinerary.idents())
                .narrative(itinerary.narrative())
                .gross(application.gross())
                .envelope(application.combination().envelope())
                .distance(itinerary.distance())
                .conditions(assessment.conditions())
                .quote(quote);
    }
}
