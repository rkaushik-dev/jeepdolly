package io.github.rkaushikdev.jeepdolly;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A deliberate no, with a stable code and the facts behind it.
 *
 * <p>A permit office says no a great deal, and it has to say no the same way twice. The hauler gets
 * a letter, the clerk gets a line on the screen, the carrier's software gets a code, and all three
 * have to be the same refusal or somebody will argue that they were told two different things.
 *
 * <p>So every refusal in this system is a {@code Refusal} with a code on the class. The code is a
 * stable string. It goes in the letter, in the log, and in the http body, and it does not change
 * when the wording does. There are seven of them and that is deliberate: a refusal a clerk cannot
 * sort into one of these is a refusal nobody can act on.
 *
 * <ul>
 *   <li>{@link Unreadable} what was written cannot be read as what it claims to be
 *   <li>{@link Impossible} the description contradicts itself before any rule is applied
 *   <li>{@link NotOnFile} something is referred to that the office does not have
 *   <li>{@link Conflict} it collides with something already on file
 *   <li>{@link OverLimit} it is past a published limit
 *   <li>{@link Blocked} the road, the season or the clock forbids it
 *   <li>{@link NotAllowed} the state of the document or the person asking forbids it
 * </ul>
 *
 * <p>A programming mistake is not on this list on purpose. Handing a bare integer where a weight
 * belongs is not a refusal, it is a defect in the caller, and the compiler or an {@link
 * IllegalArgumentException} says so rather than a letter to a hauler.
 */
public class Refusal extends RuntimeException {

    /** The code of the root, which no concrete refusal carries. */
    public static final String CODE = "refused";

    /** Every refusal the system can raise, by code, for the api error table. */
    public static final Map<String, Class<? extends Refusal>> BY_CODE = byCode();

    private final transient Map<String, Object> facts;

    /** A refusal with nothing behind it but the sentence. */
    public Refusal(String message) {
        this(message, Map.of());
    }

    /** A refusal and the facts behind it, in the order given. Null values are kept. */
    public Refusal(String message, Map<String, ?> facts) {
        super(message);
        this.facts = Collections.unmodifiableMap(new LinkedHashMap<>(facts));
    }

    /** The stable code for this kind of refusal. Every concrete refusal overrides it. */
    public String code() {
        return CODE;
    }

    /** The facts behind the sentence, in the order they were given. Read only. */
    public Map<String, Object> facts() {
        return facts;
    }

    /**
     * The shape the http layer and the log both write: code, message and facts.
     *
     * <p>The maps are fresh copies, so a caller that edits the body cannot reach back into the
     * refusal.
     */
    public Map<String, Object> asMap() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code());
        body.put("message", getMessage());
        body.put("facts", new LinkedHashMap<>(facts));
        return body;
    }

    /** The code, then the sentence, as a clerk reads it on the screen. */
    @Override
    public String toString() {
        return code() + ": " + getMessage();
    }

    private static Map<String, Class<? extends Refusal>> byCode() {
        Map<String, Class<? extends Refusal>> table = new LinkedHashMap<>();
        table.put(Unreadable.CODE, Unreadable.class);
        table.put(Impossible.CODE, Impossible.class);
        table.put(NotOnFile.CODE, NotOnFile.class);
        table.put(Conflict.CODE, Conflict.class);
        table.put(OverLimit.CODE, OverLimit.class);
        table.put(Blocked.CODE, Blocked.class);
        table.put(NotAllowed.CODE, NotAllowed.class);
        return Collections.unmodifiableMap(table);
    }
}
