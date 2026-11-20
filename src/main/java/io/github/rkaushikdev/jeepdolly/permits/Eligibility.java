package io.github.rkaushikdev.jeepdolly.permits;

import java.util.List;

/**
 * Whether a combination may hold an annual permit, and every reason why not.
 *
 * @param reasons why not, in the order the envelope, the weight and the commodity were read; empty
 *     when it is allowed
 */
public record Eligibility(boolean allowed, List<String> reasons) {

    public Eligibility {
        reasons = List.copyOf(reasons);
    }

    @Override
    public String toString() {
        if (allowed) {
            return "eligible for an annual permit";
        }
        return "not eligible: " + String.join("; ", reasons);
    }
}
