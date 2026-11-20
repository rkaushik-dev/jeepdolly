package io.github.rkaushikdev.jeepdolly.conditions;

import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.Impossible;
import java.util.Map;

/**
 * One sentence that can print on a permit, its code, and where it prints.
 *
 * <p>A condition may take facts. The text carries named slots, written {@code {structure}}, and the
 * caller hands in the values. A missing slot is a refusal rather than a blank, because a permit
 * that reads "cross the bridge at" is worse than no permit at all.
 *
 * @param order where it prints within its category, lowest first
 */
public record ConditionText(String code, Category category, String text, int order) {

    /** Where a condition prints when nobody says otherwise. */
    public static final int ORDER = 50;

    public ConditionText {
        if (code.isBlank()) {
            throw new Impossible("a condition carries a code");
        }
        if (text.isBlank()) {
            throw new Impossible("condition " + code + " has no words in it");
        }
    }

    /** A condition that prints in the middle of its category. */
    public ConditionText(String code, Category category, String text) {
        this(code, category, text, ORDER);
    }

    /** The words as written, for a condition with no slots. */
    public String render() {
        return render(Map.of());
    }

    /**
     * Fill the slots, refusing rather than printing a gap.
     *
     * <p>A doubled brace prints as one brace. A value is printed the way it prints anywhere else; an
     * absent value prints as {@code None}, the word the office's sheets have always shown.
     *
     * @throws Impossible naming the first slot the facts do not fill
     */
    public String render(Map<String, ?> facts) {
        StringBuilder out = new StringBuilder(text.length());
        int index = 0;
        while (index < text.length()) {
            char one = text.charAt(index);
            if (one == '{' && index + 1 < text.length() && text.charAt(index + 1) == '{') {
                out.append('{');
                index += 2;
            } else if (one == '}' && index + 1 < text.length() && text.charAt(index + 1) == '}') {
                out.append('}');
                index += 2;
            } else if (one == '{' && text.indexOf('}', index) > index) {
                int close = text.indexOf('}', index);
                String slot = text.substring(index + 1, close);
                if (!facts.containsKey(slot)) {
                    throw new Impossible(
                            "condition " + code + " wants " + slot + " and was not given it",
                            Facts.of("code", code, "wants", slot));
                }
                Object value = facts.get(slot);
                out.append(value == null ? "None" : value.toString());
                index = close + 1;
            } else {
                out.append(one);
                index++;
            }
        }
        return out.toString();
    }

    /** As the appendix lists it: {@code T-02 (travel)}. */
    @Override
    public String toString() {
        return code + " (" + category + ")";
    }
}
