package com.orvigas.merchant;

import com.orvigas.shared.id.MerchantId;
import java.time.Instant;
import java.util.Objects;

/**
 * Event emitted when Know-Your-Business verification completes for a merchant.
 *
 * @param merchantId the aggregate identifier
 * @param newStatus  the KYB outcome — {@link KybStatus#VERIFIED} or
 *                   {@link KybStatus#REJECTED}
 * @param occurredAt when the event was raised
 * @author orvigas@gmail.com
 */
public record MerchantKybCompleted(
        MerchantId merchantId,
        KybStatus newStatus,
        Instant occurredAt) {

    public MerchantKybCompleted {
        Objects.requireNonNull(merchantId, "merchantId must not be null");
        Objects.requireNonNull(newStatus, "newStatus must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
