package com.orvigas.merchant;

import com.orvigas.shared.id.MerchantId;
import java.time.Instant;
import java.util.Objects;

/**
 * Event emitted when a suspended merchant is reinstated.
 *
 * @param merchantId the aggregate identifier
 * @param occurredAt when the event was raised
 * @author orvigas@gmail.com
 */
public record MerchantReinstated(MerchantId merchantId, Instant occurredAt) {

    public MerchantReinstated {
        Objects.requireNonNull(merchantId, "merchantId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
