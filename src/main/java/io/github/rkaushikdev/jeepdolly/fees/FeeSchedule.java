package io.github.rkaushikdev.jeepdolly.fees;

import io.github.rkaushikdev.jeepdolly.Dollars;
import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.NotOnFile;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.domain.HighwayClass;
import io.github.rkaushikdev.jeepdolly.limits.PermitKind;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One fee schedule, effective from one day.
 *
 * <p>Four charges make a permit. The base charge is per kind of permit: an ordinary single trip is
 * one number, a study is a bigger one because somebody has to read it, and a superload is bigger
 * again. The mileage charge is per chargeable mile, by class of road; the interstate is the cheapest
 * and the state aid roads are dearest. The weight charge is per thousand pounds over the legal
 * gross, in climbing steps. The extras are flat: a review fee where an engineer is involved, an
 * amendment fee, a minimum below which no permit is written at all, and the price of an annual
 * permit.
 *
 * <p>The schedule is dated. A permit issued in March is priced by the March schedule for its whole
 * life, including when it is amended in June, because a hauler who amends a permit is not buying a
 * new one at the new price.
 *
 * @param steps the weight bands, climbing, the last one with no ceiling
 */
public record FeeSchedule(
        LocalDate effective,
        Map<PermitKind, Dollars> base,
        Map<HighwayClass, Dollars> perMile,
        List<WeightStep> steps,
        Dollars review,
        Dollars amendment,
        Dollars minimum,
        Dollars annual) {

    /** Weight is charged in blocks of this many pounds, part blocks rounded up. */
    public static final int WEIGHT_BLOCK = 1000;

    /** The day the coded schedule takes effect. */
    public static final LocalDate STANDARD_EFFECTIVE = LocalDate.of(2024, 1, 1);

    /** What an annual permit costs when a schedule does not say. */
    public static final Dollars ANNUAL = Dollars.of(500);

    public FeeSchedule {
        base = Collections.unmodifiableMap(new LinkedHashMap<>(base));
        perMile = Collections.unmodifiableMap(new LinkedHashMap<>(perMile));
        steps = List.copyOf(steps);
        if (steps.isEmpty()) {
            throw new Impossible("a schedule has at least one weight step");
        }
        if (steps.get(steps.size() - 1).upTo().isPresent()) {
            throw new Impossible("the last weight step has no ceiling");
        }
        List<Optional<Pounds>> ceilings = steps.subList(0, steps.size() - 1).stream()
                .map(WeightStep::upTo)
                .toList();
        if (ceilings.stream().anyMatch(Optional::isEmpty)) {
            throw new Impossible("only the last weight step may be open ended");
        }
        List<Integer> values =
                ceilings.stream().map(one -> one.orElseThrow().value()).toList();
        if (!values.equals(values.stream().sorted().toList()) || new HashSet<>(values).size() != values.size()) {
            throw new Impossible("the weight steps climb");
        }
    }

    /** A schedule whose annual permit costs the usual {@link #ANNUAL}. */
    public FeeSchedule(
            LocalDate effective,
            Map<PermitKind, Dollars> base,
            Map<HighwayClass, Dollars> perMile,
            List<WeightStep> steps,
            Dollars review,
            Dollars amendment,
            Dollars minimum) {
        this(effective, base, perMile, steps, review, amendment, minimum, ANNUAL);
    }

    /** The schedule as it stands, effective from {@link #STANDARD_EFFECTIVE}. */
    public static FeeSchedule standard() {
        return standard(STANDARD_EFFECTIVE);
    }

    /** The schedule as it stands, effective from {@code effective}. */
    public static FeeSchedule standard(LocalDate effective) {
        Map<PermitKind, Dollars> base = new LinkedHashMap<>();
        base.put(PermitKind.ORDINARY, Dollars.of(40));
        base.put(PermitKind.STUDY, Dollars.of(125));
        base.put(PermitKind.SUPERLOAD, Dollars.of(350));
        Map<HighwayClass, Dollars> perMile = new LinkedHashMap<>();
        perMile.put(HighwayClass.INTERSTATE, Dollars.of(0, 12));
        perMile.put(HighwayClass.ARTERIAL, Dollars.of(0, 18));
        perMile.put(HighwayClass.STATE_AID, Dollars.of(0, 26));
        perMile.put(HighwayClass.TOWN_WAY, new Dollars(0));
        return new FeeSchedule(
                effective,
                base,
                perMile,
                List.of(
                        new WeightStep(new Pounds(20000), Dollars.of(5)),
                        new WeightStep(new Pounds(60000), Dollars.of(8)),
                        WeightStep.andAbove(Dollars.of(12))),
                Dollars.of(200),
                Dollars.of(25),
                Dollars.of(40),
                Dollars.of(500));
    }

    /**
     * The base charge for a kind of permit.
     *
     * @throws NotOnFile when the schedule names no fee for that kind, rather than charging nothing
     */
    public Dollars baseFor(PermitKind kind) {
        Dollars found = base.get(kind);
        if (found == null) {
            throw new NotOnFile("no base fee for a " + kind + " permit", Facts.of("kind", kind.toString()));
        }
        return found;
    }

    /**
     * The charge per chargeable mile on a class of road.
     *
     * @throws NotOnFile when the schedule names no rate for that class
     */
    public Dollars mileRate(HighwayClass highwayClass) {
        Dollars found = perMile.get(highwayClass);
        if (found == null) {
            throw new NotOnFile(
                    "no mileage rate for " + highwayClass, Facts.of("highway_class", highwayClass.toString()));
        }
        return found;
    }

    /**
     * The charge for an overage: walk the steps, charging whole blocks and rounding part blocks up.
     * A step boundary is charged at the lower rate. No overage is no charge.
     */
    public Dollars weightCharge(Pounds overage) {
        if (overage.value() <= 0) {
            return new Dollars(0);
        }
        Dollars charged = new Dollars(0);
        int floor = 0;
        for (WeightStep step : steps) {
            int ceiling = step.upTo().map(Pounds::value).orElse(overage.value());
            if (overage.value() <= floor) {
                break;
            }
            int inside = Math.min(overage.value(), ceiling) - floor;
            int blocks = Math.ceilDiv(inside, WEIGHT_BLOCK);
            charged = charged.plus(step.perBlock().times(blocks));
            floor = ceiling;
        }
        return charged;
    }

    /** What a quote names it by: {@code schedule of 2024-01-01}. */
    @Override
    public String toString() {
        return "schedule of " + effective;
    }
}
