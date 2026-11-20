package io.github.rkaushikdev.jeepdolly.persistence.repositories;

import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.NotOnFile;
import io.github.rkaushikdev.jeepdolly.persistence.models.RestrictionRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.SegmentRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.StructureRow;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;

/** The road log and what is wrong with it: segments, their restrictions and their structures. */
public final class NetworkRepository {

    private final EntityManager entityManager;

    public NetworkRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /** The entity manager this repository asks its questions of. */
    public EntityManager entityManager() {
        return entityManager;
    }

    /** @throws NotOnFile when no segment with that ident is on the road log */
    public SegmentRow segment(String ident) {
        SegmentRow found = entityManager.find(SegmentRow.class, ident);
        if (found == null) {
            throw new NotOnFile("no segment " + ident, Facts.of("segment", ident));
        }
        return found;
    }

    /** Every segment, by ident. */
    public List<SegmentRow> segments() {
        return entityManager
                .createQuery("select s from SegmentRow s order by s.ident", SegmentRow.class)
                .getResultList();
    }

    /** The segments of one district, by ident. */
    public List<SegmentRow> inDistrict(int district) {
        return entityManager
                .createQuery(
                        "select s from SegmentRow s where s.district = :district order by s.ident", SegmentRow.class)
                .setParameter("district", district)
                .getResultList();
    }

    /** The restrictions on one segment that are in force on a day. */
    public List<RestrictionRow> restrictionsOn(String ident, LocalDate day) {
        return entityManager
                .createQuery("select r from RestrictionRow r where r.segmentIdent = :ident", RestrictionRow.class)
                .setParameter("ident", ident)
                .getResultList()
                .stream()
                .filter(one -> inForce(one, day))
                .toList();
    }

    /** @throws NotOnFile when no structure with that ident is on the inventory */
    public StructureRow structure(String ident) {
        StructureRow found = entityManager.find(StructureRow.class, ident);
        if (found == null) {
            throw new NotOnFile("no structure " + ident, Facts.of("structure", ident));
        }
        return found;
    }

    /** The structures on one segment, by ident. */
    public List<StructureRow> structuresOn(String ident) {
        return entityManager
                .createQuery(
                        "select s from StructureRow s where s.segmentIdent = :ident order by s.ident",
                        StructureRow.class)
                .setParameter("ident", ident)
                .getResultList();
    }

    /** Every posted structure, by ident. */
    public List<StructureRow> postedStructures() {
        return entityManager
                .createQuery(
                        "select s from StructureRow s where s.posted is not null order by s.ident", StructureRow.class)
                .getResultList();
    }

    private static boolean inForce(RestrictionRow restriction, LocalDate day) {
        if (restriction.getEffective().filter(day::isBefore).isPresent()) {
            return false;
        }
        return restriction.getExpires().filter(day::isAfter).isEmpty();
    }
}
