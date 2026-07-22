package com.orvigas.shared.id;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author orvigas@gmail.com
 */
class CaptureIdTest {

    @Test
    void newIdGeneratesDistinctNonNullValues() {
        CaptureId first = CaptureId.newId();
        CaptureId second = CaptureId.newId();

        assertThat(first.value()).isNotNull();
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void ofWrapsAnExistingUuid() {
        UUID raw = UUID.randomUUID();

        assertThat(CaptureId.of(raw).value()).isEqualTo(raw);
    }

    @Test
    void fromStringParsesCanonicalRepresentation() {
        UUID raw = UUID.randomUUID();

        assertThat(CaptureId.fromString(raw.toString())).isEqualTo(CaptureId.of(raw));
    }

    @Test
    void fromStringRejectsMalformedInput() {
        assertThatThrownBy(() -> CaptureId.fromString("not-a-uuid"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullValue() {
        assertThatThrownBy(() -> new CaptureId(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void equalIdsAreInterchangeable() {
        UUID raw = UUID.randomUUID();

        assertThat(new CaptureId(raw)).isEqualTo(new CaptureId(raw));
        assertThat(new CaptureId(raw)).hasSameHashCodeAs(new CaptureId(raw));
    }
}
