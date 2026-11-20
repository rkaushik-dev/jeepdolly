package io.github.rkaushikdev.jeepdolly.persistence;

import jakarta.persistence.EntityExistsException;
import java.sql.SQLDataException;
import java.sql.SQLIntegrityConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.DataException;

/**
 * The database refused a row on one of its own constraints: a key taken twice, a foreign key with
 * nothing at the other end, a check the row does not pass, a value longer than its column.
 *
 * <p>This is not a {@link io.github.rkaushikdev.jeepdolly.Refusal}. The domain refuses what it can
 * see coming, in words a hauler can read; a constraint the database had to enforce is the last line
 * behind that, and reaching it means a caller skipped a check it should have made.
 */
public final class IntegrityViolation extends RuntimeException {

    IntegrityViolation(String message, Throwable cause) {
        super(message, cause);
    }

    /** Whether {@code trouble}, or anything that caused it, is a constraint the database enforced. */
    static boolean isViolation(Throwable trouble) {
        for (Throwable one = trouble; one != null; one = one.getCause()) {
            if (one instanceof ConstraintViolationException
                    || one instanceof DataException
                    || one instanceof SQLIntegrityConstraintViolationException
                    || one instanceof SQLDataException
                    || one instanceof EntityExistsException) {
                return true;
            }
        }
        return false;
    }
}
