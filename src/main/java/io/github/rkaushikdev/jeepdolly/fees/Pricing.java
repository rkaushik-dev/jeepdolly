package io.github.rkaushikdev.jeepdolly.fees;

import io.github.rkaushikdev.jeepdolly.Dollars;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.limits.PermitKind;
import io.github.rkaushikdev.jeepdolly.routing.ClassMiles;
import io.github.rkaushikdev.jeepdolly.routing.Mileage;
import java.util.ArrayList;
import java.util.List;

/**
 * Pricing a permit, one line at a time, so the hauler can see the arithmetic.
 *
 * <p>The minimum is applied at the end, as its own line, and only ever upward. A permit that prices
 * below the minimum is written up to it with a line saying so, because a receipt that shows forty
 * dollars against thirty one dollars of arithmetic is a receipt somebody queries.
 */
public final class Pricing {

    private Pricing() {}

    /** Price one permit off one schedule, with no engineering review. */
    public static Quote price(FeeSchedule schedule, PermitKind kind, Mileage mileage, Pounds overage) {
        return price(schedule, kind, mileage, overage, false);
    }

    /**
     * Price one permit off one schedule.
     *
     * <p>A class of road with no rate gets no line at all. Miles short of the minimum mileage are
     * charged at the dearest rate the trip used.
     *
     * @param overage the worst overage on the legal gross, which is what the weight charge reads
     * @param review whether an engineer reviews it, which adds the review fee as its own line
     * @throws io.github.rkaushikdev.jeepdolly.NotOnFile when the schedule has no base fee for the
     *     kind or no rate for a class the trip uses
     */
    public static Quote price(FeeSchedule schedule, PermitKind kind, Mileage mileage, Pounds overage, boolean review) {
        List<FeeLine> lines = new ArrayList<>();
        lines.add(new FeeLine(kind + " permit", schedule.baseFor(kind)));
        Dollars dearest = new Dollars(0);
        int counted = 0;
        for (ClassMiles part : mileage.parts()) {
            Dollars rate = schedule.mileRate(part.highwayClass());
            if (rate.compareTo(dearest) > 0) {
                dearest = rate;
            }
            counted += part.charged();
            if (rate.cents() != 0) {
                lines.add(new FeeLine(part.charged() + " miles on " + part.highwayClass(), rate.times(part.charged())));
            }
        }
        if (mileage.minimumApplied() && mileage.charged() > counted) {
            int shortBy = mileage.charged() - counted;
            lines.add(new FeeLine(shortBy + " miles to the minimum mileage", dearest.times(shortBy)));
        }
        Dollars charge = schedule.weightCharge(overage);
        if (charge.cents() != 0) {
            lines.add(new FeeLine(overage + " over legal gross", charge));
        }
        if (review) {
            lines.add(new FeeLine("engineering review", schedule.review()));
        }
        Dollars running = Dollars.total(lines.stream().map(FeeLine::amount).toList());
        if (running.compareTo(schedule.minimum()) < 0) {
            lines.add(new FeeLine("minimum fee adjustment", schedule.minimum().minus(running)));
        }
        return new Quote(lines, schedule.toString());
    }

    /**
     * What an amendment costs: the flat fee, and the difference when the permit got dearer.
     *
     * <p>An amendment that makes a permit cheaper is not a refund. The office keeps what it took and
     * charges the amendment fee, because the work of amending is the same work whichever way the
     * numbers moved.
     */
    public static Quote amendment(FeeSchedule schedule, Quote before, Quote after) {
        Dollars difference = after.total().minus(before.total());
        List<FeeLine> lines = new ArrayList<>();
        lines.add(new FeeLine("amendment fee", schedule.amendment()));
        if (difference.cents() > 0) {
            lines.add(new FeeLine("difference in permit fee", difference));
        }
        return new Quote(lines, schedule.toString());
    }
}
