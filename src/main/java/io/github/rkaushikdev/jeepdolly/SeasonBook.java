package io.github.rkaushikdev.jeepdolly;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Every seasonal order on file, indexed the way the counter asks about them.
 *
 * <p>Two orders of the same kind may not overlap in one region, because then nobody can say which
 * limit is in force. A thaw and a frozen order may not overlap either, for the same reason and with
 * more force.
 */
public final class SeasonBook {

    private final List<SeasonOrder> orders = new ArrayList<>();

    /**
     * Put an order on file.
     *
     * @throws Conflict when the number is taken or the order overlaps another in its region
     */
    public SeasonOrder file(SeasonOrder order) {
        for (SeasonOrder existing : orders) {
            if (existing.number().equals(order.number())) {
                throw new Conflict(
                        "order " + order.number() + " is already on file", Facts.of("order", order.number()));
            }
            if (existing.region().equals(order.region()) && existing.overlaps(order)) {
                throw new Conflict(
                        "order " + order.number() + " overlaps " + existing.number() + " in " + order.region(),
                        Facts.of("order", order.number(), "against", existing.number()));
            }
        }
        orders.add(order);
        return order;
    }

    /** The order in force in a region on a day, if any is. */
    public Optional<SeasonOrder> inForce(String region, LocalDate day) {
        return orders.stream()
                .filter(order -> order.region().equals(region) && order.covers(day))
                .findFirst();
    }

    /** The statutory limit as the orders of the day leave it. */
    public Pounds limitOn(String region, LocalDate day, Pounds statutory) {
        return inForce(region, day).map(order -> order.appliedTo(statutory)).orElse(statutory);
    }

    /** True while a thaw order is cutting the limit in this region. */
    public boolean posted(String region, LocalDate day) {
        return inForce(region, day)
                .filter(order -> order.kind() == SeasonKind.THAW)
                .isPresent();
    }

    /** Every region with an order on file, sorted. */
    public List<String> regions() {
        TreeSet<String> found = new TreeSet<>();
        orders.forEach(order -> found.add(order.region()));
        return List.copyOf(found);
    }

    /** The orders of one region, in the order they run. */
    public List<SeasonOrder> ordersFor(String region) {
        return orders.stream()
                .filter(order -> order.region().equals(region))
                .sorted(Comparator.comparing(SeasonOrder::start))
                .toList();
    }

    public int size() {
        return orders.size();
    }

    @Override
    public String toString() {
        return "SeasonBook(" + orders.size() + " orders)";
    }
}
