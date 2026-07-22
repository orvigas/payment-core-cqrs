package com.orvigas.shared.id;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author orvigas@gmail.com
 */
class MerchantIdTest {

    @Test
    void newIdGeneratesDistinctNonNullValues() {
        MerchantId first = MerchantId.newId();
        MerchantId second = MerchantId.newId();

        assertThat(first.value()).isNotNull();
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void ofWrapsAnExistingUuid() {
        UUID raw = UUID.randomUUID();

        assertThat(MerchantId.of(raw).value()).isEqualTo(raw);
    }

    @Test
    void fromStringParsesCanonicalRepresentation() {
        UUID raw = UUID.randomUUID();

        assertThat(MerchantId.fromString(raw.toString())).isEqualTo(MerchantId.of(raw));
    }

    @Test
    void fromStringRejectsMalformedInput() {
        assertThatThrownBy(() -> MerchantId.fromString("not-a-uuid"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullValue() {
        assertThatThrownBy(() -> new MerchantId(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void equalIdsAreInterchangeable() {
        UUID raw = UUID.randomUUID();

        assertThat(new MerchantId(raw)).isEqualTo(new MerchantId(raw));
        assertThat(new MerchantId(raw)).hasSameHashCodeAs(new MerchantId(raw));
    }
}
