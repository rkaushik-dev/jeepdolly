package io.github.rkaushikdev.jeepdolly.support;

import io.github.rkaushikdev.jeepdolly.escorts.Company;
import io.github.rkaushikdev.jeepdolly.escorts.Endorsement;
import io.github.rkaushikdev.jeepdolly.escorts.EscortRegister;
import io.github.rkaushikdev.jeepdolly.escorts.Operator;
import java.time.LocalDate;
import java.util.Set;

/**
 * The pilot car register the suite shares: one company with three cards, one of them run out.
 *
 * <p>Named for the pilot cars rather than for escorts, so it never collides with the escort
 * table's own {@code Escorts}.
 */
public final class Pilots {

    private Pilots() {}

    /** One pilot car company with three cards, one of them run out. */
    public static EscortRegister anEscortRegister() {
        EscortRegister register = new EscortRegister();
        register.addCompany(new Company("PC-1", "Kennebec Pilot Car", "Waterville", "207 555 0142"));
        register.certify(
                new Operator("C-1001", "R Dubois", "PC-1", LocalDate.of(2023, 5, 1), LocalDate.of(2026, 4, 30)));
        register.certify(new Operator(
                "C-1002",
                "M Levesque",
                "PC-1",
                LocalDate.of(2023, 5, 1),
                LocalDate.of(2026, 4, 30),
                Set.of(Endorsement.HEIGHT_POLE)));
        register.certify(
                new Operator("C-1003", "A Poulin", "PC-1", LocalDate.of(2021, 5, 1), LocalDate.of(2024, 4, 30)));
        return register;
    }
}
