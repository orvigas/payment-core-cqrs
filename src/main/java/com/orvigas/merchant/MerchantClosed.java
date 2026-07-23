package com.orvigas.merchant;

import com.orvigas.shared.id.MerchantId;
import java.time.Instant;
import java.util.Objects;

/**
 * Event emitted when a merchant account is closed. This is a terminal event —
 * no further commands are accepted. Historical data is retained for the
 * regulatory period.
 *
 * @param merchantId the aggregate identifier
 * @param occurredAt when the event was raised
 * @author orvigas@gmail.com
 */
public record MerchantClosed(MerchantId merchantId, Instant occurredAt) {

    public MerchantClosed {
        Objects.requireNonNull(merchantId, "merchantId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
