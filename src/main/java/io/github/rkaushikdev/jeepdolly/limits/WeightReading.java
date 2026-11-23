package io.github.rkaushikdev.jeepdolly.limits;

import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.SeasonOrder;
import io.github.rkaushikdev.jeepdolly.domain.Axle;
import io.github.rkaushikdev.jeepdolly.domain.AxleGroup;
import io.github.rkaushikdev.jeepdolly.domain.AxleStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * Everything the weight rules say about one stream on one road.
 *
 * <p>Four rules speak to weight and a load has to answer all four: the gross limit for the road,
 * the group limits off the statutory table, the bridge formula over every run of axles, and the tire
 * rule for each axle on its own. They are read together because a refusal has to name all of them
 * at once. A hauler told his tandem is over, who moves a thousand pounds back and is then told his
 * tridem is over, is a hauler who calls his legislator, and he is right to. One reading, every
 * finding, one letter.
 *
 * <p>A seasonal order scales the group limits and the gross together before anything is compared.
 * It does not scale the formula: the formula is about bridges and the bridges did not thaw. That
 * asymmetry is the thing to remember here.
 *
 * @param order the seasonal order in force while the stream was read, if one was
 */
public record WeightReading(
        Pounds gross,
        Pounds grossLimit,
        List<GroupFinding> groups,
        List<Finding> formula,
        List<TireFinding> tires,
        Optional<SeasonOrder> order) {

    public WeightReading {
        groups = List.copyOf(groups);
        formula = List.copyOf(formula);
        tires = List.copyOf(tires);
    }

    /** Read a stream against a table with no seasonal order running. */
    public static WeightReading read(AxleStream stream, StatutoryTable table) {
        return read(stream, table, Optional.empty());
    }

    /** Read a stream against a table, with a seasonal order if one is running. */
    public static WeightReading read(AxleStream stream, StatutoryTable table, Optional<SeasonOrder> order) {
        UnaryOperator<Pounds> scale =
                order.<UnaryOperator<Pounds>>map(one -> one::appliedTo).orElse(UnaryOperator.identity());
        Pounds grossLimit = scale.apply(table.gross());
        List<GroupFinding> groups = new ArrayList<>();
        List<AxleGroup> found = stream.groups();
        for (int position = 0; position < found.size(); position++) {
            AxleGroup group = found.get(position);
            groups.add(new GroupFinding(position, group, scale.apply(table.groupLimit(group.count()))));
        }
        List<TireFinding> tires = new ArrayList<>();
        TireRule rule = table.tireRule();
        List<Axle> axles = stream.axles();
        for (int position = 0; position < axles.size(); position++) {
            tires.add(rule.read(position, axles.get(position)));
        }
        return new WeightReading(stream.gross(), grossLimit, groups, Formula.measure(stream, grossLimit), tires, order);
    }

    /** How far the gross is past the gross limit, never below zero. */
    public Pounds grossOver() {
        return gross().over(grossLimit);
    }

    /** The gross against the gross limit, as a finding like the others. */
    public GrossFinding grossFinding() {
        return new GrossFinding(gross, grossLimit);
    }

    public List<GroupFinding> groupFailures() {
        return groups.stream().filter(one -> !one.passes()).toList();
    }

    /** The groups whose axles do not share the load evenly, in stream order. */
    public List<GroupFinding> unequalGroups() {
        return groups.stream().filter(one -> !one.equalised()).toList();
    }

    public List<Finding> formulaFailures() {
        return formula.stream().filter(one -> !one.passes()).toList();
    }

    public List<TireFinding> tireFailures() {
        return tires.stream().filter(one -> !one.passes()).toList();
    }

    /** Every finding that does not pass, in the order the letter prints them: gross, groups, formula, tires. */
    public List<Overage> failures() {
        return Stream.of(List.of(grossFinding()), groups, formula, tires)
                .flatMap(List::stream)
                .map(Overage.class::cast)
                .filter(one -> !one.passes())
                .toList();
    }

    /** Whether any of the four rules finds the stream over. */
    public boolean overweight() {
        return !failures().isEmpty();
    }

    public boolean legal() {
        return !overweight();
    }

    /** Every finding, in the order the letter prints them: the gross, groups, formula, tires. */
    public List<String> reasons() {
        return failures().stream().map(Overage::toString).toList();
    }

    /**
     * The finding furthest over, the first of them on a tie, which is the one the fee is priced on
     * and the one the counter reads out first. Nothing for a legal reading.
     */
    public Optional<Overage> governing() {
        return failures().stream().max(Comparator.comparingInt(one -> one.over().value()));
    }

    /** The largest single overage, the first of them on a tie, which is what the fee is priced on. */
    public Pounds worstOverage() {
        return governing().map(Overage::over).orElse(Pounds.ZERO);
    }

    /** As the counter says it: {@code overweight, worst overage 4,000 lb on group 3, tridem ...}. */
    @Override
    public String toString() {
        return governing()
                .map(one -> "overweight, worst overage " + one.over() + " on " + one)
                .orElse("legal weight");
    }
}
