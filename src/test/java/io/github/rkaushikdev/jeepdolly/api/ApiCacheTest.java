package io.github.rkaushikdev.jeepdolly.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.jeepdolly.OfficeRules;
import io.github.rkaushikdev.jeepdolly.Settings;
import io.github.rkaushikdev.jeepdolly.permits.Office;
import io.github.rkaushikdev.jeepdolly.persistence.Database;
import io.github.rkaushikdev.jeepdolly.persistence.ReferenceCache;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;

/** The cache as the application uses it, which is the part that can go wrong. */
@WebMvcTest
@Import(TestService.class)
class ApiCacheTest {

    @Autowired
    private ReferenceCache cache;

    private Database database;

    @BeforeEach
    void aSeededDatabase() {
        database = Databases.seeded();
        cache.reset();
    }

    @Test
    void theApplicationBuildsACacheOfItsOwn() {
        assertThat(cache).isInstanceOf(ReferenceCache.class);
        assertThat(cache.size()).isEqualTo(0);
    }

    @Test
    void twoApplicationsOverOneDatabaseDoNotShareBooks() {
        Settings settings = Settings.defaults().withDatabase(Databases.URL);
        try (Running first = Running.start(settings, database);
                Running second = Running.start(settings, database)) {
            assertThat(first.bean(ReferenceCache.class)).isNotSameAs(second.bean(ReferenceCache.class));
        }
    }

    @Test
    void anOfficeBuiltWithNoCacheStillReadsEverything() {
        Office office;
        try (UnitOfWork work = database.unitOfWork()) {
            office = Deps.officeFrom(work);
        }
        assertThat(office.network().segments()).hasSize(12);
        assertThat(office.commodities().size()).isEqualTo(17);
    }

    @Test
    void anOfficeBuiltWithACacheReadsTheSameBooks() {
        ReferenceCache fresh = new ReferenceCache();
        Office plain;
        Office cached;
        try (UnitOfWork work = database.unitOfWork()) {
            plain = Deps.officeFrom(work);
            cached = Deps.officeFrom(work, LocalDate.now(), Optional.of(fresh), OfficeRules.defaults());
        }
        assertThat(cached.network().segments()).hasSameSizeAs(plain.network().segments());
        assertThat(cached.library().size()).isEqualTo(plain.library().size());
        assertThat(fresh.misses()).isEqualTo(1);
    }
}
