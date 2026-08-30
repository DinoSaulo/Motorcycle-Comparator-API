package com.motorcycle.comparison.exception;

import org.springframework.dao.DataIntegrityViolationException;

import java.util.Locale;

/** Reads the name of the database constraint a write violated. Every constraint in the migrations is explicitly named
 *  so callers can branch on the name instead of scraping a driver message that changes between versions. */
public final class ConstraintViolations {

    private ConstraintViolations() {
    }

    /** The violated constraint, lower-cased, or {@code null} when the driver did not report one. */
    public static String nameOf(DataIntegrityViolationException ex) {
        String name = ex.getCause() instanceof org.hibernate.exception.ConstraintViolationException cve ? cve.getConstraintName() : null;
        return name == null ? null : name.toLowerCase(Locale.ROOT);
    }

    /** Whether {@code ex} violated the named constraint. Never compare on the message: it is driver-specific. */
    public static boolean isViolationOf(DataIntegrityViolationException ex, String constraintName) {
        return constraintName.equals(nameOf(ex));
    }
}
