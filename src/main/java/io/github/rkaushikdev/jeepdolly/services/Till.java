package io.github.rkaushikdev.jeepdolly.services;

import io.github.rkaushikdev.jeepdolly.Dollars;
import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.NotOnFile;
import io.github.rkaushikdev.jeepdolly.Written;
import io.github.rkaushikdev.jeepdolly.persistence.models.FeeLineRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.PermitRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.ReceiptRow;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Taking money, and proving at the end of the day that it adds up.
 *
 * <p>Three questions and nothing else. What is owed on a permit: the fee that was printed, less
 * every receipt against it, so a permit with nothing against it is owed in full and one paid twice
 * is owed a negative amount, which is the office's own way of finding the mistake. What is in the
 * drawer: cash and cheques taken today, the number the clerk counts against the paper. What is
 * outstanding: permits issued and not paid, oldest first, the list that goes to the carrier before
 * anybody is suspended for it.
 *
 * <p>Nothing here decides whether to issue a permit. A hauler on account drives out with a permit
 * and an invoice, and a hauler who never pays gets suspended by a supervisor, not by this.
 */
public final class Till {

    private Till() {}

    /** One permit and what is still owed on it. */
    public record Owing(String permit, String carrier, LocalDate issued, Dollars charged, Dollars paid) {

        public Dollars owed() {
            return charged.minus(paid);
        }

        public boolean settled() {
            return owed().cents() == 0;
        }

        @Override
        public String toString() {
            return permit + "  " + carrier + "  " + charged + "  " + paid + "  " + owed();
        }
    }

    /** What was taken on one day, split by whether it is in the drawer. */
    public record Drawer(LocalDate day, Dollars inTheDrawer, Dollars onAccount, int receipts) {

        public Dollars taken() {
            return inTheDrawer.plus(onAccount);
        }

        /** The till slip the clerk initials. */
        public List<String> lines() {
            return List.of(
                    "till " + day,
                    "in the drawer  " + inTheDrawer,
                    "on account  " + onAccount,
                    receipts + " receipts  " + taken());
        }

        @Override
        public String toString() {
            return receipts + " receipts, " + inTheDrawer + " in the drawer";
        }
    }

    /** Everything printed on a permit's fee lines. */
    public static Dollars chargedOn(EntityManager db, String number) {
        return Dollars.total(db
                .createQuery("select f from FeeLineRow f where f.number = :number", FeeLineRow.class)
                .setParameter("number", number)
                .getResultList()
                .stream()
                .map(FeeLineRow::getAmount)
                .toList());
    }

    /** Everything taken against a permit, refunds included. */
    public static Dollars paidOn(EntityManager db, String number) {
        return Dollars.total(db
                .createQuery("select r from ReceiptRow r where r.permit = :number", ReceiptRow.class)
                .setParameter("number", number)
                .getResultList()
                .stream()
                .map(ReceiptRow::getAmount)
                .toList());
    }

    /** Write a receipt against a permit, with no reference or note. */
    public static ReceiptRow take(
            EntityManager db, String number, Dollars amount, String method, String by, LocalDate on) {
        return take(db, number, amount, method, by, on, "", "");
    }

    /**
     * Write a receipt against a permit.
     *
     * @throws Impossible for a way of paying the office does not take, or a receipt for nothing
     * @throws NotOnFile when nobody issued the permit
     */
    public static ReceiptRow take(
            EntityManager db,
            String number,
            Dollars amount,
            String method,
            String by,
            LocalDate on,
            String reference,
            String note) {
        if (!ReceiptRow.METHODS.contains(method)) {
            throw new Impossible(
                    Written.quoted(method) + " is not a way this office takes money", Facts.of("method", method));
        }
        if (amount.cents() == 0) {
            throw new Impossible("a receipt is for an amount");
        }
        if (db.find(PermitRow.class, number) == null) {
            throw new NotOnFile("no permit " + number, Facts.of("number", number));
        }
        ReceiptRow row = new ReceiptRow(number, on, method, amount, by, reference, note);
        db.persist(row);
        return row;
    }

    /**
     * What one permit was charged and what is left on it.
     *
     * @throws NotOnFile when nobody issued the permit
     */
    public static Owing owingOn(EntityManager db, String number) {
        PermitRow permit = db.find(PermitRow.class, number);
        if (permit == null) {
            throw new NotOnFile("no permit " + number, Facts.of("number", number));
        }
        return new Owing(
                number, permit.getCarrierName(), permit.getIssued(), chargedOn(db, number), paidOn(db, number));
    }

    /** Every permit issued by a day and not settled, oldest first. */
    public static List<Owing> outstanding(EntityManager db, LocalDate on) {
        List<Owing> found = new ArrayList<>();
        for (PermitRow row : db.createQuery(
                        "select p from PermitRow p where p.issued <= :on order by p.issued, p.number", PermitRow.class)
                .setParameter("on", on)
                .getResultList()) {
            Owing owing = owingOn(db, row.getNumber());
            if (!owing.settled()) {
                found.add(owing);
            }
        }
        return List.copyOf(found);
    }

    /** What was taken on one day, split the way the clerk counts it. */
    public static Drawer drawer(EntityManager db, LocalDate day) {
        List<ReceiptRow> rows = db.createQuery("select r from ReceiptRow r where r.taken = :day", ReceiptRow.class)
                .setParameter("day", day)
                .getResultList();
        return new Drawer(
                day,
                Dollars.total(rows.stream()
                        .filter(ReceiptRow::isInTheDrawer)
                        .map(ReceiptRow::getAmount)
                        .toList()),
                Dollars.total(rows.stream()
                        .filter(one -> !one.isInTheDrawer())
                        .map(ReceiptRow::getAmount)
                        .toList()),
                rows.size());
    }

    /** What one carrier still owes on permits issued by a day, which is what the letter says. */
    public static Dollars owedByCarrier(EntityManager db, int usdot, LocalDate on) {
        return Dollars.total(db
                .createQuery("select p from PermitRow p where p.usdot = :usdot and p.issued <= :on", PermitRow.class)
                .setParameter("usdot", usdot)
                .setParameter("on", on)
                .getResultList()
                .stream()
                .map(one -> owingOn(db, one.getNumber()).owed())
                .toList());
    }
}
