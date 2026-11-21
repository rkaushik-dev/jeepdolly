package io.github.rkaushikdev.jeepdolly.api.routers;

import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.api.Rejected;
import io.github.rkaushikdev.jeepdolly.api.Requires;
import io.github.rkaushikdev.jeepdolly.api.Role;
import io.github.rkaushikdev.jeepdolly.api.Work;
import io.github.rkaushikdev.jeepdolly.domain.Envelope;
import io.github.rkaushikdev.jeepdolly.persistence.Books;
import io.github.rkaushikdev.jeepdolly.persistence.models.LineCrossingRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.LineOwnerRow;
import io.github.rkaushikdev.jeepdolly.routing.Itinerary;
import io.github.rkaushikdev.jeepdolly.utilities.Crossing;
import io.github.rkaushikdev.jeepdolly.utilities.LineOwner;
import io.github.rkaushikdev.jeepdolly.utilities.LineReading;
import io.github.rkaushikdev.jeepdolly.utilities.Lines;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Overhead line routes: the owners, the wires, and the survey that moves them.
 *
 * <p>Remeasuring is a PUT rather than a POST because it replaces what the card says. There is no
 * history and no version: a wire is a fact about the world today, and nobody has ever asked this
 * office what a span used to hang at.
 *
 * <p>The clearance question is the one route the counter actually uses. A dispatcher rings up and
 * asks whether nineteen feet gets from Waterville to Skowhegan, and this answers with the wires in
 * the way and the telephone numbers to ring, without writing anything down.
 *
 * <p>Surveying is a supervisor's job and reading is everybody's, which is the same split every other
 * reference table uses.
 */
@RestController
@RequestMapping("/lines")
@Tag(name = "lines")
public class LinesController {

    /** The width and length a clearance question assumes: the legal box, since only the height asks. */
    static final String LEGAL_WIDTH = "8'6";

    static final String LEGAL_LENGTH = "70'";

    private final Work work;

    public LinesController(Work work) {
        this.work = work;
    }

    /** A line owner as the counter types it in. Notice left out is five working days. */
    public record OwnerIn(
            @NotNull(message = Rejected.REQUIRED) String ident,
            @NotNull(message = Rejected.REQUIRED) String name,
            String telephone,
            @Min(0) @Max(60) Integer noticeDays,
            String note) {

        public OwnerIn {
            telephone = telephone == null ? "" : telephone;
            noticeDays = noticeDays == null ? LineOwnerRow.NOTICE_DAYS : noticeDays;
            note = note == null ? "" : note;
        }

        public LineOwner toDomain() {
            return new LineOwner(ident, name, telephone, noticeDays, note);
        }
    }

    /** An owner as the office holds it, with how many wires it has. */
    public record OwnerOut(String ident, String name, String telephone, int noticeDays, int wires) {

        public static OwnerOut of(LineOwnerRow row) {
            return new OwnerOut(
                    row.getIdent(),
                    row.getName(),
                    row.getTelephone(),
                    row.getNoticeDays(),
                    row.getWires().size());
        }
    }

    /**
     * A wire as the survey wrote it down.
     *
     * @param liftTo what the owner can raise it to, when it can be raised
     * @param measured the day of the survey, when somebody wrote it down
     */
    public record CrossingIn(
            @NotNull(message = Rejected.REQUIRED) String ident,
            @NotNull(message = Rejected.REQUIRED) String segment,
            @NotNull(message = Rejected.REQUIRED) String place,
            @NotNull(message = Rejected.REQUIRED) String height,
            @NotNull(message = Rejected.REQUIRED) String owner,
            Optional<String> liftTo,
            Optional<LocalDate> measured,
            String note) {

        public CrossingIn {
            liftTo = liftTo == null ? Optional.empty() : liftTo;
            measured = measured == null ? Optional.empty() : measured;
            note = note == null ? "" : note;
        }

        public Crossing toDomain() {
            return new Crossing(ident, segment, place, Inches.parse(height), owner, liftTo.map(Inches::parse), note);
        }
    }

    /**
     * What the crew found when they went back to it.
     *
     * @param liftTo what the owner can now raise it to, or left out when it can no longer be raised
     */
    public record MeasureIn(
            @NotNull(message = Rejected.REQUIRED) String height,
            Optional<String> liftTo,
            @NotNull(message = Rejected.REQUIRED) LocalDate measured) {

        public MeasureIn {
            liftTo = liftTo == null ? Optional.empty() : liftTo;
        }
    }

    /**
     * A wire as the office holds it.
     *
     * @param liftTo what the owner can raise it to, when it can be raised
     * @param measured the day of the last survey, when anybody wrote it down
     */
    public record CrossingOut(
            String ident,
            String segment,
            String place,
            String height,
            String owner,
            Optional<String> liftTo,
            Optional<LocalDate> measured) {

        public static CrossingOut of(LineCrossingRow row) {
            return new CrossingOut(
                    row.getIdent(),
                    row.getSegmentIdent(),
                    row.getPlace(),
                    row.getHeight().toString(),
                    row.getOwnerIdent(),
                    row.getLiftTo().map(Inches::toString),
                    row.getMeasured());
        }
    }

    /** One wire in the way, and who to ring about it. */
    public record LiftOut(
            String crossing,
            String segment,
            String place,
            String hangsAt,
            String owner,
            String telephone,
            LocalDate noticeBy) {}

    /** One wire nothing can be done about. */
    public record BlockedOut(String crossing, String segment, String place, String reason) {}

    /** What one load meets overhead on one written route. */
    public record ClearanceOut(String wanted, boolean clear, List<LiftOut> lifts, List<BlockedOut> blocked) {

        public ClearanceOut {
            lifts = List.copyOf(lifts);
            blocked = List.copyOf(blocked);
        }

        public static ClearanceOut of(Envelope envelope, LineReading found) {
            return new ClearanceOut(
                    envelope.height().toString(),
                    found.clear(),
                    found.lifts().stream()
                            .map(one -> new LiftOut(
                                    one.crossing().ident(),
                                    one.crossing().segment(),
                                    one.crossing().place(),
                                    one.crossing().height().toString(),
                                    one.owner().name(),
                                    one.owner().telephone(),
                                    one.noticeBy()))
                            .toList(),
                    found.blocked().stream()
                            .map(one -> new BlockedOut(
                                    one.crossing().ident(),
                                    one.crossing().segment(),
                                    one.crossing().place(),
                                    one.toString()))
                            .toList());
        }
    }

    /** Every line owner, in ident order, with how many wires each has. */
    @GetMapping("/owners")
    @Requires(Role.READER)
    public List<OwnerOut> listOwners() {
        return work.apply(uow -> uow.lines().owners().stream().map(OwnerOut::of).toList());
    }

    /** Put a line owner on file. */
    @PostMapping("/owners")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires(Role.SUPERVISOR)
    public OwnerOut addOwner(@Valid @RequestBody OwnerIn body) {
        return work.apply(uow -> OwnerOut.of(uow.lines().addOwner(body.toDomain())));
    }

    /** Every wire, or every wire over one segment. */
    @GetMapping
    @Requires(Role.READER)
    public List<CrossingOut> listCrossings(@RequestParam Optional<String> segment) {
        return work.apply(uow -> {
            List<LineCrossingRow> found = segment.isPresent()
                    ? uow.lines().crossings(segment.get())
                    : uow.lines().crossings();
            return found.stream().map(CrossingOut::of).toList();
        });
    }

    /** Put a measured wire on file. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Requires(Role.SUPERVISOR)
    public CrossingOut addCrossing(@Valid @RequestBody CrossingIn body) {
        return work.apply(uow ->
                CrossingOut.of(uow.lines().add(body.toDomain(), body.measured().orElse(null))));
    }

    /** Every wire hanging below a height, lowest first, for the survey. */
    @GetMapping("/low")
    @Requires(Role.READER)
    public List<CrossingOut> low(@RequestParam(defaultValue = "20'") String under) {
        return work.apply(uow -> uow.lines().under(Inches.parse(under)).stream()
                .map(CrossingOut::of)
                .toList());
    }

    /**
     * What a load of this height meets on this written route. Nothing is written down: the road log
     * and the wires are read as they stand.
     *
     * @param filed the day the question is asked, which the owners' notice is counted from
     * @param travel the day the load moves under the wires
     */
    @GetMapping("/clearance")
    @Requires(Role.READER)
    public ClearanceOut clearance(
            @RequestParam String origin,
            @RequestParam List<String> segments,
            @RequestParam String height,
            @RequestParam LocalDate travel,
            @RequestParam Optional<LocalDate> filed) {
        return work.apply(uow -> {
            EntityManager db = uow.entityManager();
            Envelope envelope = Envelope.read(LEGAL_WIDTH, height, LEGAL_LENGTH);
            Itinerary itinerary = new Itinerary(Books.networkFrom(db), origin, List.copyOf(segments));
            LineReading found =
                    Lines.under(itinerary, envelope, Books.linesFrom(db), filed.orElseGet(LocalDate::now), travel);
            return ClearanceOut.of(envelope, found);
        });
    }

    /** One wire, as last measured. */
    @GetMapping("/{ident}")
    @Requires(Role.READER)
    public CrossingOut readCrossing(@PathVariable String ident) {
        return work.apply(uow -> CrossingOut.of(uow.lines().row(ident)));
    }

    /** Write what the crew found, replacing what the card said. */
    @PutMapping("/{ident}/measurement")
    @Requires(Role.SUPERVISOR)
    public CrossingOut remeasure(@PathVariable String ident, @Valid @RequestBody MeasureIn body) {
        return work.apply(uow -> CrossingOut.of(uow.lines()
                .remeasure(
                        ident,
                        Inches.parse(body.height()),
                        body.measured(),
                        body.liftTo().map(Inches::parse).orElse(null))));
    }
}
