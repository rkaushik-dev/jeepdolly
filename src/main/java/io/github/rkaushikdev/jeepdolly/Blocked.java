package io.github.rkaushikdev.jeepdolly;

import java.util.Map;

/**
 * The road, the season or the clock forbids it.
 *
 * <p>A closure, a thaw order, a curfew or a holiday travel ban stands in the way.
 */
public class Blocked extends Refusal {

    /** The stable code, which goes in the letter, the log and the http body. */
    public static final String CODE = "blocked";

    /** A refusal with nothing behind it but the sentence. */
    public Blocked(String message) {
        super(message);
    }

    /** A refusal and the facts behind it, in the order given. */
    public Blocked(String message, Map<String, ?> facts) {
        super(message, facts);
    }

    @Override
    public String code() {
        return CODE;
    }
}
