package io.github.rkaushikdev.jeepdolly.api;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.conditions.Condition;
import io.github.rkaushikdev.jeepdolly.domain.Axle;
import io.github.rkaushikdev.jeepdolly.domain.AxleKind;
import io.github.rkaushikdev.jeepdolly.domain.AxleStream;
import io.github.rkaushikdev.jeepdolly.domain.Combination;
import io.github.rkaushikdev.jeepdolly.domain.CommodityBook;
import io.github.rkaushikdev.jeepdolly.domain.Envelope;
import io.github.rkaushikdev.jeepdolly.domain.Piece;
import io.github.rkaushikdev.jeepdolly.domain.TireSize;
import io.github.rkaushikdev.jeepdolly.domain.Unit;
import io.github.rkaushikdev.jeepdolly.domain.UnitKind;
import io.github.rkaushikdev.jeepdolly.fees.Quote;
import io.github.rkaushikdev.jeepdolly.permits.Applicant;
import io.github.rkaushikdev.jeepdolly.permits.Application;
import io.github.rkaushikdev.jeepdolly.permits.Permit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The bodies that go over the wire, and the one rule about measures.
 *
 * <p>Every measure crosses as a string. A width is {@code "14 ft 6 in"}, a weight is {@code
 * "132,400 lb"}, a fee is {@code "$188.72"}: not a number, not a number with a unit field beside it,
 * but a string that reads the way it reads on the permit. The client of this interface is a
 * carrier's own software, written by whoever they could get, and the number that comes back goes
 * onto a screen in front of a driver. A JSON number invites the client to do arithmetic on it, and
 * the arithmetic they do will be in feet with a decimal point.
 *
 * <p>Incoming is the same in reverse. The parsers already accept everything the office receives on
 * paper, so the interface accepts it too, and one hauler's software sending {@code 14'6"} and
 * another sending {@code 174} both work.
 */
public final class Schemas {

    /** What a list that must hold something is told when it is empty. */
    public static final String AT_LEAST_ONE = "List should have at least 1 item after validation";

    private Schemas() {}

    /** The box, as the hauler measured it. Overhangs left out are nothing. */
    public record EnvelopeIn(
            @NotNull(message = Rejected.REQUIRED) String width,
            @NotNull(message = Rejected.REQUIRED) String height,
            @NotNull(message = Rejected.REQUIRED) String length,
            String frontOverhang,
            String rearOverhang) {

        public EnvelopeIn {
            frontOverhang = frontOverhang == null ? "0" : frontOverhang;
            rearOverhang = rearOverhang == null ? "0" : rearOverhang;
        }

        public Envelope toDomain() {
            return Envelope.read(width, height, length, frontOverhang, rearOverhang);
        }
    }

    /**
     * One axle, with the gap to the axle in front of it.
     *
     * @param spacingBefore the gap to the axle in front, which every axle but the first must give
     */
    public record AxleIn(
            @NotNull(message = Rejected.REQUIRED) String kind,
            @NotNull(message = Rejected.REQUIRED) String weight,
            Integer tires,
            String tireWidth,
            Boolean liftable,
            Optional<String> spacingBefore) {

        public AxleIn {
            tires = tires == null ? Axle.TIRES : tires;
            tireWidth = tireWidth == null ? String.valueOf(Axle.TIRE_WIDTH.value()) : tireWidth;
            liftable = liftable != null && liftable;
            spacingBefore = spacingBefore == null ? Optional.empty() : spacingBefore;
        }

        public Axle toDomain() {
            return new Axle(AxleKind.of(kind), Pounds.parse(weight), tires, TireSize.widthOf(tireWidth), liftable);
        }
    }

    /** One piece of equipment in the string. */
    public record UnitIn(
            @NotNull(message = Rejected.REQUIRED) String kind,
            @NotNull(message = Rejected.REQUIRED) Integer axles,
            @NotNull(message = Rejected.REQUIRED) String plate,
            @NotNull(message = Rejected.REQUIRED) String tare,
            String make,
            String unitNumber) {

        public UnitIn {
            make = make == null ? "" : make;
            unitNumber = unitNumber == null ? "" : unitNumber;
        }

        public Unit toDomain() {
            return new Unit(UnitKind.of(kind), axles, plate, Pounds.parse(tare), make, unitNumber);
        }
    }

    /** One thing on the trailer. */
    public record PieceIn(
            @NotNull(message = Rejected.REQUIRED) String commodity,
            @NotNull(message = Rejected.REQUIRED) String description,
            Optional<String> serial) {

        public PieceIn {
            serial = serial == null ? Optional.empty() : serial;
        }
    }

    /** Who to ring about it. */
    public record ApplicantIn(
            @NotNull(message = Rejected.REQUIRED) String name,
            @NotNull(message = Rejected.REQUIRED) String phone,
            String email) {

        public ApplicantIn {
            email = email == null ? "" : email;
        }

        public Applicant toDomain() {
            return new Applicant(name, phone, email);
        }
    }

    /** Everything a hauler sends to ask for a permit. */
    public record ApplicationIn(
            @NotNull(message = Rejected.REQUIRED) Integer usdot,
            @NotNull(message = Rejected.REQUIRED) String commodity,
            @NotNull(message = Rejected.REQUIRED) String origin,
            @NotNull(message = Rejected.REQUIRED) String destination,
            @NotNull(message = Rejected.REQUIRED) LocalDate start,
            @NotNull(message = Rejected.REQUIRED) LocalDate end,
            @NotNull(message = Rejected.REQUIRED) @Valid ApplicantIn applicant,
            @NotNull(message = Rejected.REQUIRED) @Valid EnvelopeIn envelope,
            @NotNull(message = Rejected.REQUIRED) @Size(min = 1, message = AT_LEAST_ONE) @Valid List<AxleIn> axles,
            @NotNull(message = Rejected.REQUIRED) @Size(min = 1, message = AT_LEAST_ONE) @Valid List<UnitIn> units,
            @NotNull(message = Rejected.REQUIRED) @Size(min = 1, message = AT_LEAST_ONE) @Valid List<PieceIn> pieces,
            List<String> route,
            List<String> escortCards,
            String notes) {

        public ApplicationIn {
            route = route == null ? List.of() : List.copyOf(route);
            escortCards = escortCards == null ? List.of() : List.copyOf(escortCards);
            notes = notes == null ? "" : notes;
        }

        /**
         * The application this body describes, filed under {@code reference} on {@code filed}.
         *
         * @throws Impossible when an axle after the first does not say how far behind it stands
         * @throws io.github.rkaushikdev.jeepdolly.NotOnFile when a piece names a commodity nobody
         *     filed
         */
        public Application toDomain(String reference, LocalDate filed, CommodityBook commodities) {
            List<Inches> spacings = new ArrayList<>();
            for (int position = 1; position < axles.size(); position++) {
                Optional<String> gap = axles.get(position).spacingBefore();
                if (gap.isEmpty()) {
                    throw new Impossible("axle " + (position + 1) + " does not say how far behind it stands");
                }
                spacings.add(Inches.parse(gap.get()));
            }
            AxleStream stream =
                    new AxleStream(axles.stream().map(AxleIn::toDomain).toList(), spacings);
            Combination combination =
                    new Combination(units.stream().map(UnitIn::toDomain).toList(), stream, envelope.toDomain());
            List<Piece> found = pieces.stream()
                    .map(one -> new Piece(commodities.get(one.commodity()), one.description(), one.serial(), List.of()))
                    .toList();
            return Application.builder()
                    .reference(reference)
                    .usdot(usdot)
                    .commodity(commodity)
                    .pieces(found)
                    .combination(combination)
                    .origin(origin)
                    .destination(destination)
                    .start(start)
                    .end(end)
                    .applicant(applicant.toDomain())
                    .filed(filed)
                    .route(route)
                    .escortCards(escortCards)
                    .notes(notes)
                    .build();
        }
    }

    /** One condition as it prints. */
    public record ConditionOut(String code, String category, String text) {

        public static ConditionOut of(Condition condition) {
            return new ConditionOut(condition.code(), condition.category().value(), condition.text());
        }
    }

    /** One line of what it cost. */
    public record FeeLineOut(String description, String amount) {}

    /** What it cost, line by line. */
    public record QuoteOut(List<FeeLineOut> lines, String total, String schedule) {

        public QuoteOut {
            lines = List.copyOf(lines);
        }

        public static QuoteOut of(Quote quote) {
            return new QuoteOut(
                    quote.lines().stream()
                            .map(one -> new FeeLineOut(
                                    one.description(), one.amount().toString()))
                            .toList(),
                    quote.total().toString(),
                    quote.schedule());
        }
    }

    /** An application as the office holds it. */
    public record ApplicationOut(
            String reference,
            int usdot,
            String state,
            String origin,
            String destination,
            LocalDate start,
            LocalDate end,
            String gross,
            String width,
            String height,
            String length,
            List<String> escortCards) {

        public ApplicationOut {
            escortCards = List.copyOf(escortCards);
        }

        public static ApplicationOut of(Application application) {
            Envelope envelope = application.combination().envelope();
            return new ApplicationOut(
                    application.reference(),
                    application.usdot(),
                    application.state().value(),
                    application.origin(),
                    application.destination(),
                    application.start(),
                    application.end(),
                    application.gross().toString(),
                    envelope.width().toString(),
                    envelope.height().toString(),
                    envelope.length().toString(),
                    application.escortCards());
        }
    }

    /** A permit as it is printed and as it is read at the roadside. */
    public record PermitOut(
            String number,
            String application,
            int usdot,
            String carrier,
            String state,
            LocalDate issued,
            LocalDate start,
            LocalDate end,
            String origin,
            String destination,
            List<String> route,
            String narrative,
            String gross,
            String distance,
            List<ConditionOut> conditions,
            QuoteOut fees) {

        public PermitOut {
            route = List.copyOf(route);
            conditions = List.copyOf(conditions);
        }

        public static PermitOut of(Permit permit) {
            return new PermitOut(
                    permit.number().toString(),
                    permit.application(),
                    permit.usdot(),
                    permit.carrierName(),
                    permit.state().value(),
                    permit.issued(),
                    permit.start(),
                    permit.end(),
                    permit.origin(),
                    permit.destination(),
                    permit.route(),
                    permit.narrative(),
                    permit.gross().toString(),
                    permit.distance().toString(),
                    permit.conditions().stream().map(ConditionOut::of).toList(),
                    QuoteOut.of(permit.quote()));
        }
    }

    /**
     * What the office worked out, whether or not it can be permitted.
     *
     * @param distance the chargeable distance, when a route was found
     * @param fees the price, when the load can be permitted
     */
    public record AssessmentOut(
            String reference,
            boolean permittable,
            String kind,
            List<String> refusals,
            List<String> route,
            Optional<String> distance,
            List<ConditionOut> conditions,
            Optional<QuoteOut> fees) {

        public AssessmentOut {
            refusals = List.copyOf(refusals);
            route = List.copyOf(route);
            conditions = List.copyOf(conditions);
        }

        /** An assessment that stopped short of a distance, conditions or a price. */
        public AssessmentOut(
                String reference, boolean permittable, String kind, List<String> refusals, List<String> route) {
            this(reference, permittable, kind, refusals, route, Optional.empty(), List.of(), Optional.empty());
        }
    }
}
