package io.github.rkaushikdev.jeepdolly.api;

import io.github.rkaushikdev.jeepdolly.Settings;
import io.github.rkaushikdev.jeepdolly.persistence.Database;
import java.util.List;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * The whole service, started the way the command line starts it, for a test that needs what a
 * slice does not carry: the interface description, two applications at once, or settings of its
 * own. It listens on a port nobody else has; requests go straight to it through MockMvc.
 */
public final class Running implements AutoCloseable {

    private final ConfigurableApplicationContext context;
    private final Http http;

    private Running(ConfigurableApplicationContext context) {
        this.context = context;
        this.http = new Http(MockMvcBuilders.webAppContextSetup((WebApplicationContext) context)
                .addFilters(context.getBean(RequestLog.class))
                .build());
    }

    /** Start the service over {@code database} with {@code settings}, and any routes a test adds. */
    public static Running start(Settings settings, Database database, Class<?>... extra) {
        return new Running(JeepdollyApplication.run(settings, database, List.of(extra), "--server.port=0"));
    }

    /** A client with no key. */
    public Http http() {
        return http;
    }

    /** One of the running service's own beans. */
    public <T> T bean(Class<T> type) {
        return context.getBean(type);
    }

    @Override
    public void close() {
        context.close();
    }
}
