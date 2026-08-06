package com.docuai.ai.service;

import com.docuai.core.model.SectionConstraints;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SectionConstraintValidatorTest {

    private final SectionConstraintValidator validator = new SectionConstraintValidator();

    @Test
    void valid_whenNoConstraintsAndNotEmpty() {
        var result = validator.validate("contenu quelconque", null, true);

        assertThat(result.valid()).isTrue();
        assertThat(result.violations()).isEmpty();
    }

    @Test
    void invalid_whenRequiredAndBlank() {
        var result = validator.validate("   ", null, true);

        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("vide"));
    }

    @Test
    void valid_whenNotRequiredAndBlank() {
        var result = validator.validate("", null, false);

        assertThat(result.valid()).isTrue();
    }

    @Test
    void invalid_whenBelowMinLength() {
        SectionConstraints constraints = SectionConstraints.builder().minLength(50).build();

        var result = validator.validate("trop court", constraints, true);

        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("minimale"));
    }

    @Test
    void invalid_whenAboveMaxLength() {
        SectionConstraints constraints = SectionConstraints.builder().maxLength(5).build();

        var result = validator.validate("bien plus long que cinq caractères", constraints, true);

        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("maximale"));
    }

    @Test
    void invalid_whenPatternNotMatched() {
        SectionConstraints constraints = SectionConstraints.builder().pattern("^\\d{4}-\\d{2}-\\d{2}$").build();

        var result = validator.validate("pas une date", constraints, true);

        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("format attendu"));
    }

    @Test
    void valid_whenPatternMatched() {
        SectionConstraints constraints = SectionConstraints.builder().pattern("^\\d{4}-\\d{2}-\\d{2}$").build();

        var result = validator.validate("2026-08-05", constraints, true);

        assertThat(result.valid()).isTrue();
    }

    @Test
    void valid_whenPatternInvalid_degradesGracefully() {
        SectionConstraints constraints = SectionConstraints.builder().pattern("[invalide(").build();

        var result = validator.validate("peu importe le contenu", constraints, true);

        assertThat(result.valid()).isTrue();
    }

    @Test
    void collectsMultipleViolationsAtOnce() {
        SectionConstraints constraints = SectionConstraints.builder().minLength(100).pattern("^\\d+$").build();

        var result = validator.validate("court", constraints, true);

        assertThat(result.violations()).hasSize(2);
    }
}
