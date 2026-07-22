package com.orvigas.shared.id;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author orvigas@gmail.com
 */
class RefundIdTest {

    @Test
    void newIdGeneratesDistinctNonNullValues() {
        RefundId first = RefundId.newId();
        RefundId second = RefundId.newId();

        assertThat(first.value()).isNotNull();
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void ofWrapsAnExistingUuid() {
        UUID raw = UUID.randomUUID();

        assertThat(RefundId.of(raw).value()).isEqualTo(raw);
    }

    @Test
    void fromStringParsesCanonicalRepresentation() {
        UUID raw = UUID.randomUUID();

        assertThat(RefundId.fromString(raw.toString())).isEqualTo(RefundId.of(raw));
    }

    @Test
    void fromStringRejectsMalformedInput() {
        assertThatThrownBy(() -> RefundId.fromString("not-a-uuid"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullValue() {
        assertThatThrownBy(() -> new RefundId(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void equalIdsAreInterchangeable() {
        UUID raw = UUID.randomUUID();

        assertThat(new RefundId(raw)).isEqualTo(new RefundId(raw));
        assertThat(new RefundId(raw)).hasSameHashCodeAs(new RefundId(raw));
    }
}
