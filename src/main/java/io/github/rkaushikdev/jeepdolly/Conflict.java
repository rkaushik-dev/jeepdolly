package io.github.rkaushikdev.jeepdolly;

import java.util.Map;

/**
 * It collides with something already on file.
 *
 * <p>A second record under a number already taken, or two orders over the same days.
 */
public class Conflict extends Refusal {

    /** The stable code, which goes in the letter, the log and the http body. */
    public static final String CODE = "conflict";

    /** A refusal with nothing behind it but the sentence. */
    public Conflict(String message) {
        super(message);
    }

    /** A refusal and the facts behind it, in the order given. */
    public Conflict(String message, Map<String, ?> facts) {
        super(message, facts);
    }

    @Override
    public String code() {
        return CODE;
    }
}
