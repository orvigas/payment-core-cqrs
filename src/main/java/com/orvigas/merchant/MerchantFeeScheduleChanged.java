package com.orvigas.merchant;

import com.orvigas.shared.id.MerchantId;
import java.time.Instant;
import java.util.Objects;

/**
 * Event emitted when a merchant's fee schedule is updated. The change takes
 * effect at {@code effectiveFrom}, which is always in the future — fee changes
 * are never retroactive.
 *
 * @param merchantId    the aggregate identifier
 * @param schedule      the new fee schedule
 * @param effectiveFrom when the new schedule takes effect
 * @param occurredAt    when the event was raised
 * @author orvigas@gmail.com
 */
public record MerchantFeeScheduleChanged(
        MerchantId merchantId,
        FeeSchedule schedule,
        Instant effectiveFrom,
        Instant occurredAt) {

    public MerchantFeeScheduleChanged {
        Objects.requireNonNull(merchantId, "merchantId must not be null");
        Objects.requireNonNull(schedule, "schedule must not be null");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
