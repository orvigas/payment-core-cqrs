package com.orvigas.merchant;

import com.orvigas.shared.id.MerchantId;
import java.util.Objects;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Command to suspend an active merchant. Suspension blocks new payments but
 * allows refunds and settlements to proceed.
 *
 * @param merchantId the aggregate identifier
 * @param reason     structured reason for the suspension
 * @author orvigas@gmail.com
 */
public record SuspendMerchantCommand(
        @TargetAggregateIdentifier MerchantId merchantId,
        SuspensionReason reason) {

    public SuspendMerchantCommand {
        Objects.requireNonNull(merchantId, "merchantId must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
    }
}
