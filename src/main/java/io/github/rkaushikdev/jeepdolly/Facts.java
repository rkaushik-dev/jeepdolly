package io.github.rkaushikdev.jeepdolly;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The named facts that travel beside a refusal's sentence or a log line's event, in the order they
 * were written.
 *
 * <p>{@link Map#of} would do, except that it forgets the order and refuses a null, and both a
 * refusal letter and a log line are read by people who expect the permit number before the weight
 * it was refused at.
 */
public final class Facts {

    private Facts() {}

    /** No facts at all. */
    public static Map<String, Object> none() {
        return Map.of();
    }

    public static Map<String, Object> of(String key, Object value) {
        return ordered(key, value);
    }

    public static Map<String, Object> of(String k1, Object v1, String k2, Object v2) {
        return ordered(k1, v1, k2, v2);
    }

    public static Map<String, Object> of(String k1, Object v1, String k2, Object v2, String k3, Object v3) {
        return ordered(k1, v1, k2, v2, k3, v3);
    }

    public static Map<String, Object> of(
            String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4) {
        return ordered(k1, v1, k2, v2, k3, v3, k4, v4);
    }

    public static Map<String, Object> of(
            String k1,
            Object v1,
            String k2,
            Object v2,
            String k3,
            Object v3,
            String k4,
            Object v4,
            String k5,
            Object v5) {
        return ordered(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5);
    }

    public static Map<String, Object> of(
            String k1,
            Object v1,
            String k2,
            Object v2,
            String k3,
            Object v3,
            String k4,
            Object v4,
            String k5,
            Object v5,
            String k6,
            Object v6) {
        return ordered(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6);
    }

    private static Map<String, Object> ordered(Object... pairs) {
        Map<String, Object> facts = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            facts.put((String) pairs[index], pairs[index + 1]);
        }
        return Collections.unmodifiableMap(facts);
    }
}
