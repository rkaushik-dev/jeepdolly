package io.github.rkaushikdev.jeepdolly.api;

import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.Logs;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * One log line per request, with the things somebody would search for.
 *
 * <p>The line is written after the response, because the status and the duration are what make it
 * worth having. Four fields, chosen for grepping rather than completeness: the method and path say
 * what was asked, the status what happened, the duration whether it was slow.
 *
 * <p>The query string is not logged. It carries the load, the dates and sometimes a reason typed by
 * a clerk, and none of that belongs in a file that gets shipped to a log server. The path does carry
 * the permit number and the usdot, which is exactly what somebody greps for. No header is logged,
 * so a key never is.
 *
 * <p>A refusal is logged at info like anything else, because a refusal is the service working.
 * Only an exception nothing answered is an error, logged with its type and passed on.
 */
@Component
public class RequestLog extends OncePerRequestFilter {

    /** Requests slower than this are worth a second look. */
    public static final int SLOW_MILLISECONDS = 500;

    /** Whole milliseconds since a {@link System#nanoTime()} reading. */
    public static int durationSince(long startedNanos) {
        return (int) Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long started = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException trouble) {
            Logs.failure(
                    "request.failed",
                    Facts.of(
                            "method",
                            request.getMethod(),
                            "path",
                            request.getRequestURI(),
                            "error",
                            cause(trouble).getClass().getSimpleName(),
                            "ms",
                            durationSince(started)));
            throw trouble;
        }
        int spent = durationSince(started);
        Map<String, Object> written = new LinkedHashMap<>();
        written.put("method", request.getMethod());
        written.put("path", request.getRequestURI());
        written.put("status", response.getStatus());
        written.put("ms", spent);
        if (spent >= SLOW_MILLISECONDS) {
            Logs.trouble("request.slow", written);
        } else {
            Logs.event("request.done", written);
        }
    }

    /** What actually went wrong, under the servlet's own wrapping. */
    private static Throwable cause(Throwable trouble) {
        Throwable found = trouble;
        while (found instanceof ServletException && found.getCause() != null) {
            found = found.getCause();
        }
        return found;
    }
}
