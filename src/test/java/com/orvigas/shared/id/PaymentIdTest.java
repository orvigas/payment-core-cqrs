package com.orvigas.shared.id;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author orvigas@gmail.com
 */
class PaymentIdTest {

    @Test
    void newIdGeneratesDistinctNonNullValues() {
        PaymentId first = PaymentId.newId();
        PaymentId second = PaymentId.newId();

        assertThat(first.value()).isNotNull();
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void ofWrapsAnExistingUuid() {
        UUID raw = UUID.randomUUID();

        assertThat(PaymentId.of(raw).value()).isEqualTo(raw);
    }

    @Test
    void fromStringParsesCanonicalRepresentation() {
        UUID raw = UUID.randomUUID();

        assertThat(PaymentId.fromString(raw.toString())).isEqualTo(PaymentId.of(raw));
    }

    @Test
    void fromStringRejectsMalformedInput() {
        assertThatThrownBy(() -> PaymentId.fromString("not-a-uuid"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullValue() {
        assertThatThrownBy(() -> new PaymentId(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void equalIdsAreInterchangeable() {
        UUID raw = UUID.randomUUID();

        assertThat(new PaymentId(raw)).isEqualTo(new PaymentId(raw));
        assertThat(new PaymentId(raw)).hasSameHashCodeAs(new PaymentId(raw));
    }
}
