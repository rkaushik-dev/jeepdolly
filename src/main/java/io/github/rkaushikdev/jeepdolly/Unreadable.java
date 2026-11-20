package io.github.rkaushikdev.jeepdolly;

import java.util.Map;

/**
 * What was written cannot be read as what it claims to be.
 *
 * <p>A size written as "about fourteen feet", a route nobody can find on the road log, an amount
 * with three places after the point.
 */
public class Unreadable extends Refusal {

    /** The stable code, which goes in the letter, the log and the http body. */
    public static final String CODE = "unreadable";

    /** A refusal with nothing behind it but the sentence. */
    public Unreadable(String message) {
        super(message);
    }

    /** A refusal and the facts behind it, in the order given. */
    public Unreadable(String message, Map<String, ?> facts) {
        super(message, facts);
    }

    @Override
    public String code() {
        return CODE;
    }
}
