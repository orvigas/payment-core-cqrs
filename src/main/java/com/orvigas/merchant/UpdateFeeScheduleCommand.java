package com.orvigas.merchant;

import com.orvigas.shared.id.MerchantId;
import java.time.Instant;
import java.util.Objects;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Command to update a merchant's fee schedule. The new schedule takes effect at
 * {@code effectiveFrom}, which must be in the future — fee changes are never
 * retroactive.
 *
 * @param merchantId    the aggregate identifier
 * @param schedule      the new fee schedule
 * @param effectiveFrom when the new schedule takes effect (must be in the future)
 * @author orvigas@gmail.com
 */
public record UpdateFeeScheduleCommand(
        @TargetAggregateIdentifier MerchantId merchantId,
        FeeSchedule schedule,
        Instant effectiveFrom) {

    public UpdateFeeScheduleCommand {
        Objects.requireNonNull(merchantId, "merchantId must not be null");
        Objects.requireNonNull(schedule, "schedule must not be null");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom must not be null");
    }
}
