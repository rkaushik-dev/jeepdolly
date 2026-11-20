package io.github.rkaushikdev.jeepdolly;

import java.util.Map;

/**
 * Something is referred to that the office does not have.
 *
 * <p>A carrier, a segment, a permit number or a commodity code the office has no record of.
 */
public class NotOnFile extends Refusal {

    /** The stable code, which goes in the letter, the log and the http body. */
    public static final String CODE = "not_on_file";

    /** A refusal with nothing behind it but the sentence. */
    public NotOnFile(String message) {
        super(message);
    }

    /** A refusal and the facts behind it, in the order given. */
    public NotOnFile(String message, Map<String, ?> facts) {
        super(message, facts);
    }

    @Override
    public String code() {
        return CODE;
    }
}
