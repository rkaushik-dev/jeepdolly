package io.github.rkaushikdev.jeepdolly.domain;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Written;
import java.util.Locale;
import java.util.Optional;

/**
 * One line of the commodity table: what is on the trailer, and whether it had to be.
 *
 * <p>The first question the counter asks is not how heavy. It is whether the load could have been
 * made legal by loading it differently. An excavator is one piece and cannot be cut in half. Forty
 * tons of gravel could have been two loads, so the answer is no. Round wood, baled forage and a few
 * others move under a standing allowance at a reduced overweight, because the legislature decided
 * the alternative was another thousand trucks on the same roads; a commodity that carries an
 * allowance is still divisible, it just has somewhere else to go.
 *
 * <p>A commodity also carries the flags later rules read: a tip limit for a load that needs the
 * route checked for superelevation, and a high centre of gravity that refuses a wind exemption.
 *
 * @param divisible the load could have been split
 * @param allowance the standing order that lets a divisible load move anyway, only meaningful on a
 *     divisible commodity
 */
public record Commodity(
        String code,
        String description,
        Family family,
        boolean divisible,
        Optional<String> allowance,
        Optional<Integer> tipLimitDegrees,
        boolean highCentre) {

    /** The longest description that fits on the face of the permit. */
    public static final int DESCRIPTION_LENGTH = 60;

    public Commodity {
        if (code.isEmpty() || !isUpper(code)) {
            throw new Impossible("commodity code " + Written.quoted(code) + " is not a code");
        }
        if (description.length() > DESCRIPTION_LENGTH) {
            throw new Impossible("description of " + code + " will not fit on a permit");
        }
        if (allowance.filter(one -> !one.isEmpty()).isPresent() && !divisible) {
            throw new Impossible(code + " is not divisible and needs no allowance");
        }
        if (tipLimitDegrees.isPresent() && (tipLimitDegrees.get() < 1 || tipLimitDegrees.get() > 45)) {
            throw new Impossible("tip limit on " + code + " is not a plausible angle");
        }
    }

    /** An indivisible commodity with no flags on it. */
    public Commodity(String code, String description, Family family) {
        this(code, description, family, false, Optional.empty(), Optional.empty(), false);
    }

    private static boolean isUpper(String text) {
        return text.chars().anyMatch(Character::isLetter) && text.equals(text.toUpperCase(Locale.ROOT));
    }

    /** Start a commodity line; divisibility, allowance and flags are set on the builder. */
    public static Builder builder(String code, String description, Family family) {
        return new Builder(code, description, family);
    }

    /** Whether an oversize or overweight permit can be written at all. */
    public boolean permittable() {
        return !divisible || allowance.isPresent();
    }

    /** A divisible load moving only because an allowance says it may. */
    public boolean restrictedToAllowance() {
        return divisible && allowance.isPresent();
    }

    /** As the permit face prints it: {@code XFMR power transformer}. */
    @Override
    public String toString() {
        return code + " " + description;
    }

    /** The optional parts of a commodity line, set by name the way the table is typed in. */
    public static final class Builder {

        private final String code;
        private final String description;
        private final Family family;
        private boolean divisible;
        private String allowance;
        private Integer tipLimitDegrees;
        private boolean highCentre;

        private Builder(String code, String description, Family family) {
            this.code = code;
            this.description = description;
            this.family = family;
        }

        public Builder divisible(boolean value) {
            this.divisible = value;
            return this;
        }

        public Builder allowance(String value) {
            this.allowance = value;
            return this;
        }

        public Builder tipLimitDegrees(Integer value) {
            this.tipLimitDegrees = value;
            return this;
        }

        public Builder highCentre(boolean value) {
            this.highCentre = value;
            return this;
        }

        /** @throws Impossible when the line contradicts itself */
        public Commodity build() {
            return new Commodity(
                    code,
                    description,
                    family,
                    divisible,
                    Optional.ofNullable(allowance),
                    Optional.ofNullable(tipLimitDegrees),
                    highCentre);
        }
    }
}
