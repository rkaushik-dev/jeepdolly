package io.github.rkaushikdev.jeepdolly.conditions;

import io.github.rkaushikdev.jeepdolly.Conflict;
import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.NotOnFile;
import io.github.rkaushikdev.jeepdolly.Written;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The condition library: every sentence that can print on a permit.
 *
 * <p>A permit condition is a legal instrument. It is what an enforcement officer reads at the
 * roadside and it is what a court reads afterwards, so the office holds the words in one place,
 * gives each one a code, and never writes one freehand at the counter. The rules that fire produce
 * codes and facts. Nothing upstream of the library holds a sentence, and nothing downstream of it
 * invents one.
 *
 * <p>Conditions print grouped by category and ordered within the group. The order is on the record
 * rather than in the printing code, so a change to the running order is a change to a table.
 */
public final class ConditionLibrary {

    /** Where the shipped wording lives on the class path. */
    public static final String STANDARD_RESOURCE = "/conditions/standard-wording.tsv";

    /** The office wording as it stands, which is what the printer reads. */
    public static final List<ConditionText> STANDARD_TEXTS = wording(STANDARD_RESOURCE);

    private static final Comparator<Condition> PRINTING = Comparator.comparing(Condition::category)
            .thenComparingInt(Condition::order)
            .thenComparing(Condition::code);

    private final Map<String, ConditionText> byCode = new LinkedHashMap<>();

    /** The library as it stands, which is what the printer reads. */
    public static ConditionLibrary standard() {
        ConditionLibrary library = new ConditionLibrary();
        STANDARD_TEXTS.forEach(library::add);
        return library;
    }

    /**
     * Read a wording file: one condition to a line, code, category, order and text separated by
     * tabs, with blank lines and lines starting {@code #} left out.
     *
     * @throws Impossible when the file is missing or a line of it is not a condition
     */
    public static List<ConditionText> wording(String resource) {
        InputStream found = ConditionLibrary.class.getResourceAsStream(resource);
        if (found == null) {
            throw new Impossible("no condition wording at " + resource);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(found, StandardCharsets.UTF_8))) {
            return reader.lines()
                    .filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .map(ConditionLibrary::wordingLine)
                    .toList();
        } catch (IOException trouble) {
            throw new UncheckedIOException(trouble);
        }
    }

    /**
     * One line of a wording file as a condition.
     *
     * @throws Impossible when it does not have the four fields
     */
    public static ConditionText wordingLine(String line) {
        String[] fields = line.split("\t", -1);
        if (fields.length != 4 || !fields[2].matches("\\d+")) {
            throw new Impossible("a wording line is code, category, order and text: " + Written.quoted(line));
        }
        return new ConditionText(fields[0], Category.of(fields[1]), fields[3], Integer.parseInt(fields[2]));
    }

    /**
     * This library with some of its wording replaced, code by code, by wording the office has put
     * on file since. A code the library does not hold is added, so an order can bring in a new one.
     */
    public ConditionLibrary revised(List<ConditionText> replacements) {
        ConditionLibrary made = new ConditionLibrary();
        Map<String, ConditionText> kept = new LinkedHashMap<>(byCode);
        replacements.forEach(one -> kept.put(one.code(), one));
        kept.values().forEach(made::add);
        return made;
    }

    /**
     * Group by category in printing order, then by the order on the record, then by code.
     *
     * <p>{@link Category} is declared in printing order, so its natural order is the permit face's.
     */
    public static List<Condition> sortConditions(Collection<Condition> conditions) {
        return conditions.stream().sorted(PRINTING).toList();
    }

    /** @throws Conflict when the code is already in the library */
    public ConditionText add(ConditionText condition) {
        if (byCode.containsKey(condition.code())) {
            throw new Conflict("condition " + condition.code() + " is already in the library");
        }
        byCode.put(condition.code(), condition);
        return condition;
    }

    /** @throws NotOnFile when no condition carries the code */
    public ConditionText get(String code) {
        ConditionText found = byCode.get(code);
        if (found == null) {
            throw new NotOnFile("no condition " + code, Facts.of("code", code));
        }
        return found;
    }

    /** A condition with no slots, ready to print. */
    public Condition render(String code) {
        return render(code, Map.of());
    }

    /**
     * A condition with its slots filled from the facts, ready to print.
     *
     * @throws NotOnFile when no condition carries the code
     * @throws io.github.rkaushikdev.jeepdolly.Impossible when a slot is not filled
     */
    public Condition render(String code, Map<String, ?> facts) {
        ConditionText found = get(code);
        return new Condition(found.code(), found.category(), found.render(facts), found.order());
    }

    /** One category, in the order it prints. */
    public List<ConditionText> ofCategory(Category category) {
        return byCode.values().stream()
                .filter(one -> one.category() == category)
                .sorted(Comparator.comparingInt(ConditionText::order).thenComparing(ConditionText::code))
                .toList();
    }

    /** Every code, sorted, for the printed appendix. */
    public List<String> codes() {
        return byCode.keySet().stream().sorted().toList();
    }

    public int size() {
        return byCode.size();
    }

    public boolean contains(String code) {
        return byCode.containsKey(code);
    }
}
