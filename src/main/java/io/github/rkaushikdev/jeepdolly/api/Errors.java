package io.github.rkaushikdev.jeepdolly.api;

import com.fasterxml.jackson.databind.JsonMappingException;
import io.github.rkaushikdev.jeepdolly.Blocked;
import io.github.rkaushikdev.jeepdolly.Conflict;
import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.NotAllowed;
import io.github.rkaushikdev.jeepdolly.NotOnFile;
import io.github.rkaushikdev.jeepdolly.OverLimit;
import io.github.rkaushikdev.jeepdolly.Refusal;
import io.github.rkaushikdev.jeepdolly.Unreadable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * One error shape for every refusal, and the codes that map onto statuses.
 *
 * <p>Every refusal that leaves this service is {@code {"code": ..., "message": ..., "facts":
 * {...}}}, the shape a refusal already carries, so a refusal raised anywhere in the domain arrives
 * at the client without anybody translating it. The status is a lookup on the refusal's code and
 * nothing else:
 *
 * <ul>
 *   <li>{@code unreadable} and {@code impossible} are 422: the request was understood and what it
 *       describes cannot exist.
 *   <li>{@code not_on_file} is 404. {@code conflict} is 409, and so is {@code not_allowed}, because
 *       it is about the state of a document and not about who is asking.
 *   <li>{@code over_limit} and {@code blocked} are 422 as well. A load over a limit is not a bad
 *       request: the request was perfectly good and the answer is no, and the content was understood
 *       and refused.
 * </ul>
 *
 * <p>The door's answers are {@code {"detail": "..."}} with 401 or 403, and a request that cannot be
 * read into the shape its route takes is a 422 whose {@code detail} lists where and what.
 */
@RestControllerAdvice
public class Errors {

    /** What each refusal code comes back as. */
    public static final Map<String, Integer> STATUS = statuses();

    private static Map<String, Integer> statuses() {
        Map<String, Integer> status = new LinkedHashMap<>();
        status.put(Unreadable.CODE, 422);
        status.put(Impossible.CODE, 422);
        status.put(NotOnFile.CODE, 404);
        status.put(Conflict.CODE, 409);
        status.put(OverLimit.CODE, 422);
        status.put(Blocked.CODE, 422);
        status.put(NotAllowed.CODE, 409);
        status.put(Refusal.CODE, 422);
        return Collections.unmodifiableMap(status);
    }

    /** The status a refusal code comes back as; a code nobody has seen is a 422. */
    public static int statusFor(String code) {
        return STATUS.getOrDefault(code, 422);
    }

    /** The body a refusal comes back in. */
    public static Map<String, Object> bodyFor(Refusal refusal) {
        return refusal.asMap();
    }

    @ExceptionHandler(Refusal.class)
    public ResponseEntity<Map<String, Object>> refusal(Refusal refusal) {
        return ResponseEntity.status(statusFor(refusal.code())).body(bodyFor(refusal));
    }

    @ExceptionHandler(NotAuthenticated.class)
    public ResponseEntity<Map<String, Object>> unknown(NotAuthenticated failure) {
        return detail(HttpStatus.UNAUTHORIZED, failure.getMessage());
    }

    @ExceptionHandler(NotPermitted.class)
    public ResponseEntity<Map<String, Object>> belowTheRung(NotPermitted failure) {
        return detail(HttpStatus.FORBIDDEN, failure.getMessage());
    }

    @ExceptionHandler(Rejected.class)
    public ResponseEntity<Map<String, Object>> rejected(Rejected failure) {
        return ResponseEntity.unprocessableEntity().body(failure.body());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> unreadable(HttpMessageNotReadableException failure) {
        Rejected.Problem problem = failure.getCause() instanceof JsonMappingException mapping
                ? Rejected.problem(mapping)
                : new Rejected.Problem(List.of("body"), "the body is not readable JSON");
        return rejected(new Rejected(List.of(problem)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> invalid(MethodArgumentNotValidException failure) {
        List<Rejected.Problem> problems = new ArrayList<>();
        for (FieldError error : failure.getBindingResult().getFieldErrors()) {
            problems.add(new Rejected.Problem(dotted("body", error.getField()), error.getDefaultMessage()));
        }
        return rejected(new Rejected(problems));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> missing(MissingServletRequestParameterException failure) {
        return rejected(new Rejected(
                List.of(new Rejected.Problem(List.of("query", failure.getParameterName()), Rejected.REQUIRED))));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> mismatched(MethodArgumentTypeMismatchException failure) {
        String root = failure.getParameter().hasParameterAnnotation(PathVariable.class) ? "path" : "query";
        Class<?> wanted = failure.getRequiredType();
        String kind = wanted == null ? "value" : wanted.getSimpleName();
        return rejected(new Rejected(
                List.of(new Rejected.Problem(List.of(root, failure.getName()), "Input should be a valid " + kind))));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Map<String, Object>> outOfRange(HandlerMethodValidationException failure) {
        List<Rejected.Problem> problems = new ArrayList<>();
        failure.getParameterValidationResults().forEach(result -> {
            String name = result.getMethodParameter().getParameterName();
            String root = result.getMethodParameter().hasParameterAnnotation(PathVariable.class) ? "path" : "query";
            result.getResolvableErrors()
                    .forEach(error -> problems.add(
                            new Rejected.Problem(List.of(root, String.valueOf(name)), error.getDefaultMessage())));
        });
        return rejected(new Rejected(problems));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> nothingHere(NoResourceFoundException failure) {
        return detail(HttpStatus.NOT_FOUND, "Not Found");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> wrongMethod(HttpRequestMethodNotSupportedException failure) {
        return detail(HttpStatus.METHOD_NOT_ALLOWED, "Method Not Allowed");
    }

    private static ResponseEntity<Map<String, Object>> detail(HttpStatus status, String detail) {
        return ResponseEntity.status(status).body(Map.of("detail", detail));
    }

    /** A binding path as a location: {@code axles[0].tireWidth} is body, axles, 0, tire_width. */
    static List<Object> dotted(String root, String field) {
        List<Object> loc = new ArrayList<>();
        loc.add(root);
        for (String part : field.replaceAll("\\[(\\d+)]", ".$1").split("\\.")) {
            boolean index = !part.isEmpty() && part.chars().allMatch(Character::isDigit);
            loc.add(index ? Integer.parseInt(part) : Rejected.snake(part));
        }
        return loc;
    }
}
