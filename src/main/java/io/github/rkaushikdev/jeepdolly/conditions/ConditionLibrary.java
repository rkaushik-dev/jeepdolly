package io.github.rkaushikdev.jeepdolly.conditions;

import io.github.rkaushikdev.jeepdolly.Conflict;
import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.NotOnFile;
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

    /** The office wording as it stands, which is what the printer reads. */
    public static final List<ConditionText> STANDARD_TEXTS = List.of(
            new ConditionText("T-01", Category.TRAVEL, "Valid from {start} to {end} inclusive.", 10),
            new ConditionText("T-02", Category.TRAVEL, "Daylight hours only, half an hour either side.", 20),
            new ConditionText("T-03", Category.TRAVEL, "No movement Saturday, Sunday or a legal holiday.", 30),
            new ConditionText("T-04", Category.TRAVEL, "No movement during the {holiday} travel ban.", 40),
            new ConditionText("T-05", Category.TRAVEL, "No movement on {segment} between {curfew}.", 50),
            new ConditionText("T-06", Category.TRAVEL, "Stop and park when the wind reaches {wind} mph.", 60),
            new ConditionText("T-07", Category.TRAVEL, "Stop when visibility falls below five hundred feet.", 70),
            new ConditionText("R-01", Category.ROUTE, "Route as written. Any deviation voids this permit.", 10),
            new ConditionText("R-02", Category.ROUTE, "{segment} is posted at {limit} while order {order} runs.", 20),
            new ConditionText("R-03", Category.ROUTE, "Do not enter {segment}. Use {detour}.", 30),
            new ConditionText("R-04", Category.ROUTE, "Obey the posted clearance of {clearance} at {structure}.", 40),
            new ConditionText("R-05", Category.ROUTE, "Stop traffic both ways to turn onto {segment} at {place}.", 50),
            new ConditionText("R-06", Category.ROUTE, "The turn at {place} allows {allowed}. Walk it first.", 60),
            new ConditionText("U-01", Category.ROUTE, "Line lift at {place}. Ring {owner} on {telephone}.", 70),
            new ConditionText("U-02", Category.ROUTE, "Do not move until every line lift is confirmed.", 80),
            new ConditionText("S-01", Category.STRUCTURE, "Cross {structure} alone with no other vehicle on it.", 10),
            new ConditionText("S-02", Category.STRUCTURE, "Cross {structure} at walking pace.", 20),
            new ConditionText("S-03", Category.STRUCTURE, "Cross {structure} centred on the roadway.", 30),
            new ConditionText("S-04", Category.STRUCTURE, "Do not stop on {structure} for any reason.", 40),
            new ConditionText("E-01", Category.ESCORT, "{count} certified pilot car ahead of the load.", 10),
            new ConditionText("E-02", Category.ESCORT, "{count} certified pilot car behind the load.", 20),
            new ConditionText("E-03", Category.ESCORT, "Front pilot car carries a height pole set one inch high.", 30),
            new ConditionText("E-04", Category.ESCORT, "Police escort, arranged by the carrier.", 40),
            new ConditionText("E-05", Category.ESCORT, "Pilot cars for this move: {cards}.", 50),
            new ConditionText("E-06", Category.ESCORT, "Tell the office who is piloting before the load moves.", 60),
            new ConditionText("M-01", Category.MARKING, "OVERSIZE LOAD banners front and rear.", 10),
            new ConditionText(
                    "M-02", Category.MARKING, "{count} eighteen inch red flags on corners and widest points.", 20),
            new ConditionText("M-03", Category.MARKING, "{count} amber lamps in place of flags.", 30),
            new ConditionText("M-04", Category.MARKING, "{count} amber beacons on the power unit.", 40),
            new ConditionText(
                    "Q-01", Category.EQUIPMENT, "Axle spacings as declared. Any change voids this permit.", 10),
            new ConditionText(
                    "Q-02", Category.EQUIPMENT, "All liftable axles down and carrying for the whole move.", 20),
            new ConditionText("Q-03", Category.EQUIPMENT, "Tires as declared, no fewer and no narrower.", 30),
            new ConditionText(
                    "G-01", Category.GENERAL, "Carry this permit in the power unit and produce it on demand.", 10),
            new ConditionText("G-02", Category.GENERAL, "The carrier is liable for damage to any structure.", 20),
            new ConditionText(
                    "G-03", Category.GENERAL, "This permit does not waive any other law or local order.", 30));

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
