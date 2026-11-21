package io.github.rkaushikdev.jeepdolly.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.jeepdolly.OfficeRules;
import io.github.rkaushikdev.jeepdolly.Settings;
import io.github.rkaushikdev.jeepdolly.api.routers.ApplicationsController;
import io.github.rkaushikdev.jeepdolly.permits.Office;
import io.github.rkaushikdev.jeepdolly.persistence.Database;
import io.github.rkaushikdev.jeepdolly.persistence.ReferenceCache;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.support.Clients;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.format.DateTimeFormatters;
import org.springframework.boot.autoconfigure.web.format.WebConversionService;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** The cache as the application uses it, which is the part that can go wrong. */
@WebMvcTest
@Import(TestService.class)
class ApiCacheTest {

    private static final String APRIL = "2025-04-08";

    @Autowired
    private MockMvc mvc;

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

    private static Http.Answer find(Http client) {
        return client.postJson("/routes/find?origin=waterville&destination=pittsfield&day=" + APRIL, Map.of());
    }

    @Test
    void theFirstRequestFillsItAndTheSecondHitsIt() {
        Http client = Clients.keyed(new Http(mvc), database);
        assertThat(find(client).status()).isEqualTo(200);
        find(client);
        assertThat(cache.misses()).isEqualTo(1);
        assertThat(cache.hits()).isEqualTo(1);
    }

    /** The Fairfield village wire, with no survey day written down. */
    private static Map<String, Object> aWire() {
        return Bodies.map(
                "ident",
                "L-1",
                "segment",
                "US201-2",
                "place",
                "Fairfield village",
                "height",
                "19'",
                "owner",
                "CMP",
                "lift_to",
                "26'");
    }

    private static Map<String, Object> anOwner() {
        return Bodies.map("ident", "CMP", "name", "Central Maine Power", "telephone", "207 555 0100");
    }

    @Test
    void aRouteThatWritesAPublishedRowEmptiesIt() {
        Http client = Clients.keyed(new Http(mvc), database);
        find(client);
        assertThat(cache.size()).isEqualTo(1);
        client.postJson("/lines/owners", anOwner());
        assertThat(cache.size()).isEqualTo(0);
    }

    @Test
    void aRouteThatWritesAnApplicationLeavesItAlone() {
        Http client = Clients.keyed(new Http(mvc), database);
        find(client);
        int filled = cache.size();
        client.get("/citations/next-number?on=2025-04-08");
        assertThat(cache.size()).isEqualTo(filled);
    }

    @Test
    void aNewWireIsSeenByTheVeryNextRequest() {
        Http client = Clients.keyed(new Http(mvc), database);
        String query = "origin=waterville&segments=US201-1&segments=US201-2&height=20'&travel=2025-04-09";
        JsonNode before =
                client.get("/lines/clearance?" + query + "&filed=2025-03-01").json();
        assertThat(before.get("lifts").isArray()).isTrue();
        assertThat(before.get("lifts").size()).isZero();
        client.postJson("/lines/owners", anOwner());
        client.postJson("/lines", aWire());
        JsonNode after =
                client.get("/lines/clearance?" + query + "&filed=2025-03-01").json();
        assertThat(after.get("lifts").size()).isEqualTo(1);
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

    @Test
    void anApplicationWithNoCacheStillWorks() {
        Work bare = new Work(database, Optional.empty(), Settings.defaults().withDatabase(Databases.URL));
        Gate gate = new Gate(database);
        MockMvc plain = MockMvcBuilders.standaloneSetup(new ApplicationsController(bare))
                .addInterceptors(gate)
                .setCustomArgumentResolvers(gate)
                .setControllerAdvice(new Errors())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(Json.mapper()))
                .setConversionService(new WebConversionService(new DateTimeFormatters().dateFormat("iso")))
                .build();
        Http client = Clients.keyed(new Http(plain), database);
        assertThat(client.get("/applications/25-04471/assessment?on=" + APRIL).status())
                .isIn(200, 404);
    }
}
