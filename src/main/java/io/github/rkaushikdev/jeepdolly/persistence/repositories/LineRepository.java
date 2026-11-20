package io.github.rkaushikdev.jeepdolly.persistence.repositories;

import io.github.rkaushikdev.jeepdolly.Conflict;
import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.NotOnFile;
import io.github.rkaushikdev.jeepdolly.persistence.models.LineCrossingRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.LineOwnerRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.SegmentRow;
import io.github.rkaushikdev.jeepdolly.utilities.Crossing;
import io.github.rkaushikdev.jeepdolly.utilities.LineOwner;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;

/**
 * Overhead lines and the people who own them.
 *
 * <p>A crossing is edited here, which none of the other repositories allow. That is the difference
 * between a record of the world and a record of a decision: when the crew raises a span, the height
 * on the card is simply wrong until somebody changes it.
 */
public final class LineRepository {

    private final EntityManager entityManager;

    public LineRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /** The entity manager this repository asks its questions of. */
    public EntityManager entityManager() {
        return entityManager;
    }

    /** @throws NotOnFile when no owner with that ident is on file */
    public LineOwnerRow ownerRow(String ident) {
        LineOwnerRow found = entityManager.find(LineOwnerRow.class, ident);
        if (found == null) {
            throw new NotOnFile("no line owner " + ident, Facts.of("ident", ident));
        }
        return found;
    }

    /** @throws NotOnFile when no crossing with that ident is on file */
    public LineCrossingRow row(String ident) {
        LineCrossingRow found = entityManager.find(LineCrossingRow.class, ident);
        if (found == null) {
            throw new NotOnFile("no crossing " + ident, Facts.of("ident", ident));
        }
        return found;
    }

    /** Every owner, by ident. */
    public List<LineOwnerRow> owners() {
        return entityManager
                .createQuery("select o from LineOwnerRow o order by o.ident", LineOwnerRow.class)
                .getResultList();
    }

    /** Every crossing, by ident. */
    public List<LineCrossingRow> crossings() {
        return entityManager
                .createQuery("select c from LineCrossingRow c order by c.ident", LineCrossingRow.class)
                .getResultList();
    }

    /** The crossings over one segment, by ident. */
    public List<LineCrossingRow> crossings(String segment) {
        return entityManager
                .createQuery(
                        "select c from LineCrossingRow c where c.segmentIdent = :segment order by c.ident",
                        LineCrossingRow.class)
                .setParameter("segment", segment)
                .getResultList();
    }

    /** @throws Conflict when an owner with that ident is already on file */
    public LineOwnerRow addOwner(LineOwner owner) {
        if (entityManager.find(LineOwnerRow.class, owner.ident()) != null) {
            throw new Conflict("line owner " + owner.ident() + " is already on file");
        }
        LineOwnerRow row = LineOwnerRow.of(owner);
        entityManager.persist(row);
        return row;
    }

    /** File a wire with no survey day written down. */
    public LineCrossingRow add(Crossing crossing) {
        return add(crossing, null);
    }

    /**
     * File a measured wire, refusing one that hangs over no known road.
     *
     * <p>The road is checked here rather than left to the foreign key, which would refuse it only
     * at commit time; a survey keyed in at speed is exactly where a wrong segment comes from.
     *
     * @param measured the day of the survey, or null when nobody wrote it down
     * @throws Conflict when a crossing with that ident is already on file
     * @throws NotOnFile when the owner or the segment is not on file
     */
    public LineCrossingRow add(Crossing crossing, LocalDate measured) {
        if (entityManager.find(LineCrossingRow.class, crossing.ident()) != null) {
            throw new Conflict("crossing " + crossing.ident() + " is already on file");
        }
        ownerRow(crossing.owner());
        if (entityManager.find(SegmentRow.class, crossing.segment()) == null) {
            throw new NotOnFile("no segment " + crossing.segment(), Facts.of("segment", crossing.segment()));
        }
        LineCrossingRow row = LineCrossingRow.of(crossing, measured);
        entityManager.persist(row);
        return row;
    }

    /** A survey's new height for a wire that cannot be lifted. */
    public LineCrossingRow remeasure(String ident, Inches height, LocalDate on) {
        return remeasure(ident, height, on, null);
    }

    /**
     * Write a new measurement onto a crossing, which is what a survey does.
     *
     * <p>The lift height is passed every time rather than left alone when it is omitted. A crew that
     * raises a span permanently changes both numbers, and keeping the old lift height would leave the
     * card saying a wire lifts to a height it now hangs above.
     *
     * @param liftTo what the owner can now raise it to, or null when it cannot be lifted
     * @throws Conflict when the lift height is not above the new height
     */
    public LineCrossingRow remeasure(String ident, Inches height, LocalDate on, Inches liftTo) {
        LineCrossingRow row = row(ident);
        if (liftTo != null && liftTo.compareTo(height) <= 0) {
            throw new Conflict("crossing " + ident + " cannot be lifted to below where it hangs");
        }
        row.setHeight(height);
        row.setLiftTo(liftTo);
        row.setMeasured(on);
        return row;
    }

    /** Every wire hanging below a height, lowest first, which is the survey list. */
    public List<LineCrossingRow> under(Inches height) {
        return entityManager
                .createQuery(
                        "select c from LineCrossingRow c where c.height < :height order by c.height, c.ident",
                        LineCrossingRow.class)
                .setParameter("height", height)
                .getResultList();
    }

    public long count() {
        return entityManager
                .createQuery("select count(c) from LineCrossingRow c", Long.class)
                .getSingleResult();
    }
}
