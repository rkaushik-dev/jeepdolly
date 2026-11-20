package io.github.rkaushikdev.jeepdolly;

import java.util.Map;

/**
 * The description contradicts itself before any rule is applied.
 *
 * <p>Overhangs longer than the combination, an order that ends before it starts, a stream with
 * more axles than the equipment lists.
 */
public class Impossible extends Refusal {

    /** The stable code, which goes in the letter, the log and the http body. */
    public static final String CODE = "impossible";

    /** A refusal with nothing behind it but the sentence. */
    public Impossible(String message) {
        super(message);
    }

    /** A refusal and the facts behind it, in the order given. */
    public Impossible(String message, Map<String, ?> facts) {
        super(message, facts);
    }

    @Override
    public String code() {
        return CODE;
    }
}
