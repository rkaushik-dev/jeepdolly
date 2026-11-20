package io.github.rkaushikdev.jeepdolly.permits;

import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.Miles;
import io.github.rkaushikdev.jeepdolly.NotAllowed;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.conditions.Condition;
import io.github.rkaushikdev.jeepdolly.conditions.ConditionLibrary;
import io.github.rkaushikdev.jeepdolly.domain.Commodity;
import io.github.rkaushikdev.jeepdolly.domain.Envelope;
import io.github.rkaushikdev.jeepdolly.fees.FeeLine;
import io.github.rkaushikdev.jeepdolly.fees.FeeSchedule;
import io.github.rkaushikdev.jeepdolly.fees.Quote;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The annual permit, which is a different animal from a single trip.
 *
 * <p>A single trip permit is written against a load, a route and a week. An annual is written
 * against a combination and a year, with no route at all, and the hauler is trusted to keep inside
 * a published envelope and to obey what is posted. It exists because a crane company that moves the
 * same crane four times a week cannot ring the office four times a week.
 *
 * <p>There is no route: the permit says all state maintained highways, and that is why the envelope
 * is tight. There is no bridge analysis, for the same reason: the weight cap is set below what the
 * posted structures on the system will take. It is valid for a year from the day it is issued, not
 * for a calendar year, because the alternative is a queue at the counter every January.
 *
 * <p>What the annual does not excuse is a posting or a closure. The conditions say so in as many
 * words, because the one argument the office has every spring is a hauler with an annual on a road
 * under a thaw order.
 */
public final class Annual {

    /** The box an annual permit covers. Past it, the hauler wants a trip permit. */
    public static final Envelope ENVELOPE =
            new Envelope(Inches.of(12), Inches.of(14), Inches.of(100), Inches.of(4), Inches.of(10));

    /** Set below what the posted structures on the system will carry. */
    public static final Pounds GROSS = new Pounds(120000);

    /** What an annual permit says instead of a route. */
    public static final String EVERYWHERE = "all state maintained highways";

    /** The conditions an annual carries on top of its dates. */
    public static final List<String> CONDITIONS = List.of("R-01", "T-02", "Q-01", "Q-03", "G-01", "G-02", "G-03");

    /** An annual runs this many days after the day it is issued, which makes a year. */
    static final int DAYS_AFTER_ISSUE = 364;

    private Annual() {}

    /** Read the annual envelope and the weight cap against one combination. */
    public static Eligibility eligible(Envelope envelope, Pounds gross, Commodity commodity) {
        List<String> reasons = new ArrayList<>();
        for (String name : envelope.excessOver(ENVELOPE).over()) {
            reasons.add(name.replace('_', ' ') + " is past the annual envelope");
        }
        if (gross.compareTo(GROSS) > 0) {
            reasons.add("gross is over the annual cap of " + GROSS);
        }
        if (!commodity.permittable()) {
            reasons.add(commodity.code() + " could have been loaded to legal weight");
        }
        if (commodity.restrictedToAllowance()) {
            reasons.add(commodity.code() + " moves under an allowance and not on an annual");
        }
        return new Eligibility(reasons.isEmpty(), reasons);
    }

    /** One line, one number, and no mileage at all. */
    public static Quote price(FeeSchedule schedule) {
        return new Quote(List.of(new FeeLine("annual permit", schedule.annual())), schedule.toString());
    }

    /** What an annual prints, which is short and says what it does not cover. */
    public static List<Condition> conditionsFor(ConditionLibrary library, LocalDate start, LocalDate end) {
        List<Condition> found = new ArrayList<>();
        found.add(library.render("T-01", Facts.of("start", start.toString(), "end", end.toString())));
        for (String code : CONDITIONS) {
            found.add(library.render(code));
        }
        return ConditionLibrary.sortConditions(found);
    }

    /**
     * Write an annual, refusing anything the envelope does not cover.
     *
     * @throws NotAllowed with every reason, when the combination cannot hold an annual
     */
    public static Permit issue(
            Numbering numbering,
            ConditionLibrary library,
            FeeSchedule schedule,
            String applicationReference,
            int usdot,
            String carrierName,
            Envelope envelope,
            Pounds gross,
            Commodity commodity,
            LocalDate on) {
        Eligibility standing = eligible(envelope, gross, commodity);
        if (!standing.allowed()) {
            throw new NotAllowed(
                    applicationReference + " cannot hold an annual permit", Facts.of("reasons", standing.reasons()));
        }
        LocalDate end = on.plusDays(DAYS_AFTER_ISSUE);
        return Permit.builder()
                .number(numbering.nextNumber(on, Series.ANNUAL))
                .application(applicationReference)
                .usdot(usdot)
                .carrierName(carrierName)
                .issued(on)
                .start(on)
                .end(end)
                .origin(EVERYWHERE)
                .destination(EVERYWHERE)
                .route(List.of(EVERYWHERE))
                .narrative(EVERYWHERE + ", except as posted or closed")
                .gross(gross)
                .envelope(envelope)
                .distance(Miles.ZERO)
                .conditions(conditionsFor(library, on, end))
                .quote(price(schedule))
                .build();
    }
}
