package io.github.rkaushikdev.jeepdolly.limits;

import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.domain.Span;
import java.util.Optional;

/**
 * One run of axles measured against the bridge formula.
 *
 * @param allowed what the formula, or the exception, lets the run carry
 * @param exception the rule that let the run through when the formula alone would not, so a report
 *     can say which rule it was
 */
public record Finding(Span span, Pounds allowed, Optional<String> exception) {

    /** A finding the formula decided on its own. */
    public Finding(Span span, Pounds allowed) {
        this(span, allowed, Optional.empty());
    }

    public Pounds weight() {
        return span.weight();
    }

    /** How far past what is allowed the run is, never below zero. */
    public Pounds over() {
        return weight().over(allowed);
    }

    public boolean passes() {
        return over().value() == 0;
    }

    /** The run, the allowance and the verdict, as a refusal letter lists them. */
    @Override
    public String toString() {
        String verdict = passes() ? "within" : "over by " + over();
        String note = exception
                .filter(one -> !one.isEmpty())
                .map(one -> " under the " + one)
                .orElse("");
        return span + ", allowed " + allowed + ", " + verdict + note;
    }
}
