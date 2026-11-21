package io.github.rkaushikdev.jeepdolly.api.routers;

import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.NotOnFile;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.Written;
import io.github.rkaushikdev.jeepdolly.api.Requires;
import io.github.rkaushikdev.jeepdolly.api.Role;
import io.github.rkaushikdev.jeepdolly.api.Work;
import io.github.rkaushikdev.jeepdolly.domain.Envelope;
import io.github.rkaushikdev.jeepdolly.permits.Office;
import io.github.rkaushikdev.jeepdolly.routing.Evaluate;
import io.github.rkaushikdev.jeepdolly.routing.Itinerary;
import io.github.rkaushikdev.jeepdolly.routing.Mileage;
import io.github.rkaushikdev.jeepdolly.routing.RouteReading;
import io.github.rkaushikdev.jeepdolly.routing.Search;
import io.github.rkaushikdev.jeepdolly.routing.Select;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Route routes: check one, find one, and ask what is in the way.
 *
 * <p>This is the part of the interface carriers actually use. A dispatcher with a load and a map
 * wants to know whether the road will take it before anybody fills in an application, and the
 * office would much rather answer that question a hundred times than refuse one application.
 * Nothing here writes anything and nothing needs more than a reader: there is no document, there is
 * a question, so the load is described in the body rather than filed.
 */
@RestController
@RequestMapping("/routes")
@Tag(name = "routes")
public class RoutesController {

    private final Work work;

    public RoutesController(Work work) {
        this.work = work;
    }

    /** One thing in the way, and what it needed. */
    public record ObstructionOut(
            String segment, String kind, String note, String needed, String allowed, boolean fatal) {}

    /** A route, its mileage, and what is in the way of it. */
    public record RouteOut(
            List<String> segments,
            List<String> towns,
            String narrative,
            String distance,
            int chargeableMiles,
            boolean clear,
            List<ObstructionOut> obstructions) {

        public RouteOut {
            segments = List.copyOf(segments);
            towns = List.copyOf(towns);
            obstructions = List.copyOf(obstructions);
        }
    }

    /**
     * The load, as a dispatcher describes it over the telephone. Anything left out is the legal
     * load that fits everywhere.
     */
    public record LoadQuery(String width, String height, String length, String rearOverhang, String gross) {

        public LoadQuery {
            width = width == null ? "8'6" : width;
            height = height == null ? "13'6" : height;
            length = length == null ? "70'" : length;
            rearOverhang = rearOverhang == null ? "0" : rearOverhang;
            gross = gross == null ? "80000" : gross;
        }

        public Envelope envelope() {
            return Envelope.read(width, height, length, "0", rearOverhang);
        }

        public Pounds weight() {
            return Pounds.parse(gross);
        }
    }

    private static RouteOut shape(Itinerary itinerary, Office desk, LoadQuery load, LocalDate day) {
        RouteReading reading = Evaluate.evaluate(
                itinerary,
                load.envelope(),
                load.weight(),
                day,
                desk.restrictions(),
                desk.seasons(),
                desk.statutes(),
                desk.turns());
        return new RouteOut(
                itinerary.idents(),
                itinerary.towns().stream().map(Written::title).toList(),
                itinerary.narrative(),
                itinerary.distance().toString(),
                Mileage.chargeable(itinerary).charged(),
                reading.clear(),
                reading.obstructions().stream()
                        .map(one -> new ObstructionOut(
                                one.segment(), one.kind(), one.note(), one.needed(), one.allowed(), one.fatal()))
                        .toList());
    }

    /** Walk a route the caller has written and say what is in the way. */
    @PostMapping("/check")
    @Requires(Role.READER)
    public RouteOut checkRoute(
            @RequestBody LoadQuery load,
            @RequestParam List<String> segments,
            @RequestParam String origin,
            @RequestParam Optional<LocalDate> day) {
        LocalDate when = day.orElseGet(LocalDate::now);
        return work.apply(uow -> {
            Office desk = work.office(uow);
            return shape(new Itinerary(desk.network(), origin, segments), desk, load, when);
        });
    }

    /**
     * Find the cheapest route this load can take on a day.
     *
     * @throws NotOnFile naming both ends when there is no route the load can take
     */
    @PostMapping("/find")
    @Requires(Role.READER)
    public RouteOut findRoute(
            @RequestBody LoadQuery load,
            @RequestParam String origin,
            @RequestParam String destination,
            @RequestParam Optional<LocalDate> day) {
        LocalDate when = day.orElseGet(LocalDate::now);
        return work.apply(uow -> {
            Office desk = work.office(uow);
            Search search = new Search(load.envelope(), load.weight(), when);
            Itinerary found = Select.find(
                            desk.network(),
                            origin,
                            destination,
                            search,
                            desk.restrictions(),
                            desk.seasons(),
                            desk.statutes(),
                            desk.turns())
                    .orElseThrow(() -> new NotOnFile(
                            "no route from " + origin + " to " + destination + " this load can take",
                            Facts.of("origin", origin, "destination", destination)));
            return shape(found, desk, load, when);
        });
    }

    /** Everywhere this load can get to from here today. */
    @PostMapping("/reachable")
    @Requires(Role.READER)
    public List<String> reachablePlaces(
            @RequestBody LoadQuery load, @RequestParam String origin, @RequestParam Optional<LocalDate> day) {
        LocalDate when = day.orElseGet(LocalDate::now);
        return work.apply(uow -> {
            Office desk = work.office(uow);
            Search search = new Search(load.envelope(), load.weight(), when);
            return Select.reachable(
                    desk.network(), origin, search, desk.restrictions(), desk.seasons(), desk.statutes(), desk.turns());
        });
    }
}
