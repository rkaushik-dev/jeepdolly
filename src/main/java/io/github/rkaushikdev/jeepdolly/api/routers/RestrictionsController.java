package io.github.rkaushikdev.jeepdolly.api.routers;

import io.github.rkaushikdev.jeepdolly.Conflict;
import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.NotOnFile;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.api.Caller;
import io.github.rkaushikdev.jeepdolly.api.Rejected;
import io.github.rkaushikdev.jeepdolly.api.Requires;
import io.github.rkaushikdev.jeepdolly.api.Role;
import io.github.rkaushikdev.jeepdolly.api.Work;
import io.github.rkaushikdev.jeepdolly.domain.RestrictionKind;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.persistence.models.PermitRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.RestrictionRow;
import io.github.rkaushikdev.jeepdolly.services.Audit;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Closing a road and opening it again, which is the busiest write there is.
 *
 * <p>A closure is typed by whoever takes the telephone call from the district, at any hour, and it
 * has to be one request with no ceremony. So this route takes a segment, a note and two optional
 * dates, and everything else is defaulted.
 *
 * <p>What it gives back is the list of live permits whose route names that segment. That list is
 * the reason the route exists: somebody has to ring those carriers, and until this was built they
 * were found by reading the day's permits on paper.
 *
 * <p>Opening a road again deletes the row. That is the one place in this system that deletes
 * anything, and it is right: a lifted closure is not history, the trail already says who closed it
 * and who opened it, and a restriction table full of dead rows makes every route check slower for
 * nothing.
 */
@RestController
@RequestMapping("/restrictions")
@Tag(name = "restrictions")
public class RestrictionsController {

    private final Work work;

    public RestrictionsController(Work work) {
        this.work = work;
    }

    /**
     * What the district said on the telephone. A measure left out or left empty is none.
     *
     * @param effective the first day it applies, or always when left out
     * @param expires the last day it applies, or never lifting when left out
     */
    public record RestrictionIn(
            @NotNull(message = Rejected.REQUIRED) String ident,
            @NotNull(message = Rejected.REQUIRED) String segment,
            @NotNull(message = Rejected.REQUIRED) String kind,
            @NotNull(message = Rejected.REQUIRED) String note,
            Optional<String> height,
            Optional<String> width,
            Optional<String> weight,
            Optional<String> region,
            Optional<LocalDate> effective,
            Optional<LocalDate> expires) {

        public RestrictionIn {
            height = height == null ? Optional.empty() : height;
            width = width == null ? Optional.empty() : width;
            weight = weight == null ? Optional.empty() : weight;
            region = region == null ? Optional.empty() : region;
            effective = effective == null ? Optional.empty() : effective;
            expires = expires == null ? Optional.empty() : expires;
        }

        /** The row this restriction goes on file as. */
        public RestrictionRow toRow() {
            RestrictionRow row = new RestrictionRow(ident, segment, RestrictionKind.of(kind), note);
            row.setHeight(written(height).map(Inches::parse).orElse(null));
            row.setWidth(written(width).map(Inches::parse).orElse(null));
            row.setWeight(written(weight).map(Pounds::parse).orElse(null));
            row.setRegion(region.orElse(null));
            row.setEffective(effective.orElse(null));
            row.setExpires(expires.orElse(null));
            return row;
        }

        private static Optional<String> written(Optional<String> measure) {
            return measure.filter(one -> !one.isEmpty());
        }
    }

    /**
     * One restriction, and who is already under it.
     *
     * @param permitsCrossing the permits still running whose route names the segment
     */
    public record RestrictionOut(
            String ident,
            String segment,
            String kind,
            String note,
            Optional<LocalDate> effective,
            Optional<LocalDate> expires,
            List<String> permitsCrossing) {

        public RestrictionOut {
            permitsCrossing = List.copyOf(permitsCrossing);
        }

        public static RestrictionOut of(RestrictionRow row, List<String> permitsCrossing) {
            return new RestrictionOut(
                    row.getIdent(),
                    row.getSegmentIdent(),
                    row.getKind().value(),
                    row.getNote(),
                    row.getEffective(),
                    row.getExpires(),
                    permitsCrossing);
        }
    }

    /** The permits whose route names a segment and whose last day has not gone by. */
    private static List<String> crossing(UnitOfWork uow, String segment, LocalDate day) {
        return uow.permits().touchingSegment(segment).stream()
                .filter(one -> !one.getValidTo().isBefore(day))
                .map(PermitRow::getNumber)
                .toList();
    }

    /**
     * Put a restriction on a segment and say who is already crossing it.
     *
     * @throws Conflict when a restriction with that ident is already on file
     * @throws NotOnFile when the segment is not on the road log
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Requires(Role.SUPERVISOR)
    public RestrictionOut closeRoad(
            @Valid @RequestBody RestrictionIn body, @RequestParam Optional<LocalDate> on, Caller who) {
        LocalDate day = on.orElseGet(LocalDate::now);
        return work.apply(uow -> {
            if (uow.entityManager().find(RestrictionRow.class, body.ident()) != null) {
                throw new Conflict(
                        "restriction " + body.ident() + " is already on file", Facts.of("restriction", body.ident()));
            }
            uow.network().segment(body.segment());
            RestrictionRow row = uow.add(body.toRow());
            List<String> crossing = crossing(uow, body.segment(), day);
            Audit.record(
                    uow.entityManager(),
                    "road.closed",
                    who.person(),
                    "segment",
                    body.segment(),
                    body.kind() + ": " + body.note() + ", " + crossing.size() + " permits crossing");
            return RestrictionOut.of(row, crossing);
        });
    }

    /**
     * Take a restriction off. The trail keeps the record of it.
     *
     * @throws NotOnFile when no restriction with that ident is on file
     */
    @DeleteMapping("/{ident}")
    @Requires(Role.SUPERVISOR)
    public RestrictionOut openRoad(@PathVariable String ident, @RequestParam Optional<LocalDate> on, Caller who) {
        LocalDate day = on.orElseGet(LocalDate::now);
        return work.apply(uow -> {
            RestrictionRow row = uow.entityManager().find(RestrictionRow.class, ident);
            if (row == null) {
                throw new NotOnFile("no restriction " + ident, Facts.of("restriction", ident));
            }
            RestrictionOut answer = RestrictionOut.of(row, crossing(uow, row.getSegmentIdent(), day));
            Audit.record(uow.entityManager(), "road.opened", who.person(), "segment", row.getSegmentIdent(), ident);
            uow.delete(row);
            return answer;
        });
    }

    /** What is on one segment on a day. */
    @GetMapping("/on/{segment}")
    @Requires(Role.READER)
    public List<RestrictionOut> restrictionsOn(@PathVariable String segment, @RequestParam Optional<LocalDate> day) {
        LocalDate when = day.orElseGet(LocalDate::now);
        return work.apply(uow -> uow.network().restrictionsOn(segment, when).stream()
                .map(one -> RestrictionOut.of(one, List.of()))
                .toList());
    }
}
