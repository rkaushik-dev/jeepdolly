package io.github.rkaushikdev.jeepdolly;

import java.util.Map;

/**
 * It is past a published limit.
 *
 * <p>A weight, a size or a count the tables say no to.
 */
public class OverLimit extends Refusal {

    /** The stable code, which goes in the letter, the log and the http body. */
    public static final String CODE = "over_limit";

    /** A refusal with nothing behind it but the sentence. */
    public OverLimit(String message) {
        super(message);
    }

    /** A refusal and the facts behind it, in the order given. */
    public OverLimit(String message, Map<String, ?> facts) {
        super(message, facts);
    }

    @Override
    public String code() {
        return CODE;
    }
}
