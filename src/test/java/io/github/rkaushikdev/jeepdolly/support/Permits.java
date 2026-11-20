package io.github.rkaushikdev.jeepdolly.support;

import io.github.rkaushikdev.jeepdolly.Dollars;
import io.github.rkaushikdev.jeepdolly.Miles;
import io.github.rkaushikdev.jeepdolly.conditions.ConditionLibrary;
import io.github.rkaushikdev.jeepdolly.fees.FeeLine;
import io.github.rkaushikdev.jeepdolly.fees.Quote;
import io.github.rkaushikdev.jeepdolly.permits.Application;
import io.github.rkaushikdev.jeepdolly.permits.Permit;
import io.github.rkaushikdev.jeepdolly.permits.PermitNumber;
import io.github.rkaushikdev.jeepdolly.permits.Series;
import java.util.List;

/**
 * An issued permit for an application, priced and conditioned the way the excavator move is.
 *
 * <p>A test that wants it with one thing different takes {@link #permit(Application)}, changes that
 * one field on the builder, and builds it, the way the original passed one keyword.
 */
public final class Permits {

    /** The number the excavator move's permit carries. */
    public static final PermitNumber NUMBER = new PermitNumber(2025, Series.SINGLE, 4471);

    /** The four segments the excavator move is written over. */
    public static final List<String> ROUTE = List.of("US201-1", "US201-2", "SA148-2", "SR43-1");

    private Permits() {}

    /** The permit for an application as issued, ready to have one field changed and be built. */
    public static Permit.Builder permit(Application application) {
        ConditionLibrary library = ConditionLibrary.standard();
        return Permit.builder()
                .number(NUMBER)
                .application(application.reference())
                .usdot(application.usdot())
                .carrierName("Bickford Heavy Haul")
                .issued(application.filed())
                .start(application.start())
                .end(application.end())
                .origin(application.origin())
                .destination(application.destination())
                .route(ROUTE)
                .narrative("from Waterville, US 201 to Skowhegan")
                .gross(application.gross())
                .envelope(application.combination().envelope())
                .distance(Miles.of(35, 20))
                .conditions(List.of(library.render("R-01"), library.render("G-01")))
                .quote(new Quote(
                        List.of(
                                new FeeLine("ordinary permit", Dollars.of(40)),
                                new FeeLine("34 miles on arterial", Dollars.of(6, 12))),
                        "schedule of 2024-01-01"));
    }

    /** An issued permit for an application, priced and conditioned. */
    public static Permit aPermit(Application application) {
        return permit(application).build();
    }
}
