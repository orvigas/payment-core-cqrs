package com.orvigas.merchant;

import com.orvigas.shared.id.MerchantId;
import java.time.Instant;
import java.util.Objects;

/**
 * Event emitted when an active merchant is suspended.
 *
 * @param merchantId the aggregate identifier
 * @param reason     structured reason for the suspension
 * @param occurredAt when the event was raised
 * @author orvigas@gmail.com
 */
public record MerchantSuspended(
        MerchantId merchantId,
        SuspensionReason reason,
        Instant occurredAt) {

    public MerchantSuspended {
        Objects.requireNonNull(merchantId, "merchantId must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
