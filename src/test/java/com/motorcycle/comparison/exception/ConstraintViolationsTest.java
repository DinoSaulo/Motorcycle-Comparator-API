package com.motorcycle.comparison.exception;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every constraint in the migrations is named, but the parsing here still has to survive whatever a driver or a
 * test double hands it: a missing cause, a cause of the wrong type, or a constraint the exception itself never named.
 */
@DisplayName("ConstraintViolations")
class ConstraintViolationsTest {

    @Test
    @DisplayName("lower-cases the constraint name the driver reported")
    void lowerCasesTheName() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("duplicate key",
                new ConstraintViolationException("violates unique constraint", new SQLException("23505"), "UK_Motorcycles_Slug"));

        assertThat(ConstraintViolations.nameOf(ex)).isEqualTo("uk_motorcycles_slug");
    }

    @Test
    @DisplayName("returns null when the cause is not a Hibernate constraint violation")
    void returnsNullForAnUnrelatedCause() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("could not execute statement", new SQLException("connection reset"));

        assertThat(ConstraintViolations.nameOf(ex)).isNull();
    }

    @Test
    @DisplayName("returns null when there is no cause at all")
    void returnsNullWithNoCause() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("could not execute statement");

        assertThat(ConstraintViolations.nameOf(ex)).isNull();
    }

    @Test
    @DisplayName("returns null when the driver itself never named the constraint")
    void returnsNullWhenTheDriverOmittedTheName() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("could not execute statement",
                new ConstraintViolationException("violates constraint", new SQLException("23514"), null));

        assertThat(ConstraintViolations.nameOf(ex)).isNull();
    }

    @Test
    @DisplayName("matches case-insensitively against the named constraint")
    void isViolationOfMatches() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("duplicate key",
                new ConstraintViolationException("violates unique constraint", new SQLException("23505"), "UK_MOTORCYCLES_SLUG"));

        assertThat(ConstraintViolations.isViolationOf(ex, "uk_motorcycles_slug")).isTrue();
    }

    @Test
    @DisplayName("does not match a different constraint")
    void isViolationOfRejectsAMismatch() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("duplicate key",
                new ConstraintViolationException("violates unique constraint", new SQLException("23505"), "uk_motorcycles_slug"));

        assertThat(ConstraintViolations.isViolationOf(ex, "ck_motorcycles_model_year")).isFalse();
    }

    @Test
    @DisplayName("does not match anything when the constraint has no name")
    void isViolationOfRejectsAnUnnamedConstraint() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("could not execute statement", new SQLException("connection reset"));

        assertThat(ConstraintViolations.isViolationOf(ex, "uk_motorcycles_slug")).isFalse();
    }
}
