package io.github.rkaushikdev.jeepdolly.enforcement;

import io.github.rkaushikdev.jeepdolly.Dollars;
import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.NotOnFile;
import io.github.rkaushikdev.jeepdolly.Pounds;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The penalty statute as it stood from one day, with the day on it.
 *
 * <p>The office does not write the penalties. They are in statute and they change when the
 * legislature says so, which is roughly every four years and never on a schedule anybody can plan
 * around. So the schedule is data with a date on it, the same shape the fee schedules use, and an
 * old citation is always priced against the schedule that was in force on the day it was written.
 *
 * <p>Weight is priced by the pound over, in cumulative {@link Band}s. Everything that is not weight
 * is a flat amount, because the statute does not scale it and an office that invented a scale
 * would be inventing law.
 *
 * @param minimum the least any citation comes to before the repeat multiplier
 * @param bands the overweight scale, every band but the last with a ceiling
 * @param flat the amount for each offence that is not priced by weight
 */
public record PenaltySchedule(LocalDate effective, Dollars minimum, List<Band> bands, Map<Offence, Dollars> flat) {

    public PenaltySchedule {
        bands = List.copyOf(bands);
        flat = Map.copyOf(flat);
        if (bands.isEmpty()) {
            throw new Impossible("a penalty schedule has at least one weight band");
        }
        if (bands.subList(0, bands.size() - 1).stream()
                .anyMatch(one -> one.upto().isEmpty())) {
            throw new Impossible("only the last penalty band runs to the top");
        }
        if (bands.get(bands.size() - 1).upto().isPresent()) {
            throw new Impossible("the last penalty band runs to the top");
        }
    }

    /** The schedule as it stands in the statute, in force from the start of 2024. */
    public static PenaltySchedule standard() {
        return standard(LocalDate.of(2024, 1, 1));
    }

    /** The schedule as it stands in the statute, for an office with no rows, from a given day. */
    public static PenaltySchedule standard(LocalDate effective) {
        Map<Offence, Dollars> flat = new LinkedHashMap<>();
        flat.put(Offence.NO_PERMIT, Dollars.of(500));
        flat.put(Offence.OFF_ROUTE, Dollars.of(250));
        flat.put(Offence.IN_CURFEW, Dollars.of(250));
        flat.put(Offence.NO_ESCORT, Dollars.of(350));
        flat.put(Offence.NO_MARKING, Dollars.of(100));
        flat.put(Offence.AXLES_NOT_AS_DECLARED, Dollars.of(300));
        flat.put(Offence.EXPIRED_PERMIT, Dollars.of(400));
        return new PenaltySchedule(
                effective,
                Dollars.of(50),
                List.of(
                        new Band(new Pounds(5000), 3),
                        new Band(new Pounds(5000), 5),
                        new Band(new Pounds(10000), 9),
                        Band.toTheTop(15)),
                flat);
    }

    /**
     * The flat amount for an offence that is not priced by weight.
     *
     * @throws NotOnFile when this schedule does not price the offence
     */
    public Dollars flatFor(Offence offence) {
        Dollars found = flat.get(offence);
        if (found == null) {
            throw new NotOnFile(
                    "the schedule of " + effective + " does not price " + offence,
                    Facts.of("offence", offence.value()));
        }
        return found;
    }

    @Override
    public String toString() {
        return "penalties of " + effective;
    }
}
