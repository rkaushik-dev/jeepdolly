package io.github.rkaushikdev.jeepdolly.escorts;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The pilot cars a hauler named, checked against the register for the day of travel.
 *
 * <p>The escort table says a load needs two pilot cars. Naming them is a separate thing and it
 * happens later, usually the day before the move, because a carrier does not know in the morning
 * who is free on Thursday. So naming is optional: an application with no cards on it is assessed,
 * priced and issued, and the permit prints {@link #NOT_NAMED}.
 *
 * <p>What is not optional is that a card, once named, is good. A card not on the register, a card
 * not good on the day of travel, and a set of cards with nobody endorsed for a height pole the load
 * needs are all refusals rather than conditions. Naming more cards than the load needs is allowed
 * and always has been.
 *
 * @param named every card the hauler wrote, in the order written
 * @param good the named cards that passed, in the order written
 * @param wanted how many pilot cars the escort table asked for
 */
public record Naming(List<String> named, List<Operator> good, List<String> refusals, int wanted) {

    /** What the permit prints when nobody has been named yet. */
    public static final String NOT_NAMED = "pilot cars not named, tell the office before the load moves";

    public Naming {
        named = List.copyOf(named);
        good = List.copyOf(good);
        refusals = List.copyOf(refusals);
    }

    /** Read every named card against the register for the day of travel. */
    public static Naming check(EscortRegister register, List<String> cards, Escorts escorts, LocalDate day) {
        List<Operator> good = new ArrayList<>();
        List<String> refusals = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String card : cards) {
            if (!seen.add(card)) {
                refusals.add("pilot car card " + card + " is named twice");
                continue;
            }
            Optional<String> trouble = troubleWith(register, card, day);
            if (trouble.isPresent()) {
                refusals.add(trouble.get());
                continue;
            }
            good.add(register.operator(card));
        }
        if (escorts.heightPole()
                && !good.isEmpty()
                && good.stream().noneMatch(one -> one.carries(Endorsement.HEIGHT_POLE))) {
            refusals.add("this load needs a height pole and no card named carries the endorsement");
        }
        return new Naming(cards, good, refusals, escorts.total());
    }

    /** What is wrong with one card on the day of travel, or nothing. */
    private static Optional<String> troubleWith(EscortRegister register, String card, LocalDate day) {
        if (!register.contains(card)) {
            return Optional.of("pilot car card " + card + " is not on the register");
        }
        Standing standing = register.standing(card, day);
        if (!standing.available()) {
            return Optional.of("pilot car card " + card + " is " + standing + " on " + day);
        }
        return Optional.empty();
    }

    /** Whether enough good cards were named to cover the escorts wanted. */
    public boolean complete() {
        return good.size() >= wanted;
    }

    /** Whether nothing named is a refusal. Naming nobody is acceptable. */
    public boolean acceptable() {
        return refusals.isEmpty();
    }

    /** How many more good cards the load wants, which is never below zero. */
    public int shortfall() {
        return Math.max(0, wanted - good.size());
    }

    /** The line that goes on the permit face: who is piloting, or {@link #NOT_NAMED}. */
    public String line() {
        if (named.isEmpty()) {
            return NOT_NAMED;
        }
        return good.stream().map(one -> one.name() + " (" + one.card() + ")").collect(Collectors.joining(", "));
    }

    @Override
    public String toString() {
        if (!refusals.isEmpty()) {
            return refusals.size() + " of " + named.size() + " named cards are no good";
        }
        if (named.isEmpty()) {
            return "no pilot cars named";
        }
        return good.size() + " of " + wanted + " named";
    }
}
