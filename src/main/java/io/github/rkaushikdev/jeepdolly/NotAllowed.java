package io.github.rkaushikdev.jeepdolly;

import java.util.Map;

/**
 * The state of the document, or the person asking, forbids it.
 *
 * <p>A void permit cannot be amended, and a submitted application cannot be edited.
 */
public class NotAllowed extends Refusal {

    /** The stable code, which goes in the letter, the log and the http body. */
    public static final String CODE = "not_allowed";

    /** A refusal with nothing behind it but the sentence. */
    public NotAllowed(String message) {
        super(message);
    }

    /** A refusal and the facts behind it, in the order given. */
    public NotAllowed(String message, Map<String, ?> facts) {
        super(message, facts);
    }

    @Override
    public String code() {
        return CODE;
    }
}
