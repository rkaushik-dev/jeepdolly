package io.github.rkaushikdev.jeepdolly.support;

import io.github.rkaushikdev.jeepdolly.Written;
import io.github.rkaushikdev.jeepdolly.api.Http;
import io.github.rkaushikdev.jeepdolly.api.Role;
import io.github.rkaushikdev.jeepdolly.api.Security;
import io.github.rkaushikdev.jeepdolly.persistence.Database;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.persistence.models.ApiKeyRow;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

/**
 * Clients signed in as each role, over one database.
 *
 * <p>One live key per role, each named for its role ({@code Reader}, {@code Clerk}, ...). Asking
 * twice on one database reissues rather than colliding: a key is stored as a hash, so the old one
 * cannot be handed back, and a test that wants two clients wants two live keys at once. So take
 * every client a test needs from one call to {@link #clientsFor}, never from two calls to {@link
 * #keyed}: the second call reissues the whole set and the first client's key stops working.
 */
public final class Clients {

    private Clients() {}

    /** One live key for each role, issued today. */
    public static Map<Role, String> keysFor(Database database) {
        LocalDate today = LocalDate.now();
        Map<Role, String> made = new EnumMap<>(Role.class);
        try (UnitOfWork work = database.unitOfWork()) {
            for (Role role : Role.values()) {
                String ident = "k-" + role.value();
                ApiKeyRow standing = work.entityManager().find(ApiKeyRow.class, ident);
                if (standing != null) {
                    work.delete(standing);
                    work.flush();
                }
                made.put(
                        role,
                        Security.issueKey(work, ident, Written.title(role.value()), role, today)
                                .key());
            }
            work.commit();
        }
        return made;
    }

    /** One client per role, every key live at once. */
    public static Map<Role, Http> clientsFor(Http http, Database database) {
        Map<Role, Http> made = new EnumMap<>(Role.class);
        keysFor(database).forEach((role, key) -> made.put(role, http.as(key)));
        return made;
    }

    /** A client signed in as a supervisor. */
    public static Http keyed(Http http, Database database) {
        return keyed(http, database, Role.SUPERVISOR);
    }

    /** A client signed in as one role. */
    public static Http keyed(Http http, Database database, Role role) {
        return http.as(keysFor(database).get(role));
    }
}
