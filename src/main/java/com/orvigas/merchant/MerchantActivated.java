package com.orvigas.merchant;

import com.orvigas.shared.id.MerchantId;
import java.time.Instant;
import java.util.Objects;

/**
 * Event emitted when a merchant is activated after completing KYB verification
 * and settlement-account verification.
 *
 * @param merchantId the aggregate identifier
 * @param occurredAt when the event was raised
 * @author orvigas@gmail.com
 */
public record MerchantActivated(MerchantId merchantId, Instant occurredAt) {

    public MerchantActivated {
        Objects.requireNonNull(merchantId, "merchantId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
