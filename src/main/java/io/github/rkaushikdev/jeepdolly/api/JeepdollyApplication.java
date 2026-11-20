package io.github.rkaushikdev.jeepdolly.api;

import io.github.rkaushikdev.jeepdolly.Jeepdolly;
import io.github.rkaushikdev.jeepdolly.Settings;
import io.github.rkaushikdev.jeepdolly.persistence.Database;
import io.github.rkaushikdev.jeepdolly.persistence.ReferenceCache;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * The http service: applications, permits, the road log and the office's books, behind a key per
 * person.
 *
 * <p>There is one factory, {@link #run}, and it takes everything it needs as arguments: no database
 * of its own, no environment read anywhere in here. The settings and the database are handed in and
 * become beans. The reference cache belongs to the application rather than to the process, so two
 * applications over two databases in one process do not share books. Units of work are per
 * request, and nothing but the published books is held between requests.
 *
 * <p>Spring Boot's own data source, JPA and Flyway set up are switched off: with H2 on the class
 * path and no data source of its own, Boot would otherwise build an embedded database and migrate
 * that.
 */
@SpringBootApplication(
        exclude = {
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class
        })
public class JeepdollyApplication {

    /** What the service calls itself, on the health route and in its interface description. */
    public static final String TITLE = Jeepdolly.NAME;

    public static final String DESCRIPTION = "Oversize and overweight permits for the motor carrier office";

    /** Start the service over a database somebody else opened and will close. */
    public static ConfigurableApplicationContext run(Settings settings, Database database, String... args) {
        return run(settings, database, List.of(), args);
    }

    /**
     * Start the service with more configuration beside the application's own: the routes a test
     * adds, for instance.
     */
    public static ConfigurableApplicationContext run(
            Settings settings, Database database, List<Class<?>> extra, String... args) {
        List<Class<?>> sources = new ArrayList<>();
        sources.add(JeepdollyApplication.class);
        sources.addAll(extra);
        SpringApplication application = new SpringApplicationBuilder(sources.toArray(Class<?>[]::new))
                .initializers(context -> {
                    context.getBeanFactory().registerSingleton("settings", settings);
                    context.getBeanFactory().registerSingleton("database", database);
                })
                .build();
        return application.run(args);
    }

    @Bean
    ReferenceCache referenceCache() {
        return new ReferenceCache();
    }

    @Bean
    ReferenceWatch referenceWatch(Database database, ReferenceCache cache) {
        return new ReferenceWatch(database, cache);
    }

    @Bean
    Work work(Database database, ReferenceCache cache, Settings settings) {
        return new Work(database, Optional.of(cache), settings);
    }

    @Bean
    Jackson2ObjectMapperBuilderCustomizer officeJson() {
        return Json::configure;
    }

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info().title(TITLE).description(DESCRIPTION).version(Jeepdolly.VERSION));
    }
}
