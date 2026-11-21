package io.github.rkaushikdev.jeepdolly.api.routers;

import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.api.Caller;
import io.github.rkaushikdev.jeepdolly.api.Requires;
import io.github.rkaushikdev.jeepdolly.api.Role;
import io.github.rkaushikdev.jeepdolly.api.Work;
import io.github.rkaushikdev.jeepdolly.persistence.models.PermitRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.ReviewRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.StructureRow;
import io.github.rkaushikdev.jeepdolly.services.Audit;
import io.github.rkaushikdev.jeepdolly.services.Studies;
import io.github.rkaushikdev.jeepdolly.structures.Structure;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

/**
 * Structure routes: the inventory, the postings, and the study queue.
 *
 * <p>Posting a structure is a supervisor's decision and it is the one write in the service that can
 * strand a permit that has already been issued. The office does not void those automatically: a
 * posting is often a precaution taken on a Friday and lifted on the Monday, and voiding every
 * permit over that bridge would strand loads that are already halfway there.
 *
 * <p>What it does instead is report which live permits cross the structure, so the supervisor can
 * ring them. That list is the whole reason the route is on a permit as segment idents rather than
 * as prose.
 */
@RestController
@RequestMapping("/structures")
@Tag(name = "structures")
public class StructuresController {

    /** The query parameter an engineer repeats, once for each condition written on a clearance. */
    static final String CONDITION = "condition";

    private final Work work;

    public StructuresController(Work work) {
        this.work = work;
    }

    /**
     * One structure as the inventory holds it.
     *
     * @param longestSpan the longest single span in whole feet
     * @param posted the weight on the sign, when there is one
     * @param operatingRating the rating a permit load is held to, when the structure has one
     * @param inspected the day of the last inspection, when anybody knows it
     */
    public record StructureOut(
            String ident,
            String name,
            String segment,
            String kind,
            int longestSpan,
            Optional<String> posted,
            Optional<String> operatingRating,
            Optional<LocalDate> inspected) {

        public static StructureOut of(StructureRow row) {
            Structure structure = row.toDomain();
            return new StructureOut(
                    structure.ident(),
                    structure.name(),
                    structure.segment(),
                    structure.kind().value(),
                    structure.longestSpan(),
                    structure.posted().map(Pounds::toString),
                    structure.operatingRating().map(Pounds::toString),
                    structure.inspected());
        }
    }

    /** What a posting did, and who is on the road under it. */
    public record PostingOut(StructureOut structure, List<String> permitsCrossing) {

        public PostingOut {
            permitsCrossing = List.copyOf(permitsCrossing);
        }
    }

    /**
     * One study in the queue.
     *
     * @param engineer who has it, once somebody has picked it up
     */
    public record StudyOut(
            String reference,
            String state,
            LocalDate opened,
            Optional<String> engineer,
            List<String> structures,
            List<String> conditions) {

        public StudyOut {
            structures = List.copyOf(structures);
            conditions = List.copyOf(conditions);
        }

        public static StudyOut of(ReviewRow row) {
            return new StudyOut(
                    row.getReference(),
                    row.getState().value(),
                    row.getOpened(),
                    row.getEngineer(),
                    split(row.getStructures(), ","),
                    split(row.getConditions(), "\\|"));
        }

        private static List<String> split(String written, String separator) {
            return Arrays.stream(written.split(separator, -1))
                    .filter(one -> !one.isEmpty())
                    .toList();
        }
    }

    /** Everything on one piece of road. */
    @GetMapping("/on/{segment}")
    @Requires(Role.READER)
    public List<StructureOut> structuresOnSegment(@PathVariable String segment) {
        return work.apply(uow -> uow.network().structuresOn(segment).stream()
                .map(StructureOut::of)
                .toList());
    }

    /** The posted list, which is the one the router reads. */
    @GetMapping("/posted")
    @Requires(Role.READER)
    public List<StructureOut> postedStructures() {
        return work.apply(uow ->
                uow.network().postedStructures().stream().map(StructureOut::of).toList());
    }

    /** Put a weight on a structure, and say who is already crossing it. */
    @PostMapping("/{ident}/posting")
    @Requires(Role.SUPERVISOR)
    public PostingOut postStructure(
            @PathVariable String ident, @RequestParam String weight, @RequestParam Optional<LocalDate> on, Caller who) {
        LocalDate day = on.orElseGet(LocalDate::now);
        return work.apply(uow -> {
            StructureRow row = uow.network().structure(ident);
            Pounds posted = Pounds.parse(weight);
            row.setPosted(posted);
            List<String> crossing = uow.permits().onTheRoad(day).stream()
                    .filter(one -> one.getRouteIdents().contains(row.getSegmentIdent()))
                    .map(PermitRow::getNumber)
                    .toList();
            Audit.record(
                    uow.entityManager(),
                    "structure.posted",
                    who.person(),
                    "structure",
                    ident,
                    "posted at " + posted + ", " + crossing.size() + " permits crossing");
            return new PostingOut(StructureOut.of(row), crossing);
        });
    }

    /** The queue, oldest first. */
    @GetMapping("/studies/waiting")
    @Requires(Role.READER)
    public List<StudyOut> waitingStudies() {
        return work.apply(uow ->
                Studies.waiting(uow.entityManager()).stream().map(StudyOut::of).toList());
    }

    /** An engineer picks one up. */
    @PostMapping("/studies/{reference}/assign")
    @Requires(Role.ENGINEER)
    public StudyOut assignStudy(
            @PathVariable String reference,
            @RequestParam String engineer,
            @RequestParam Optional<LocalDate> on,
            Caller who) {
        LocalDate day = on.orElseGet(LocalDate::now);
        return work.apply(uow -> {
            ReviewRow row = Studies.assign(uow.entityManager(), reference, engineer, day);
            Audit.record(uow.entityManager(), "review.assigned", who.person(), "review", reference, engineer);
            return StudyOut.of(row);
        });
    }

    /**
     * Signed, with whatever conditions the engineer wrote.
     *
     * <p>The conditions are read as the request carries them, one per {@code condition} value. A
     * condition may hold a comma, and a single value bound to a list would be split on it.
     */
    @PostMapping("/studies/{reference}/clear")
    @Requires(Role.ENGINEER)
    @Parameter(
            name = CONDITION,
            in = ParameterIn.QUERY,
            description = "one condition written on the clearance, repeated for each",
            array = @ArraySchema(schema = @Schema(implementation = String.class)))
    public StudyOut clearStudy(
            @PathVariable String reference, @RequestParam Optional<LocalDate> on, WebRequest request, Caller who) {
        LocalDate day = on.orElseGet(LocalDate::now);
        String[] written = request.getParameterValues(CONDITION);
        List<String> conditions = written == null ? List.of() : List.of(written);
        return work.apply(uow -> {
            ReviewRow row = Studies.clear(uow.entityManager(), reference, day, conditions);
            Audit.record(uow.entityManager(), "review.cleared", who.person(), "review", reference);
            return StudyOut.of(row);
        });
    }

    /** Signed the other way. */
    @PostMapping("/studies/{reference}/refuse")
    @Requires(Role.ENGINEER)
    public StudyOut refuseStudy(
            @PathVariable String reference,
            @RequestParam @Size(min = 3) String reason,
            @RequestParam Optional<LocalDate> on,
            Caller who) {
        LocalDate day = on.orElseGet(LocalDate::now);
        return work.apply(uow -> {
            ReviewRow row = Studies.refuse(uow.entityManager(), reference, day, reason);
            Audit.record(uow.entityManager(), "review.refused", who.person(), "review", reference, reason);
            return StudyOut.of(row);
        });
    }
}
