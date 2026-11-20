package io.github.rkaushikdev.jeepdolly.conditions;

/** A rendered condition, its slots filled, ready to print on the permit face. */
public record Condition(String code, Category category, String text, int order) {

    /** The words, which is all the permit face prints. */
    @Override
    public String toString() {
        return text;
    }
}
