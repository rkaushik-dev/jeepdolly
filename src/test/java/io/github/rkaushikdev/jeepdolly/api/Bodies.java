package io.github.rkaushikdev.jeepdolly.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The excavator move as a request body, every part of it editable, and the steps the route tests
 * take with it: filed, submitted, issued.
 */
public final class Bodies {

    /** The day the move is filed and issued on. */
    public static final String FILED = "2025-04-07";

    private Bodies() {}

    /** A map from alternating keys and values, editable afterwards. */
    public static Map<String, Object> map(Object... pairs) {
        Map<String, Object> made = new LinkedHashMap<>();
        for (int at = 0; at < pairs.length; at += 2) {
            made.put((String) pairs[at], pairs[at + 1]);
        }
        return made;
    }

    /** The day cab and three axle lowboy with the excavator on it. */
    public static Map<String, Object> application() {
        return map(
                "usdot",
                1204471,
                "commodity",
                "EXCV",
                "origin",
                "waterville",
                "destination",
                "pittsfield",
                "start",
                "2025-04-08",
                "end",
                "2025-04-10",
                "applicant",
                map("name", "Dana Bickford", "phone", "207 555 0143"),
                "envelope",
                map("width", "12'", "height", "13'6", "length", "82 ft", "rear_overhang", "6'"),
                "axles",
                new ArrayList<>(List.of(
                        map("kind", "steer", "weight", "12000", "tires", 2, "tire_width", "16"),
                        map("kind", "drive", "weight", "17000", "spacing_before", "200"),
                        map("kind", "drive", "weight", "17000", "spacing_before", "54"),
                        map("kind", "trailer", "weight", "16000", "spacing_before", "180"),
                        map("kind", "trailer", "weight", "16000", "spacing_before", "52"),
                        map("kind", "trailer", "weight", "16000", "spacing_before", "52"))),
                "units",
                new ArrayList<>(List.of(
                        map("kind", "tractor", "axles", 3, "plate", "ME 4471", "tare", "19000"),
                        map("kind", "lowboy", "axles", 3, "plate", "ME 7712", "tare", "24000"))),
                "pieces",
                new ArrayList<>(List.of(map("commodity", "EXCV", "description", "Cat 336 excavator"))));
    }

    /** The same move with gravel on the trailer, which could have been two loads. */
    public static Map<String, Object> gravel() {
        Map<String, Object> written = application();
        written.put("commodity", "GRVL");
        written.put("pieces", List.of(map("commodity", "GRVL", "description", "gravel")));
        return written;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> part(Map<String, Object> body, String key) {
        return (Map<String, Object>) body.get(key);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> item(Map<String, Object> body, String key, int index) {
        return (Map<String, Object>) ((List<Object>) body.get(key)).get(index);
    }

    /** File the body and answer what the office said. */
    public static Http.Answer file(Http client, Map<String, Object> body) {
        return client.postJson("/applications?filed=" + FILED, body);
    }

    /** File the body and answer the reference it was given. */
    public static String filed(Http client, Map<String, Object> body) {
        return file(client, body).json().get("reference").asText();
    }

    /** File and submit the body, and answer its reference. */
    public static String submitted(Http client, Map<String, Object> body) {
        String reference = filed(client, body);
        client.post("/applications/" + reference + "/submit?on=" + FILED);
        return reference;
    }

    /** File, submit and issue the body, and answer what the office said to the issue. */
    public static Http.Answer issue(Http client, Map<String, Object> body) {
        String reference = submitted(client, body);
        return client.post("/permits?reference=" + reference + "&on=" + FILED);
    }

    /** File, submit and issue the excavator move, and answer its permit number. */
    public static String issued(Http client) {
        return issue(client, application()).json().get("number").asText();
    }
}
