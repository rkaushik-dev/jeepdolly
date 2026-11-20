package io.github.rkaushikdev.jeepdolly.api;

import io.github.rkaushikdev.jeepdolly.persistence.Database;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** The service's additions to Spring MVC: the {@link Gate} in front of every route, and its caller. */
@Component
public class ApiWeb implements WebMvcConfigurer {

    private final Gate gate;

    public ApiWeb(Database database) {
        this.gate = new Gate(database);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(gate);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(gate);
    }
}
