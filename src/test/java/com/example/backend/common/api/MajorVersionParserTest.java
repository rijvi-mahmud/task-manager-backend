package com.example.backend.common.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.accept.InvalidApiVersionException;

class MajorVersionParserTest {

    private final MajorVersionParser parser = new MajorVersionParser();

    @Test
    void parsesCanonicalForm() {
        assertThat(parser.parseVersion("v1")).isEqualTo(1);
        assertThat(parser.parseVersion("v12")).isEqualTo(12);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "V1", "x1", "v0", "v01", "v1.0", "v", "", "v1000"})
    void rejectsAnythingElse(String version) {
        assertThatThrownBy(() -> parser.parseVersion(version)).isInstanceOf(InvalidApiVersionException.class);
    }
}
