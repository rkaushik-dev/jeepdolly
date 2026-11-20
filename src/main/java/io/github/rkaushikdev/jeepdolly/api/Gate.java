package io.github.rkaushikdev.jeepdolly.api;

import io.github.rkaushikdev.jeepdolly.persistence.Database;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * The door every route marked {@link Requires} goes through.
 *
 * <p>It runs as an interceptor, before a single argument is read, so a caller who may not be here
 * is told so before anybody reads what they sent: a 401 or a 403 beats a 422. It also hands the
 * caller to any handler that asks for a {@link Caller}, so a write can put a name on the audit
 * trail and a carrier's key can be held to its own permits. The key is read in a unit of work of
 * its own, which only reads.
 */
public final class Gate implements HandlerInterceptor, HandlerMethodArgumentResolver {

    private static final String CALLER = Gate.class.getName() + ".caller";

    private final Database database;

    public Gate(Database database) {
        this.database = database;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod method) {
            Requires needs = method.getMethodAnnotation(Requires.class);
            if (needs != null) {
                Caller caller;
                try (UnitOfWork work = database.unitOfWork()) {
                    caller = Security.callerFor(
                            work, Optional.ofNullable(request.getHeader(Security.HEADER)), LocalDate.now());
                }
                request.setAttribute(CALLER, Security.needs(caller, needs.value()));
            }
        }
        return true;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == Caller.class;
    }

    @Override
    public Caller resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer container,
            NativeWebRequest request,
            WebDataBinderFactory binders) {
        Object caller = request.getAttribute(CALLER, NativeWebRequest.SCOPE_REQUEST);
        if (caller instanceof Caller known) {
            return known;
        }
        throw new IllegalStateException(
                parameter.getExecutable().getName() + " asks for a caller and has no @Requires to check one");
    }
}
