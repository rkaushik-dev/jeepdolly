package io.github.rkaushikdev.jeepdolly.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * A small client over MockMvc for the API tests: send a request, get the status and the parsed body
 * back. A path may carry its own query string, written the way a browser would send it.
 */
public final class Http {

    static final ObjectMapper JSON = Json.mapper();

    private final MockMvc mvc;
    private final String key;

    public Http(MockMvc mvc) {
        this(mvc, null);
    }

    private Http(MockMvc mvc, String key) {
        this.mvc = mvc;
        this.key = key;
    }

    /** The same client, sending {@code key} on every request. */
    public Http as(String key) {
        return new Http(mvc, key);
    }

    /** The key this client sends, if it sends one. */
    public Optional<String> key() {
        return Optional.ofNullable(key);
    }

    public Answer get(String path) {
        return send(MockMvcRequestBuilders.get(path));
    }

    public Answer post(String path) {
        return send(MockMvcRequestBuilders.post(path));
    }

    public Answer postJson(String path, Object body) {
        return send(MockMvcRequestBuilders.post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(write(body)));
    }

    public Answer putJson(String path, Object body) {
        return send(MockMvcRequestBuilders.put(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(write(body)));
    }

    public Answer delete(String path) {
        return send(MockMvcRequestBuilders.delete(path));
    }

    /** Send a request built by hand, with this client's key. */
    public Answer send(MockHttpServletRequestBuilder request) {
        if (key != null) {
            request.header(Security.HEADER, key);
        }
        try {
            return new Answer(mvc.perform(request).andReturn());
        } catch (Exception failed) {
            throw new IllegalStateException("the request did not complete", failed);
        }
    }

    /** A JSON object as a map, to compare whole. */
    public static Map<String, Object> asMap(JsonNode tree) {
        return JSON.convertValue(tree, new TypeReference<Map<String, Object>>() {});
    }

    static String write(Object body) {
        try {
            return JSON.writeValueAsString(body);
        } catch (JsonProcessingException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }

    /** What came back. */
    public record Answer(MvcResult result) {

        public int status() {
            return result.getResponse().getStatus();
        }

        public String text() {
            return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        }

        public JsonNode json() {
            try {
                return JSON.readTree(text());
            } catch (JsonProcessingException unreadable) {
                throw new UncheckedIOException(unreadable);
            }
        }

        public String header(String name) {
            return result.getResponse().getHeader(name);
        }

        public String contentType() {
            return header(HttpHeaders.CONTENT_TYPE);
        }
    }
}
