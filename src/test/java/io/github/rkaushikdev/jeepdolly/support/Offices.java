package io.github.rkaushikdev.jeepdolly.support;

import io.github.rkaushikdev.jeepdolly.SeasonBook;
import io.github.rkaushikdev.jeepdolly.conditions.ConditionLibrary;
import io.github.rkaushikdev.jeepdolly.domain.CommodityBook;
import io.github.rkaushikdev.jeepdolly.domain.RestrictionBook;
import io.github.rkaushikdev.jeepdolly.enforcement.EnforcementBook;
import io.github.rkaushikdev.jeepdolly.fees.ScheduleBook;
import io.github.rkaushikdev.jeepdolly.limits.SizeBook;
import io.github.rkaushikdev.jeepdolly.limits.StatutoryBook;
import io.github.rkaushikdev.jeepdolly.permits.Numbering;
import io.github.rkaushikdev.jeepdolly.permits.Office;
import io.github.rkaushikdev.jeepdolly.routing.TurnBook;

/**
 * The whole office wired up against the test network.
 *
 * <p>A test that wants one book different takes {@link #office()}, names that one book on the
 * builder and builds it: restrictions, seasons, turns, escorts, enforcement or lines.
 */
public final class Offices {

    private Offices() {}

    /** The office with every book it reads off the suite's fixtures, ready to have one changed. */
    public static Office.Builder office() {
        return Office.builder()
                .network(RoadLog.roadLog())
                .restrictions(new RestrictionBook())
                .seasons(new SeasonBook())
                .statutes(StatutoryBook.standard())
                .sizes(SizeBook.standard())
                .commodities(CommodityBook.standard())
                .carriers(Carriers.aCarrierRegister())
                .structures(Structures.structureBook())
                .turns(TurnBook.coded())
                .escorts(Pilots.anEscortRegister())
                .enforcement(new EnforcementBook())
                .lines(Wires.aLineBook())
                .schedules(ScheduleBook.standard())
                .library(ConditionLibrary.standard())
                .numbering(new Numbering());
    }

    /** The whole office wired up against the test network. */
    public static Office anOffice() {
        return office().build();
    }
}
