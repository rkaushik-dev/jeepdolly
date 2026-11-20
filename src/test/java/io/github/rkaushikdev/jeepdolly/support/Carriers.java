package io.github.rkaushikdev.jeepdolly.support;

import io.github.rkaushikdev.jeepdolly.Dollars;
import io.github.rkaushikdev.jeepdolly.domain.Bond;
import io.github.rkaushikdev.jeepdolly.domain.Carrier;
import io.github.rkaushikdev.jeepdolly.domain.CarrierRegister;
import io.github.rkaushikdev.jeepdolly.domain.Insurance;
import java.time.LocalDate;

/** The two carriers the suite shares: one in good standing and one that is not. */
public final class Carriers {

    /** Bickford Heavy Haul, insured and bonded for 2025. */
    public static final int BICKFORD = 1204471;

    /** Aroostook Crane Service, with no paper on file. */
    public static final int AROOSTOOK = 2299001;

    private Carriers() {}

    /** One carrier in good standing and one that is not. */
    public static CarrierRegister aCarrierRegister() {
        CarrierRegister register = new CarrierRegister();
        Insurance cover = new Insurance(
                "CA-88214", "Acadia Mutual", Dollars.of(1000000), LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));
        Bond bond = new Bond(
                "SB-4471", "Penobscot Surety", Dollars.of(25000), LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));
        register.add(Carrier.builder(BICKFORD, "Bickford Heavy Haul", "Newport", "ME")
                .insurance(cover)
                .bond(bond)
                .build());
        register.add(new Carrier(AROOSTOOK, "Aroostook Crane Service", "Presque Isle", "ME"));
        return register;
    }
}
