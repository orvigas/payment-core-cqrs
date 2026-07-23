package com.orvigas.merchant;

import com.orvigas.shared.id.MerchantId;
import java.util.Objects;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Command to reinstate a suspended merchant after the cause of suspension has
 * been resolved.
 *
 * @param merchantId the aggregate identifier
 * @author orvigas@gmail.com
 */
public record ReinstateMerchantCommand(
        @TargetAggregateIdentifier MerchantId merchantId) {

    public ReinstateMerchantCommand {
        Objects.requireNonNull(merchantId, "merchantId must not be null");
    }
}
