package com.orvigas.shared.id;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author orvigas@gmail.com
 */
class SettlementIdTest {

    @Test
    void newIdGeneratesDistinctNonNullValues() {
        SettlementId first = SettlementId.newId();
        SettlementId second = SettlementId.newId();

        assertThat(first.value()).isNotNull();
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void ofWrapsAnExistingUuid() {
        UUID raw = UUID.randomUUID();

        assertThat(SettlementId.of(raw).value()).isEqualTo(raw);
    }

    @Test
    void fromStringParsesCanonicalRepresentation() {
        UUID raw = UUID.randomUUID();

        assertThat(SettlementId.fromString(raw.toString())).isEqualTo(SettlementId.of(raw));
    }

    @Test
    void fromStringRejectsMalformedInput() {
        assertThatThrownBy(() -> SettlementId.fromString("not-a-uuid"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullValue() {
        assertThatThrownBy(() -> new SettlementId(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void equalIdsAreInterchangeable() {
        UUID raw = UUID.randomUUID();

        assertThat(new SettlementId(raw)).isEqualTo(new SettlementId(raw));
        assertThat(new SettlementId(raw)).hasSameHashCodeAs(new SettlementId(raw));
    }
}
