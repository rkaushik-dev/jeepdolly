package io.github.rkaushikdev.jeepdolly.persistence;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.conditions.Category;
import io.github.rkaushikdev.jeepdolly.conditions.Condition;
import io.github.rkaushikdev.jeepdolly.domain.Axle;
import io.github.rkaushikdev.jeepdolly.domain.AxleStream;
import io.github.rkaushikdev.jeepdolly.domain.Combination;
import io.github.rkaushikdev.jeepdolly.domain.CommodityBook;
import io.github.rkaushikdev.jeepdolly.domain.Envelope;
import io.github.rkaushikdev.jeepdolly.domain.Piece;
import io.github.rkaushikdev.jeepdolly.domain.Unit;
import io.github.rkaushikdev.jeepdolly.fees.FeeLine;
import io.github.rkaushikdev.jeepdolly.fees.Quote;
import io.github.rkaushikdev.jeepdolly.permits.Applicant;
import io.github.rkaushikdev.jeepdolly.permits.Application;
import io.github.rkaushikdev.jeepdolly.permits.Permit;
import io.github.rkaushikdev.jeepdolly.permits.PermitNumber;
import io.github.rkaushikdev.jeepdolly.persistence.models.ApplicationAxleRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.ApplicationPieceRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.ApplicationRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.ApplicationUnitRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.FeeLineRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.PermitConditionRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.PermitRow;
import java.util.List;
import java.util.Optional;

/**
 * Domain documents to rows and back, for the two that are not one to one.
 *
 * <p>Most tables carry their own {@code of} and {@code toDomain} because the mapping is a rename.
 * Applications and permits are not: an application is four tables and a permit is three, and the
 * assembly is worth having in one place where both directions sit next to each other and can be
 * read against each other.
 *
 * <p>The rule here is that the round trip is exact. An application written and read back is equal
 * to the one that went in, and anything that cannot survive the round trip does not belong in the
 * row: it belongs on a report. The domain objects know nothing about the database and the rows know
 * nothing about the rules; this class is the only thing that has heard of both.
 */
public final class Mappers {

    private Mappers() {}

    /** One application into the rows that hold it: the form, its axles, its units, its pieces. */
    public static ApplicationRow applicationToRow(Application application) {
        Envelope envelope = application.combination().envelope();
        ApplicationRow row = new ApplicationRow(application.reference());
        row.setUsdot(application.usdot());
        row.setCommodity(application.commodity());
        row.setOrigin(application.origin());
        row.setDestination(application.destination());
        row.setTravelStart(application.start());
        row.setTravelEnd(application.end());
        row.setFiled(application.filed());
        row.setState(application.state());
        row.setApplicantName(application.applicant().name());
        row.setApplicantPhone(application.applicant().phone());
        row.setApplicantEmail(application.applicant().email());
        row.setRoute(String.join(",", application.route()));
        row.setEscortCards(String.join(",", application.escortCards()));
        row.setNotes(application.notes());
        row.setWidth(envelope.width());
        row.setHeight(envelope.height());
        row.setLength(envelope.length());
        row.setFrontOverhang(envelope.frontOverhang());
        row.setRearOverhang(envelope.rearOverhang());
        AxleStream stream = application.combination().stream();
        for (int position = 0; position < stream.count(); position++) {
            Axle axle = stream.axles().get(position);
            Inches spacing = position == 0 ? null : stream.spacings().get(position - 1);
            row.addAxle(new ApplicationAxleRow(
                    position, axle.kind(), axle.weight(), axle.tires(), axle.tireWidth(), axle.liftable(), spacing));
        }
        List<Unit> units = application.combination().units();
        for (int position = 0; position < units.size(); position++) {
            Unit unit = units.get(position);
            row.addUnit(new ApplicationUnitRow(
                    position, unit.kind(), unit.axles(), unit.plate(), unit.tare(), unit.make(), unit.unitNumber()));
        }
        List<Piece> pieces = application.pieces();
        for (int position = 0; position < pieces.size(); position++) {
            Piece piece = pieces.get(position);
            row.addPiece(new ApplicationPieceRow(
                    position,
                    piece.commodity().code(),
                    piece.description(),
                    piece.serial().orElse(null)));
        }
        return row;
    }

    /**
     * The rows back into one application, its pieces pointing at the commodity table.
     *
     * @throws Impossible when the application has no axles on file, rather than guessing a stream
     */
    public static Application rowToApplication(ApplicationRow row, CommodityBook commodities) {
        if (row.getAxles().isEmpty()) {
            throw new Impossible("application " + row.getReference() + " has no axles on file");
        }
        List<Axle> axles = row.getAxles().stream()
                .map(one ->
                        new Axle(one.getKind(), one.getWeight(), one.getTires(), one.getTireWidth(), one.isLiftable()))
                .toList();
        List<Inches> spacings = row.getAxles().subList(1, row.getAxles().size()).stream()
                .map(ApplicationAxleRow::getSpacingBefore)
                .flatMap(Optional::stream)
                .toList();
        List<Unit> units = row.getUnits().stream()
                .map(one -> new Unit(
                        one.getKind(),
                        one.getAxles(),
                        one.getPlate(),
                        one.getTare(),
                        one.getMake(),
                        one.getUnitNumber()))
                .toList();
        Envelope envelope = new Envelope(
                row.getWidth(), row.getHeight(), row.getLength(), row.getFrontOverhang(), row.getRearOverhang());
        List<Piece> pieces = row.getPieces().stream()
                .map(one -> new Piece(
                        commodities.get(one.getCommodity()), one.getDescription(), one.getSerial(), List.of()))
                .toList();
        return Application.builder()
                .reference(row.getReference())
                .usdot(row.getUsdot())
                .commodity(row.getCommodity())
                .pieces(pieces)
                .combination(new Combination(units, new AxleStream(axles, spacings), envelope))
                .origin(row.getOrigin())
                .destination(row.getDestination())
                .start(row.getTravelStart())
                .end(row.getTravelEnd())
                .applicant(new Applicant(row.getApplicantName(), row.getApplicantPhone(), row.getApplicantEmail()))
                .filed(row.getFiled())
                .route(row.getRouteIdents())
                .escortCards(row.getCards())
                .state(row.getState())
                .notes(row.getNotes() == null ? "" : row.getNotes())
                .build();
    }

    /** One permit into the rows that hold it: the permit, its printed conditions, its fee lines. */
    public static PermitRow permitToRow(Permit permit) {
        PermitRow row = new PermitRow(permit.number().toString());
        row.setSeries(permit.number().series().value());
        row.setApplication(permit.application());
        row.setUsdot(permit.usdot());
        row.setCarrierName(permit.carrierName());
        row.setIssued(permit.issued());
        row.setValidFrom(permit.start());
        row.setValidTo(permit.end());
        row.setOrigin(permit.origin());
        row.setDestination(permit.destination());
        row.setRoute(String.join(",", permit.route()));
        row.setNarrative(permit.narrative());
        row.setGross(permit.gross());
        Envelope envelope = permit.envelope();
        row.setWidth(envelope.width());
        row.setHeight(envelope.height());
        row.setLength(envelope.length());
        row.setFrontOverhang(envelope.frontOverhang());
        row.setRearOverhang(envelope.rearOverhang());
        row.setDistance(permit.distance());
        row.setState(permit.state());
        row.setVoidReason(permit.voidReason());
        row.setSupersededBy(permit.supersededBy().map(PermitNumber::toString).orElse(null));
        row.setSupersedes(permit.supersedes().map(PermitNumber::toString).orElse(null));
        row.setSchedule(permit.quote().schedule());
        List<Condition> conditions = permit.conditions();
        for (int position = 0; position < conditions.size(); position++) {
            Condition condition = conditions.get(position);
            row.addCondition(new PermitConditionRow(
                    position, condition.code(), condition.category().value(), condition.text(), condition.order()));
        }
        List<FeeLine> lines = permit.quote().lines();
        for (int position = 0; position < lines.size(); position++) {
            FeeLine line = lines.get(position);
            row.addFee(new FeeLineRow(position, line.description(), line.amount()));
        }
        return row;
    }

    /** The rows back into one permit, in the words and amounts it was printed with. */
    public static Permit rowToPermit(PermitRow row) {
        List<Condition> conditions = row.getConditions().stream()
                .map(one -> new Condition(
                        one.getCode(), Category.of(one.getCategory()), one.getBody(), one.getPrintOrder()))
                .toList();
        Quote quote = new Quote(
                row.getFees().stream()
                        .map(one -> new FeeLine(one.getDescription(), one.getAmount()))
                        .toList(),
                row.getSchedule() == null ? "" : row.getSchedule());
        return Permit.builder()
                .number(PermitNumber.parse(row.getNumber()))
                .application(row.getApplication())
                .usdot(row.getUsdot())
                .carrierName(row.getCarrierName())
                .issued(row.getIssued())
                .start(row.getValidFrom())
                .end(row.getValidTo())
                .origin(row.getOrigin())
                .destination(row.getDestination())
                .route(row.getRouteIdents())
                .narrative(row.getNarrative())
                .gross(row.getGross())
                .envelope(new Envelope(
                        row.getWidth(),
                        row.getHeight(),
                        row.getLength(),
                        row.getFrontOverhang(),
                        row.getRearOverhang()))
                .distance(row.getDistance())
                .conditions(conditions)
                .quote(quote)
                .state(row.getState())
                .voidReason(row.getVoidReason() == null ? "" : row.getVoidReason())
                .supersededBy(row.getSupersededBy().map(PermitNumber::parse).orElse(null))
                .supersedes(row.getSupersedes().map(PermitNumber::parse).orElse(null))
                .build();
    }
}
