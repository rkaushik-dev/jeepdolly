package io.github.rkaushikdev.jeepdolly.api.routers;

import io.github.rkaushikdev.jeepdolly.Jeepdolly;
import io.github.rkaushikdev.jeepdolly.OfficeRules;
import io.github.rkaushikdev.jeepdolly.api.Requires;
import io.github.rkaushikdev.jeepdolly.api.Role;
import io.github.rkaushikdev.jeepdolly.api.Work;
import io.github.rkaushikdev.jeepdolly.services.Metrics;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The routes about the service rather than about permits: health, version and metrics.
 *
 * <p>None of them needs a key, and that is a decision rather than an oversight: a load balancer has
 * no credentials, a collector runs inside the same network, and the numbers here say how much work
 * the office did, not what anybody's load weighs. Health touches the database on purpose: a health
 * check that answers without reaching the database tells the load balancer the process is alive
 * while every request is failing.
 *
 * <p>The settings route is the exception to the no key rule. It says nothing secret, but it says
 * how this particular office is configured, and how many copies a clerk may print is not something
 * to publish to the internet.
 */
@RestController
@Tag(name = "service")
public class ServiceController {

    private final Work work;

    public ServiceController(Work work) {
        this.work = work;
    }

    /** Alive, and able to reach the database it was given. */
    @GetMapping("/health")
    public Map<String, Object> health() {
        work.run(uow -> uow.entityManager().createNativeQuery("select 1").getSingleResult());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", Jeepdolly.NAME);
        body.put("version", Jeepdolly.VERSION);
        body.put("database", "up");
        return body;
    }

    /** What is deployed, for the person reading a deploy log. */
    @GetMapping("/version")
    public Map<String, String> version() {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("service", Jeepdolly.NAME);
        body.put("version", Jeepdolly.VERSION);
        return body;
    }

    /** The scrape, in the text format every collector reads. */
    @GetMapping(value = "/metrics", produces = MediaType.TEXT_PLAIN_VALUE)
    public String metrics(@RequestParam Optional<LocalDate> day) {
        return work.apply(uow -> Metrics.exposition(uow.entityManager(), day.orElseGet(LocalDate::now)));
    }

    /** The same numbers for anything that would rather have JSON. */
    @GetMapping("/metrics.json")
    public Map<String, Long> metricsJson(@RequestParam Optional<LocalDate> day) {
        return work.apply(uow -> Metrics.byName(uow.entityManager(), day.orElseGet(LocalDate::now)));
    }

    /** How this office is configured, for the person about to ring about it. */
    @GetMapping("/settings")
    @Requires(Role.READER)
    public Map<String, Object> settings() {
        OfficeRules limits = work.rules();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("free_copies", limits.freeCopies());
        body.put("renewal_days", limits.renewalDays());
        body.put("citation_grace", limits.citationGrace());
        body.put("line_survey_years", limits.lineSurveyYears());
        body.put("lines", limits.lines());
        return body;
    }
}
