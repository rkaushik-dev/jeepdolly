package io.github.rkaushikdev.jeepdolly.api;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Path;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A request that could not be read into the shape a route takes, answered 422.
 *
 * <p>It is not a refusal: nothing was decided about a load. The body says where each problem was
 * found, as a path that starts with {@code body}, {@code query} or {@code path}, and what was wrong
 * there:
 *
 * <pre>{"detail": [{"loc": ["body", "axles"], "msg": "List should have at least 1 item"}]}</pre>
 */
public class Rejected extends RuntimeException {

    /** What a field left out of a body is told. */
    public static final String REQUIRED = "Field required";

    private final transient List<Problem> problems;

    public Rejected(List<Problem> problems) {
        super(problems.isEmpty() ? "the request was rejected" : problems.get(0).toString());
        this.problems = List.copyOf(problems);
    }

    /**
     * One thing wrong with a request.
     *
     * @param loc where it was found, from {@code body}, {@code query} or {@code path} down
     * @param msg what was wrong there
     */
    public record Problem(List<Object> loc, String msg) {

        public Problem {
            loc = List.copyOf(loc);
        }

        /** The shape the body carries it in. */
        public Map<String, Object> asMap() {
            Map<String, Object> shape = new LinkedHashMap<>();
            shape.put("loc", loc);
            shape.put("msg", msg);
            return shape;
        }

        @Override
        public String toString() {
            return String.join(".", loc.stream().map(Object::toString).toList()) + ": " + msg;
        }
    }

    /** What was wrong, in the order it was found. */
    public List<Problem> problems() {
        return problems;
    }

    /** The body a 422 carries. */
    public Map<String, Object> body() {
        return Map.of("detail", problems.stream().map(Problem::asMap).toList());
    }

    /**
     * Read {@code body} into {@code shape} the way a route reads its request: the JSON rules first,
     * then the shape's constraints.
     *
     * @throws Rejected naming every problem found
     */
    public static <T> T read(ObjectMapper mapper, Validator validator, Object body, Class<T> shape) {
        T read;
        try {
            read = mapper.convertValue(body, shape);
        } catch (IllegalArgumentException unreadable) {
            if (unreadable.getCause() instanceof JsonMappingException mapping) {
                throw new Rejected(List.of(problem(mapping)));
            }
            throw unreadable;
        }
        Set<ConstraintViolation<T>> violations = validator.validate(read);
        if (!violations.isEmpty()) {
            throw new Rejected(violations.stream()
                    .map(one -> new Problem(location("body", one.getPropertyPath()), one.getMessage()))
                    .sorted(Comparator.comparing(Problem::toString))
                    .toList());
        }
        return read;
    }

    /** A problem the JSON reader found, at the path it was reading. */
    static Problem problem(JsonMappingException mapping) {
        List<Object> loc = new ArrayList<>();
        loc.add("body");
        for (JsonMappingException.Reference step : mapping.getPath()) {
            loc.add(step.getFieldName() != null ? step.getFieldName() : step.getIndex());
        }
        return new Problem(loc, mapping.getOriginalMessage());
    }

    /** A constraint's property path as a location, each name in snake case. */
    static List<Object> location(String root, Path path) {
        List<Object> loc = new ArrayList<>();
        loc.add(root);
        for (Path.Node step : path) {
            // A step inside a list carries the index of the element it is in.
            if (step.isInIterable() && step.getIndex() != null) {
                loc.add(step.getIndex());
            }
            if (step.getName() != null) {
                loc.add(snake(step.getName()));
            }
        }
        return loc;
    }

    /** {@code escortCards} as the wire spells it, {@code escort_cards}. */
    static String snake(String name) {
        StringBuilder out = new StringBuilder();
        for (char one : name.toCharArray()) {
            if (Character.isUpperCase(one)) {
                out.append('_').append(Character.toLowerCase(one));
            } else {
                out.append(one);
            }
        }
        return out.toString().toLowerCase(Locale.ROOT);
    }
}
