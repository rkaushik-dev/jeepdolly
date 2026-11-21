package io.github.rkaushikdev.jeepdolly.api.routers;

import io.github.rkaushikdev.jeepdolly.Conflict;
import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.Unreadable;
import io.github.rkaushikdev.jeepdolly.Written;
import io.github.rkaushikdev.jeepdolly.api.Caller;
import io.github.rkaushikdev.jeepdolly.api.Rejected;
import io.github.rkaushikdev.jeepdolly.api.Requires;
import io.github.rkaushikdev.jeepdolly.api.Role;
import io.github.rkaushikdev.jeepdolly.api.Work;
import io.github.rkaushikdev.jeepdolly.domain.HighwayClass;
import io.github.rkaushikdev.jeepdolly.limits.StatutoryBook;
import io.github.rkaushikdev.jeepdolly.limits.StatutoryTable;
import io.github.rkaushikdev.jeepdolly.persistence.TariffStore;
import io.github.rkaushikdev.jeepdolly.persistence.models.StatutoryRow;
import io.github.rkaushikdev.jeepdolly.services.Audit;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Filing a new published table, which happens twice a legislative session.
 *
 * <p>The tables are rows, so filing one is a request rather than a release. It is a supervisor's job
 * and it lands on the trail, because a limit that moved without anybody knowing who moved it is the
 * sort of thing that ends up in a newspaper.
 *
 * <p>A table takes effect on a day and never before. Filing one dated next month changes nothing
 * today, which is the point: the office types it in when the paper arrives and it starts working on
 * the day the statute says.
 *
 * <p>Nothing here edits a table. There is no route to change a number, only to file a new one, and
 * the read routes say which is in force on which day.
 */
@RestController
@RequestMapping("/tariff")
@Tag(name = "tariff")
public class TariffController {

    private final Work work;

    public TariffController(Work work) {
        this.work = work;
    }

    /**
     * Read a class of road, refusing rather than failing on a name nobody uses.
     *
     * @throws Unreadable when it is not a class of road
     */
    public static HighwayClass roadClass(String written) {
        try {
            return HighwayClass.of(written);
        } catch (IllegalArgumentException unknown) {
            throw new Unreadable(Written.quoted(written) + " is not a class of road", Facts.of("written", written));
        }
    }

    /**
     * A weight table as the statute publishes it.
     *
     * @param beyond the allowance for each axle past the quad
     * @param tireRate pounds per inch of tire on the ground
     */
    public record StatutoryIn(
            @NotNull(message = Rejected.REQUIRED) String highwayClass,
            @NotNull(message = Rejected.REQUIRED) LocalDate effective,
            @NotNull(message = Rejected.REQUIRED) String single,
            @NotNull(message = Rejected.REQUIRED) String tandem,
            @NotNull(message = Rejected.REQUIRED) String tridem,
            @NotNull(message = Rejected.REQUIRED) String quad,
            @NotNull(message = Rejected.REQUIRED) String beyond,
            @NotNull(message = Rejected.REQUIRED) String gross,
            @NotNull(message = Rejected.REQUIRED) Integer tireRate) {}

    /** A weight table as the office holds it. */
    public record StatutoryOut(
            String highwayClass,
            LocalDate effective,
            String single,
            String tandem,
            String tridem,
            String quad,
            String gross,
            int tireRate) {

        public static StatutoryOut of(StatutoryRow row) {
            return new StatutoryOut(
                    row.getHighwayClass().value(),
                    row.getEffective(),
                    row.getSingle().toString(),
                    row.getTandem().toString(),
                    row.getTridem().toString(),
                    row.getQuad().toString(),
                    row.getGross().toString(),
                    row.getTireRate());
        }
    }

    private static Optional<StatutoryRow> tableRow(EntityManager db, HighwayClass highwayClass, LocalDate effective) {
        return Optional.ofNullable(db.find(StatutoryRow.class, new StatutoryRow.Key(highwayClass.value(), effective)));
    }

    /**
     * File a weight table, which takes effect on the day it names.
     *
     * @throws Conflict when the class of road already has a table taking effect that day
     */
    @PostMapping("/statutory")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires(Role.SUPERVISOR)
    public StatutoryOut fileStatutoryTable(@Valid @RequestBody StatutoryIn body, Caller who) {
        return work.apply(uow -> {
            EntityManager db = uow.entityManager();
            HighwayClass highwayClass = roadClass(body.highwayClass());
            if (tableRow(db, highwayClass, body.effective()).isPresent()) {
                throw new Conflict(
                        "a " + highwayClass + " table already takes effect on " + body.effective(),
                        Facts.of("highway_class", body.highwayClass()));
            }
            StatutoryRow row = new StatutoryRow(
                    highwayClass,
                    body.effective(),
                    Pounds.parse(body.single()),
                    Pounds.parse(body.tandem()),
                    Pounds.parse(body.tridem()),
                    Pounds.parse(body.quad()),
                    Pounds.parse(body.beyond()),
                    Pounds.parse(body.gross()),
                    body.tireRate());
            uow.add(row);
            Audit.record(
                    db,
                    "tariff.filed",
                    who.person(),
                    "tariff",
                    body.highwayClass() + " " + body.effective(),
                    "gross " + body.gross());
            return StatutoryOut.of(row);
        });
    }

    /**
     * What is in force on a day, one table per class of road.
     *
     * @throws io.github.rkaushikdev.jeepdolly.NotOnFile when a class of road has no table in force
     */
    @GetMapping("/statutory")
    @Requires(Role.READER)
    public List<StatutoryOut> statutoryTables(@RequestParam Optional<LocalDate> day) {
        LocalDate when = day.orElseGet(LocalDate::now);
        return work.apply(uow -> {
            EntityManager db = uow.entityManager();
            StatutoryBook book = TariffStore.statutoryBook(db);
            List<StatutoryOut> found = new ArrayList<>();
            for (HighwayClass highwayClass : HighwayClass.values()) {
                StatutoryTable table = book.on(highwayClass, when);
                tableRow(db, highwayClass, table.effective())
                        .map(StatutoryOut::of)
                        .ifPresent(found::add);
            }
            return found;
        });
    }

    /** Every table one class of road has ever had, oldest first. */
    @GetMapping("/statutory/{highway_class}")
    @Requires(Role.READER)
    public List<StatutoryOut> tablesForClass(@PathVariable("highway_class") String highwayClass) {
        return work.apply(uow -> {
            HighwayClass wanted = roadClass(highwayClass);
            return uow
                    .entityManager()
                    .createQuery("select s from StatutoryRow s order by s.effective", StatutoryRow.class)
                    .getResultList()
                    .stream()
                    .filter(one -> one.getHighwayClass() == wanted)
                    .map(StatutoryOut::of)
                    .toList();
        });
    }
}
