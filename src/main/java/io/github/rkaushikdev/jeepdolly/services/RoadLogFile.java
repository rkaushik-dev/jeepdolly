package io.github.rkaushikdev.jeepdolly.services;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Miles;
import io.github.rkaushikdev.jeepdolly.Refusal;
import io.github.rkaushikdev.jeepdolly.Written;
import io.github.rkaushikdev.jeepdolly.domain.Jurisdiction;
import io.github.rkaushikdev.jeepdolly.domain.Node;
import io.github.rkaushikdev.jeepdolly.domain.Route;
import io.github.rkaushikdev.jeepdolly.domain.Segment;
import io.github.rkaushikdev.jeepdolly.persistence.models.NodeRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.SegmentRow;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Loading the road log, which arrives twice a year as a text file.
 *
 * <p>The state publishes the log as one line per segment, pipe separated, in the order the fields
 * are written on the paper form. It is produced by a system older than anybody here and it will not
 * be changing, so this reads it as it is.
 *
 * <p>Loading is idempotent and it is not destructive. A segment already on file is updated in
 * place. A segment on file and not in the load is left alone, because half the loads that arrive
 * are partial and deleting on absence would empty the log the first time somebody sent a district
 * instead of the state. Every line that cannot be read is refused with its line number and the file
 * carries on: a load of nine thousand segments with three bad lines is a load the office wants.
 */
public final class RoadLogFile {

    /** What separates the fields of a published line. */
    public static final String SEPARATOR = "|";

    /** How many fields a published line carries. */
    public static final int FIELDS = 9;

    private RoadLogFile() {}

    /** What a load did, and what it could not read. */
    public static final class Loaded {

        /** One line the load could not read, numbered from one, and why. */
        public record Refused(int line, String why) {}

        private final List<String> added = new ArrayList<>();
        private final List<String> updated = new ArrayList<>();
        private final List<Refused> refused = new ArrayList<>();

        /** The segments the load put on file for the first time. */
        public List<String> added() {
            return Collections.unmodifiableList(added);
        }

        /** The segments already on file that the load brought up to date. */
        public List<String> updated() {
            return Collections.unmodifiableList(updated);
        }

        /** The lines the load could not read, in file order. */
        public List<Refused> refused() {
            return Collections.unmodifiableList(refused);
        }

        /** How many lines went onto the log one way or the other. */
        public int read() {
            return added.size() + updated.size();
        }

        /** A summary line, then one line per refusal, for the command to print. */
        public List<String> lines() {
            List<String> found = new ArrayList<>();
            found.add(added.size() + " added  " + updated.size() + " updated  " + refused.size() + " refused");
            refused.forEach(one -> found.add("line " + one.line() + "  " + one.why()));
            return List.copyOf(found);
        }

        @Override
        public String toString() {
            return read() + " read, " + refused.size() + " refused";
        }
    }

    /** One segment as the published log writes it. */
    public static String written(Segment segment) {
        return String.join(
                SEPARATOR,
                segment.ident(),
                segment.route().toString(),
                segment.start(),
                segment.end(),
                String.valueOf(segment.length().hundredths()),
                segment.jurisdiction().county(),
                segment.jurisdiction().town(),
                String.valueOf(segment.jurisdiction().district()),
                segment.oneWay() ? "1" : "0");
    }

    /**
     * One line of the published log.
     *
     * @throws Refusal naming what is wrong with the line
     */
    public static Segment parse(String line) {
        List<String> parts =
                Arrays.stream(line.split("\\|", -1)).map(String::strip).toList();
        if (parts.size() != FIELDS) {
            throw new Impossible("a log line has " + FIELDS + " fields, this one has " + parts.size());
        }
        String hundredths = parts.get(4);
        String district = parts.get(7);
        if (!Written.isDigits(hundredths)) {
            throw new Impossible(Written.quoted(hundredths) + " is not a length in hundredths");
        }
        if (!Written.isDigits(district)) {
            throw new Impossible(Written.quoted(district) + " is not a district");
        }
        String oneWay = parts.get(8);
        return new Segment(
                parts.get(0),
                Route.parse(parts.get(1)),
                parts.get(2),
                parts.get(3),
                new Miles(Integer.parseInt(hundredths)),
                new Jurisdiction(parts.get(5), parts.get(6), Integer.parseInt(district)),
                oneWay.equals("1") || oneWay.equals("y") || oneWay.equals("Y"));
    }

    /** The whole log as text, in ident order, for a diff against the state's. */
    public static List<String> exportLog(EntityManager db) {
        return db.createQuery("select s from SegmentRow s order by s.ident", SegmentRow.class).getResultList().stream()
                .map(one -> written(one.toDomain()))
                .toList();
    }

    /** Read a published log into the tables, making nodes for ends nobody has filed. */
    public static Loaded loadLog(EntityManager db, List<String> lines) {
        return loadLog(db, lines, true);
    }

    /**
     * Read a published log into the tables, keeping what is not mentioned.
     *
     * <p>Blank lines and lines starting with {@code #} are skipped; every other line counts toward
     * the line numbers a refusal is reported by.
     */
    public static Loaded loadLog(EntityManager db, List<String> lines, boolean makeNodes) {
        Loaded found = new Loaded();
        int number = 0;
        for (String line : lines) {
            number++;
            if (line.isBlank() || line.stripLeading().startsWith("#")) {
                continue;
            }
            Segment segment;
            try {
                segment = parse(line);
            } catch (Refusal refusal) {
                found.refused.add(new Loaded.Refused(number, refusal.getMessage()));
                continue;
            }
            if (makeNodes) {
                for (String name : List.of(segment.start(), segment.end())) {
                    if (db.find(NodeRow.class, name) == null) {
                        db.persist(NodeRow.of(new Node(name, Written.title(name))));
                    }
                }
                db.flush();
            }
            SegmentRow row = db.find(SegmentRow.class, segment.ident());
            if (row == null) {
                db.persist(SegmentRow.of(segment));
                found.added.add(segment.ident());
            } else {
                row.refresh(segment);
                found.updated.add(segment.ident());
            }
        }
        return found;
    }
}
