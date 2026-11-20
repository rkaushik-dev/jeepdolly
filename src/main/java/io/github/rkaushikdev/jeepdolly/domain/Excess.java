package io.github.rkaushikdev.jeepdolly.domain;

import io.github.rkaushikdev.jeepdolly.Inches;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** How far past a limit each measure of an envelope runs. Zero where it is inside. */
public record Excess(Inches width, Inches height, Inches length, Inches frontOverhang, Inches rearOverhang) {

    /** Nothing over anywhere. */
    public static final Excess NONE = new Excess(Inches.ZERO, Inches.ZERO, Inches.ZERO, Inches.ZERO, Inches.ZERO);

    /**
     * One measure and how far over it runs.
     *
     * @param name the measure as the form names it, such as {@code front_overhang}
     */
    public record Over(String name, Inches amount) {}

    /** One measure by the name the form gives it. */
    public Inches measure(String name) {
        return switch (name) {
            case "width" -> width;
            case "height" -> height;
            case "length" -> length;
            case "front_overhang" -> frontOverhang;
            case "rear_overhang" -> rearOverhang;
            default -> throw new IllegalArgumentException("an excess has no " + name);
        };
    }

    /** Whether anything at all is past the limit. */
    public boolean any() {
        return !over().isEmpty();
    }

    /** Which measures are past the limit, in form order. */
    public List<String> over() {
        return Envelope.MEASURES.stream()
                .filter(name -> measure(name).value() != 0)
                .toList();
    }

    /** The measure furthest past its limit, for the refusal letter. The first wins a tie. */
    public Optional<Over> worst() {
        return over().stream()
                .map(name -> new Over(name, measure(name)))
                .max(Comparator.comparingInt(one -> one.amount().value()));
    }

    /** As the letter writes it: {@code width over by 1 ft 6 in, rear overhang over by 4 ft}. */
    @Override
    public String toString() {
        if (!any()) {
            return "within limits";
        }
        return over().stream()
                .map(name -> name.replace('_', ' ') + " over by " + measure(name))
                .collect(Collectors.joining(", "));
    }
}
