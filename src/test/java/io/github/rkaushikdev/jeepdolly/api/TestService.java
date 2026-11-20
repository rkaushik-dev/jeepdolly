package io.github.rkaushikdev.jeepdolly.api;

import io.github.rkaushikdev.jeepdolly.Settings;
import io.github.rkaushikdev.jeepdolly.persistence.Database;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * What the service is handed when a test slice starts it: the default settings, and the suite's
 * database, which each test empties or seeds for itself.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestService {

    @Bean
    Settings settings() {
        return Settings.defaults().withDatabase(Databases.URL);
    }

    /** The suite's database. The suite closes it, not the context. */
    @Bean(destroyMethod = "")
    Database database() {
        return Databases.empty();
    }
}
