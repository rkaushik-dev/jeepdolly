package io.github.rkaushikdev.jeepdolly.permits;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.SeasonOrder;
import io.github.rkaushikdev.jeepdolly.conditions.Assemble;
import io.github.rkaushikdev.jeepdolly.conditions.Condition;
import io.github.rkaushikdev.jeepdolly.domain.AxleStream;
import io.github.rkaushikdev.jeepdolly.domain.Carrier;
import io.github.rkaushikdev.jeepdolly.domain.Commodity;
import io.github.rkaushikdev.jeepdolly.domain.Envelope;
import io.github.rkaushikdev.jeepdolly.domain.HighwayClass;
import io.github.rkaushikdev.jeepdolly.escorts.EscortRules;
import io.github.rkaushikdev.jeepdolly.escorts.Escorts;
import io.github.rkaushikdev.jeepdolly.escorts.Marking;
import io.github.rkaushikdev.jeepdolly.escorts.Naming;
import io.github.rkaushikdev.jeepdolly.fees.Pricing;
import io.github.rkaushikdev.jeepdolly.fees.Quote;
import io.github.rkaushikdev.jeepdolly.limits.Classification;
import io.github.rkaushikdev.jeepdolly.limits.PermitKind;
import io.github.rkaushikdev.jeepdolly.limits.SizeTable;
import io.github.rkaushikdev.jeepdolly.limits.StatutoryBook;
import io.github.rkaushikdev.jeepdolly.limits.StatutoryTable;
import io.github.rkaushikdev.jeepdolly.limits.WeightReading;
import io.github.rkaushikdev.jeepdolly.routing.Evaluate;
import io.github.rkaushikdev.jeepdolly.routing.Itinerary;
import io.github.rkaushikdev.jeepdolly.routing.Mileage;
import io.github.rkaushikdev.jeepdolly.routing.Obstruction;
import io.github.rkaushikdev.jeepdolly.routing.RouteReading;
import io.github.rkaushikdev.jeepdolly.routing.Search;
import io.github.rkaushikdev.jeepdolly.routing.Select;
import io.github.rkaushikdev.jeepdolly.structures.Analysis;
import io.github.rkaushikdev.jeepdolly.structures.StructureFinding;
import io.github.rkaushikdev.jeepdolly.structures.StructureReading;
import io.github.rkaushikdev.jeepdolly.travel.TravelLimits;
import io.github.rkaushikdev.jeepdolly.travel.Windows;
import io.github.rkaushikdev.jeepdolly.utilities.LineReading;
import io.github.rkaushikdev.jeepdolly.utilities.Lines;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The one pass that decides an application, and everything it worked out.
 *
 * <p>Everything before this measures one thing. This runs them all in the right order against one
 * application and produces either a permit or a list of reasons. It is the only place that knows the
 * order, and the order is the office procedure rather than a technical dependency.
 *
 * <p>Carrier first, because a suspended carrier is not entitled to the office's afternoon, and the
 * enforcement hold with it: a carrier that has neither paid nor contested a citation for seven weeks
 * has stopped answering letters, and the office holds new permits until it starts again. Commodity
 * next, because a divisible load is refused before anybody walks a route. Then the route, either the
 * one written on the application or one the router finds. Then weight, which needs the route
 * because the limits differ by class of road. Then classification, which needs both. Then the
 * structures, the overhead lines, the escorts, the marking, the travel limits, and last the price,
 * because the price depends on the classification and the mileage.
 *
 * <p>The lines come after the structures because they are the same kind of finding read the other
 * way up, and because a hauler who is going to be refused for a bridge would rather hear about the
 * bridge and the wire in one letter. The escorts come after both, because a bridge crossed under
 * conditions wants pilot cars to stop the traffic, so the escort table reads the structures as well
 * as the load. The pilot cars a hauler has named are read after the escort table, because the table
 * says how many are wanted and the naming is checked against that. A card that is no good is a
 * refusal even when the load would otherwise be fine, and naming nobody at all is not.
 *
 * <p>Three of the steps are named methods rather than paragraphs of {@link #assess}, because they are
 * the three somebody reads on their own: whether the carrier is entitled to anything ({@link
 * #entitled}), what table the load is weighed against ({@link #weigh}), and what is in the way of it
 * ({@link #walkRoute}). The rest is a sequence and reads as one.
 */
public final class Assessor {

    /** When the router assumes a load sets off, for the curfews it reads. */
    public static final LocalTime DEPARTURE = LocalTime.of(9, 0);

    private Assessor() {}

    /**
     * The class of road on the route with the least generous gross limit, the first of them on a
     * tie.
     *
     * @throws Impossible when the route runs over no road this office writes permits for
     */
    public static HighwayClass governingClass(Itinerary itinerary, StatutoryBook statutes, LocalDate day) {
        List<HighwayClass> permittable =
                itinerary.classes().stream().filter(HighwayClass::permittable).toList();
        if (permittable.isEmpty()) {
            throw new Impossible("the route runs over no road this office writes permits for");
        }
        return Collections.min(
                permittable,
                Comparator.comparingInt(one -> statutes.on(one, day).gross().value()));
    }

    /**
     * Whether this carrier is entitled to the office's afternoon at all.
     *
     * <p>Two questions and neither of them looks at the load. A suspended carrier is refused, and so
     * is one that has neither paid nor argued a citation for seven weeks. Both are about the carrier
     * rather than the trip, which is why they are read before anything is measured, and both come
     * back together rather than the first one.
     *
     * @return the reasons the carrier is refused, standing first; empty when it is entitled
     */
    public static List<String> entitled(Office office, Application application, LocalDate day, Pounds gross) {
        List<String> found = new ArrayList<>();
        Carrier carrier = office.carriers().get(application.usdot());
        carrier.standing(application.start(), application.end(), gross.value())
                .filter(reason -> !reason.isEmpty())
                .ifPresent(found::add);
        office.enforcement()
                .hold(application.usdot(), day)
                .filter(reason -> !reason.isEmpty())
                .ifPresent(found::add);
        return List.copyOf(found);
    }

    /**
     * Read the load against the tightest table the route touches.
     *
     * <p>A trip that runs sixteen miles on the interstate and two hundred yards on a state aid road
     * is held to the state aid table for the whole trip. That is conservative and it is what the
     * office does, because a permit legal for part of a route is not a thing that can be printed on
     * one sheet. The seasonal order read is the one in force in the district the trip starts in.
     */
    public static WeightReading weigh(Office office, Itinerary itinerary, AxleStream stream, LocalDate day) {
        StatutoryTable table = office.statutes().on(governingClass(itinerary, office.statutes(), day), day);
        Optional<SeasonOrder> order =
                office.seasons().inForce("district " + itinerary.districts().get(0), day);
        return WeightReading.read(stream, table, order);
    }

    /** Everything in the way of this load on this route on this day, corners included. */
    public static RouteReading walkRoute(
            Office office, Itinerary itinerary, Envelope envelope, Pounds gross, LocalDate day) {
        return Evaluate.evaluate(
                itinerary,
                envelope,
                gross,
                day,
                office.restrictions(),
                office.seasons(),
                office.statutes(),
                office.turns());
    }

    /** Run the whole procedure once, as of the first day of travel, and report everything it found. */
    public static Assessment assess(Office office, Application application) {
        return assess(office, application, application.start());
    }

    /**
     * Run the whole procedure once, as of {@code day}, and report everything it found.
     *
     * @throws io.github.rkaushikdev.jeepdolly.NotOnFile when the carrier or the commodity is not on
     *     file, which is a mistake on the application rather than a reason to refuse it
     */
    public static Assessment assess(Office office, Application application, LocalDate day) {
        List<String> refusals = new ArrayList<>();
        Envelope envelope = application.combination().envelope();
        AxleStream stream = application.combination().stream();
        Pounds gross = stream.gross();

        refusals.addAll(entitled(office, application, day, gross));
        Commodity commodity = office.commodities().get(application.commodity());

        Optional<Itinerary> found = routeFor(office, application, envelope, gross, day);
        if (found.isEmpty()) {
            refusals.add("no route this load can take on the days asked for");
            return new Assessment(application, refusals);
        }
        Itinerary itinerary = found.get();

        WeightReading weight = weigh(office, itinerary, stream, day);
        SizeTable sizeTable = office.sizes()
                .tightest(itinerary.classes().stream()
                        .filter(HighwayClass::permittable)
                        .toList());
        Classification classification = Classification.classify(envelope, weight, sizeTable, commodity);
        classification.refusal().ifPresent(refusals::add);

        RouteReading route = walkRoute(office, itinerary, envelope, gross, day);
        route.fatal().stream().map(Obstruction::toString).forEach(refusals::add);

        StructureReading structures = Analysis.analyseRoute(office.structures(), itinerary, stream);
        for (StructureFinding finding : structures.refusals()) {
            refusals.add(finding.structure().name() + " will not take this load");
        }

        LineReading lines = Lines.under(itinerary, envelope, office.lines(), application.filed(), day);
        refusals.addAll(lines.refusals());

        boolean superload = classification.kind() == PermitKind.SUPERLOAD;
        Escorts escorts = EscortRules.STANDARD.required(envelope, itinerary.classes(), superload, structures);
        Naming naming = Naming.check(office.escorts(), application.escortCards(), escorts, day);
        refusals.addAll(naming.refusals());
        TravelLimits limits = Windows.limitsFor(envelope, superload, commodity.highCentre(), itinerary.classes());
        Marking marking = Marking.forTrip(envelope, limits, superload);
        Mileage mileage = Mileage.chargeable(itinerary);

        Optional<Quote> quote = Optional.empty();
        List<Condition> conditions = List.of();
        if (classification.kind() != PermitKind.NONE) {
            quote = Optional.of(Pricing.price(
                    office.schedules().on(day),
                    classification.kind(),
                    mileage,
                    weight.worstOverage(),
                    classification.kind().needsEngineer()));
            conditions = Assemble.assemble(
                    office.library(),
                    application.start().toString(),
                    application.end().toString(),
                    limits,
                    escorts,
                    marking,
                    Optional.of(route),
                    Optional.of(structures),
                    stream.liftableCount(),
                    Optional.of(naming),
                    Optional.of(lines));
        } else if (refusals.isEmpty()) {
            refusals.add("this load is legal and needs no permit");
        }

        return new Assessment(
                application,
                refusals,
                Optional.of(itinerary),
                Optional.of(classification),
                Optional.of(weight),
                Optional.of(route),
                Optional.of(structures),
                Optional.of(lines),
                Optional.of(escorts),
                Optional.of(naming),
                Optional.of(marking),
                Optional.of(limits),
                Optional.of(mileage),
                quote,
                conditions);
    }

    /** The route written on the application, or the one the router finds when none was written. */
    private static Optional<Itinerary> routeFor(
            Office office, Application application, Envelope envelope, Pounds gross, LocalDate day) {
        if (!application.route().isEmpty()) {
            return Optional.of(new Itinerary(office.network(), application.origin(), application.route()));
        }
        Search search = new Search(envelope, gross, day).withDeparture(DEPARTURE);
        return Select.find(
                office.network(),
                application.origin(),
                application.destination(),
                search,
                office.restrictions(),
                office.seasons(),
                office.statutes(),
                office.turns());
    }
}
