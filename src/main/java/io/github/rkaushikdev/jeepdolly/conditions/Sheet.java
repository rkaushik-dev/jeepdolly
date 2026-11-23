package io.github.rkaushikdev.jeepdolly.conditions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The conditions of one permit, collected by code with the first facts.
 *
 * <p>A key is usually a library code and sometimes a code with a tail on it, written {@code
 * U-01/L-4}. The tail exists for the conditions that are printed more than once with different
 * facts each time: three line lifts are three conditions naming three places, and two bridges the
 * pilot cars hold traffic at are two, and collecting them by code alone would keep the first and
 * quietly lose the rest. {@link #addAt} writes the tail so no caller has to.
 *
 * <p>The tail is dropped when the library is asked for the wording. Nothing else in the system sees
 * it.
 */
public final class Sheet {

    private final ConditionLibrary library;
    private final List<String> codes = new ArrayList<>();
    private final Map<String, Map<String, Object>> facts = new LinkedHashMap<>();

    public Sheet(ConditionLibrary library) {
        this.library = library;
    }

    /** The library the sheet takes its wording from. */
    public ConditionLibrary library() {
        return library;
    }

    /** Add a condition with no slots, unless one under that key is already on the sheet. */
    public void add(String code) {
        add(code, Map.of());
    }

    /** Add a condition and its facts, unless one under that key is already on the sheet. */
    public void add(String code, Map<String, ?> given) {
        if (facts.containsKey(code)) {
            return;
        }
        codes.add(code);
        facts.put(code, new LinkedHashMap<>(given));
    }

    /** Add a condition once for each place it names, the place keeping it apart from the others. */
    public void addAt(String code, String place, Map<String, ?> given) {
        add(code + "/" + place, given);
    }

    /** Every condition on the sheet, worded and in printing order. */
    public List<Condition> rendered() {
        List<Condition> found = codes.stream()
                .map(code -> library.render(Assemble.base(code), facts.get(code)))
                .toList();
        return ConditionLibrary.sortConditions(found);
    }

    /** The keys on the sheet, in the order they were added. */
    public List<String> codes() {
        return List.copyOf(codes);
    }

    public int size() {
        return codes.size();
    }
}
