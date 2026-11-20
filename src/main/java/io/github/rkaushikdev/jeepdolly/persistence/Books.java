package io.github.rkaushikdev.jeepdolly.persistence;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.domain.CarrierRegister;
import io.github.rkaushikdev.jeepdolly.domain.Curfew;
import io.github.rkaushikdev.jeepdolly.domain.Network;
import io.github.rkaushikdev.jeepdolly.domain.Restriction;
import io.github.rkaushikdev.jeepdolly.domain.RestrictionBook;
import io.github.rkaushikdev.jeepdolly.enforcement.Account;
import io.github.rkaushikdev.jeepdolly.enforcement.Citation;
import io.github.rkaushikdev.jeepdolly.enforcement.CitationState;
import io.github.rkaushikdev.jeepdolly.enforcement.EnforcementBook;
import io.github.rkaushikdev.jeepdolly.enforcement.Owed;
import io.github.rkaushikdev.jeepdolly.enforcement.Standing;
import io.github.rkaushikdev.jeepdolly.escorts.EscortRegister;
import io.github.rkaushikdev.jeepdolly.escorts.Operator;
import io.github.rkaushikdev.jeepdolly.persistence.models.CarrierRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.CitationRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.LineCrossingRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.LineOwnerRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.NodeRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.PilotCardRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.PilotCompanyRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.RestrictionRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.SegmentRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.StructureRow;
import io.github.rkaushikdev.jeepdolly.structures.StructureBook;
import io.github.rkaushikdev.jeepdolly.utilities.Crossing;
import io.github.rkaushikdev.jeepdolly.utilities.LineBook;
import io.github.rkaushikdev.jeepdolly.utilities.LineOwner;
import jakarta.persistence.EntityManager;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Building the domain books off the rows, which nothing but an entity manager needs.
 *
 * <p>These live in the persistence layer rather than the web layer because a command line report
 * wants the road log too, and importing a web framework to get a network is the kind of dependency
 * that only ever gets worse. Nothing here is cached: the live books must be read every time or the
 * office writes permits against yesterday.
 */
public final class Books {

    private Books() {}

    /** The whole road log, nodes first so the segments have ends to join. */
    public static Network networkFrom(EntityManager entityManager) {
        Network network = new Network();
        for (NodeRow node : all(entityManager, NodeRow.class)) {
            network.addNode(node.toDomain());
        }
        for (SegmentRow segment : all(entityManager, SegmentRow.class)) {
            network.addSegment(segment.toDomain());
        }
        return network;
    }

    /**
     * Every restriction on file, expired ones included. The book filters by day when it is asked,
     * not when it is built, because the same book answers questions about a trip next month.
     */
    public static RestrictionBook restrictionsFrom(EntityManager entityManager) {
        RestrictionBook book = new RestrictionBook();
        for (RestrictionRow row : all(entityManager, RestrictionRow.class)) {
            book.add(restrictionFrom(row));
        }
        return book;
    }

    /** One restriction row, with its curfew reassembled if it has one. */
    public static Restriction restrictionFrom(RestrictionRow row) {
        Restriction.Builder built = Restriction.builder(
                        row.getIdent(), row.getSegmentIdent(), row.getKind(), row.getNote())
                .height(row.getHeight().orElse(null))
                .width(row.getWidth().orElse(null))
                .weight(row.getWeight().orElse(null))
                .region(row.getRegion().orElse(null))
                .effective(row.getEffective().orElse(null))
                .expires(row.getExpires().orElse(null));
        String days = row.getCurfewDays().orElse("");
        if (!days.isEmpty()
                && row.getCurfewFrom().isPresent()
                && row.getCurfewTo().isPresent()) {
            built.curfew(new Curfew(
                    weekdays(days), row.getCurfewFrom().get(), row.getCurfewTo().get()));
        }
        return built.build();
    }

    /**
     * The weekday digits a curfew is stored as, Monday as {@code 0}, back into days of the week.
     *
     * @throws Impossible when a digit is not a day of the week
     */
    static Set<DayOfWeek> weekdays(String digits) {
        Set<DayOfWeek> found = EnumSet.noneOf(DayOfWeek.class);
        for (char one : digits.toCharArray()) {
            int day = Character.digit(one, 10);
            if (day < 0 || day > 6) {
                throw new Impossible("a curfew names days of the week");
            }
            found.add(DayOfWeek.of(day + 1));
        }
        return found;
    }

    /** Every structure on file, postings and ratings as they stand. */
    public static StructureBook structuresFrom(EntityManager entityManager) {
        StructureBook book = new StructureBook();
        for (StructureRow row : all(entityManager, StructureRow.class)) {
            book.add(row.toDomain());
        }
        return book;
    }

    /**
     * Every carrier, with insurance and bond read as at one day. The day matters: the same rows
     * produce a different register for a trip in August than for one in December.
     */
    public static CarrierRegister carriersFrom(EntityManager entityManager, LocalDate on) {
        CarrierRegister register = new CarrierRegister();
        for (CarrierRow row : all(entityManager, CarrierRow.class)) {
            register.add(row.toDomain(on));
        }
        return register;
    }

    /**
     * The pilot car register, companies first so the cards have somewhere to go.
     *
     * <p>A card whose company has gone is dropped rather than raised: the foreign key means it
     * should not happen, and a register that refuses to build over one orphan row would take the
     * whole office down over a piece of housekeeping.
     */
    public static EscortRegister escortsFrom(EntityManager entityManager) {
        EscortRegister register = new EscortRegister();
        for (PilotCompanyRow row : all(entityManager, PilotCompanyRow.class)) {
            register.addCompany(row.toDomain());
        }
        Set<String> companies =
                register.companies().stream().map(one -> one.ident()).collect(Collectors.toSet());
        for (PilotCardRow card : all(entityManager, PilotCardRow.class)) {
            Operator operator = card.toDomain();
            if (companies.contains(operator.company())) {
                register.certify(operator);
            }
        }
        return register;
    }

    /** What every carrier owes, with the usual grace before an unpaid citation holds them. */
    public static EnforcementBook enforcementFrom(EntityManager entityManager) {
        return enforcementFrom(entityManager, Standing.GRACE_DAYS);
    }

    /**
     * What every carrier owes, grouped, with the day each one falls due.
     *
     * <p>Only citations still expecting money are read: a paid one is settled, and a dismissed or
     * voided one was never owed. The grace is passed rather than read, because it is an office rule
     * set once in the settings.
     */
    public static EnforcementBook enforcementFrom(EntityManager entityManager, int grace) {
        Map<Integer, List<Owed>> owed = new LinkedHashMap<>();
        for (CitationRow row : all(entityManager, CitationRow.class)) {
            if (!row.getState().owing()) {
                continue;
            }
            owed.computeIfAbsent(row.getUsdot(), usdot -> new ArrayList<>())
                    .add(new Owed(
                            row.getNumber(),
                            row.getWritten(),
                            row.getAmount(),
                            Standing.dueDay(row.getWritten(), Citation.CONTEST_DAYS, grace),
                            row.getState() == CitationState.CONTESTED));
        }
        EnforcementBook book = new EnforcementBook();
        owed.forEach((usdot, unpaid) -> book.add(new Account(
                usdot,
                unpaid.stream().sorted(Comparator.comparing(Owed::number)).toList())));
        return book;
    }

    /**
     * Every measured wire, owners first so the crossings have somewhere to go. A crossing whose
     * owner has gone is dropped rather than raised, the same rule the pilot register follows.
     */
    public static LineBook linesFrom(EntityManager entityManager) {
        LineBook book = new LineBook();
        for (LineOwnerRow row : all(entityManager, LineOwnerRow.class)) {
            book.addOwner(row.toDomain());
        }
        Set<String> known = book.owners().stream().map(LineOwner::ident).collect(Collectors.toSet());
        for (LineCrossingRow wire : all(entityManager, LineCrossingRow.class)) {
            Crossing crossing = wire.toDomain();
            if (known.contains(crossing.owner())) {
                book.add(crossing);
            }
        }
        return book;
    }

    private static <T> List<T> all(EntityManager entityManager, Class<T> type) {
        return entityManager
                .createQuery("select r from " + type.getSimpleName() + " r", type)
                .getResultList();
    }
}
