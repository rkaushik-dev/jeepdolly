package io.github.rkaushikdev.jeepdolly.api;

import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.persistence.models.ApiKeyRow;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Api keys: how one is made, how it is stored, and how a request's key becomes a {@link Caller}.
 *
 * <p>A key arrives in the {@value #HEADER} header. It is hashed and looked up. If there is no live
 * row for that hash the answer is 401 and nothing else, with no hint about whether the key was wrong
 * or revoked. The table holds only the hash, so a key is handed out exactly once, when it is made.
 */
public final class Security {

    /** The header a key arrives in. */
    public static final String HEADER = "X-Permit-Key";

    /** Keys are issued with this many bytes of randomness. */
    public static final int KEY_BYTES = 24;

    private static final SecureRandom RANDOM = new SecureRandom();

    private Security() {}

    /** A key just made, and the row that now holds its hash. */
    public record IssuedKey(String key, ApiKeyRow row) {}

    /** The SHA-256 hash the table holds, in lower case hex, which is all it ever holds. */
    public static String digestOf(String key) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha.digest(key.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException("every Java runtime carries SHA-256", missing);
        }
    }

    /** A fresh key, URL safe, handed out once and never stored. */
    public static String newKey() {
        byte[] bytes = new byte[KEY_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Make an office key, store its hash, and hand the key back exactly once. */
    public static IssuedKey issueKey(UnitOfWork work, String ident, String person, Role role, LocalDate on) {
        return issueKey(work, ident, person, role, on, Optional.empty(), "");
    }

    /** Make a carrier's own key, limited to that carrier's permits. */
    public static IssuedKey issueKey(UnitOfWork work, String ident, String person, Role role, LocalDate on, int usdot) {
        return issueKey(work, ident, person, role, on, Optional.of(usdot), "");
    }

    /**
     * Make a key, store its hash, and hand the key back exactly once.
     *
     * @param usdot the carrier a carrier's key belongs to, or empty for an office key
     * @param note why the key was issued, for whoever reads the table later
     */
    public static IssuedKey issueKey(
            UnitOfWork work,
            String ident,
            String person,
            Role role,
            LocalDate on,
            Optional<Integer> usdot,
            String note) {
        String key = newKey();
        ApiKeyRow row = new ApiKeyRow(ident, digestOf(key), person, role.value(), usdot.orElse(null), on, note);
        work.add(row);
        return new IssuedKey(key, row);
    }

    /**
     * Read the key a request sent into a caller, as at one day.
     *
     * @throws NotAuthenticated when no key was sent, or the key has no live row
     */
    public static Caller callerFor(UnitOfWork work, Optional<String> key, LocalDate on) {
        if (key.filter(one -> !one.isEmpty()).isEmpty()) {
            throw new NotAuthenticated("a permit key is required");
        }
        Optional<ApiKeyRow> found = work.entityManager()
                .createQuery("select k from ApiKeyRow k where k.digest = :digest", ApiKeyRow.class)
                .setParameter("digest", digestOf(key.get()))
                .getResultStream()
                .findFirst();
        if (found.isEmpty() || !found.get().liveOn(on)) {
            throw new NotAuthenticated("that key is not in use");
        }
        ApiKeyRow row = found.get();
        return new Caller(row.getIdent(), row.getPerson(), Role.of(row.getRole()), row.getUsdot());
    }

    /**
     * Refuse anybody below {@code wanted}, saying what was needed and what was held.
     *
     * @throws NotPermitted when the caller's rung does not reach {@code wanted}
     */
    public static Caller needs(Caller who, Role wanted) {
        if (!who.may(wanted)) {
            throw new NotPermitted("this needs a " + wanted + ", and you are a " + who.role());
        }
        return who;
    }
}
