package com.orvigas.merchant;

import com.orvigas.shared.id.MerchantId;
import java.time.Instant;
import java.util.Objects;

/**
 * Event emitted when a merchant's settlement account is changed. The new
 * account enters {@link SettlementAccountVerificationStatus#PENDING} and must
 * be re-verified before payouts resume.
 *
 * @param merchantId the aggregate identifier
 * @param account    the new settlement account (with PENDING verification status)
 * @param occurredAt when the event was raised
 * @author orvigas@gmail.com
 */
public record MerchantSettlementAccountChanged(
        MerchantId merchantId,
        SettlementAccount account,
        Instant occurredAt) {

    public MerchantSettlementAccountChanged {
        Objects.requireNonNull(merchantId, "merchantId must not be null");
        Objects.requireNonNull(account, "account must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
