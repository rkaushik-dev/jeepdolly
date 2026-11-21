package io.github.rkaushikdev.jeepdolly.api.routers;

import io.github.rkaushikdev.jeepdolly.api.Requires;
import io.github.rkaushikdev.jeepdolly.api.Role;
import io.github.rkaushikdev.jeepdolly.api.Work;
import io.github.rkaushikdev.jeepdolly.reports.IssuanceReport;
import io.github.rkaushikdev.jeepdolly.reports.RevenueReport;
import io.github.rkaushikdev.jeepdolly.reports.RoadCalendar;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Report routes, which hand back both the printed lines and the figures behind them: the evening
 * issuance report, where the money came from, and what changes on the road.
 */
@RestController
@RequestMapping("/reports")
@Tag(name = "reports")
public class ReportsController {

    private final Work work;

    public ReportsController(Work work) {
        this.work = work;
    }

    /** What was written and what it took. */
    public record IssuanceOut(
            LocalDate start,
            LocalDate end,
            int count,
            String taken,
            Map<String, Integer> bySeries,
            int refused,
            int withdrawn,
            List<String> lines) {

        public IssuanceOut {
            bySeries = Collections.unmodifiableMap(new LinkedHashMap<>(bySeries));
            lines = List.copyOf(lines);
        }
    }

    /** Where the money came from. */
    public record RevenueOut(
            LocalDate start,
            LocalDate end,
            int permits,
            String taken,
            Map<String, String> byKind,
            Map<String, String> byMonth,
            List<String> lines) {

        public RevenueOut {
            lines = List.copyOf(lines);
        }
    }

    /** One restriction in force. */
    public record StandingOut(String segment, String kind, String limit, String note, boolean permanent) {}

    /** What changes on the road, and what is in force on the day beside it. */
    public record CalendarOut(LocalDate start, int days, List<String> changes, List<StandingOut> standing) {

        public CalendarOut {
            changes = List.copyOf(changes);
            standing = List.copyOf(standing);
        }
    }

    /** The evening report, for a day or a span. */
    @GetMapping("/issuance")
    @Requires(Role.READER)
    public IssuanceOut issuanceReport(@RequestParam LocalDate day, @RequestParam Optional<LocalDate> to) {
        return work.apply(uow -> {
            IssuanceReport.Issuance found = to.map(end -> IssuanceReport.issuance(uow.entityManager(), day, end))
                    .orElseGet(() -> IssuanceReport.issuance(uow.entityManager(), day));
            return new IssuanceOut(
                    found.start(),
                    found.end(),
                    found.count(),
                    found.taken().toString(),
                    found.bySeries(),
                    found.refused(),
                    found.withdrawn(),
                    found.lines());
        });
    }

    /** Where the money came from over a span. */
    @GetMapping("/revenue")
    @Requires(Role.READER)
    public RevenueOut revenueReport(@RequestParam LocalDate start, @RequestParam LocalDate end) {
        return work.apply(uow -> {
            RevenueReport.Revenue found = RevenueReport.revenue(uow.entityManager(), start, end);
            return new RevenueOut(
                    found.start(),
                    found.end(),
                    found.permits(),
                    found.taken().toString(),
                    asText(found.byKind()),
                    asText(found.byMonth()),
                    found.lines());
        });
    }

    /** What changes on the road, with what is in force on the day beside it. */
    @GetMapping("/roads")
    @Requires(Role.READER)
    public CalendarOut roadReport(
            @RequestParam LocalDate day, @RequestParam(defaultValue = "14") @Min(1) @Max(90) int days) {
        return work.apply(uow -> new CalendarOut(
                day,
                days,
                RoadCalendar.changes(uow.entityManager(), day, days),
                RoadCalendar.standingOn(uow.entityManager(), day).stream()
                        .map(one ->
                                new StandingOut(one.segment(), one.kind(), one.limit(), one.note(), one.permanent()))
                        .toList()));
    }

    private static <K> Map<K, String> asText(Map<K, ?> amounts) {
        Map<K, String> written = new LinkedHashMap<>();
        amounts.forEach((name, amount) -> written.put(name, amount.toString()));
        return written;
    }
}
