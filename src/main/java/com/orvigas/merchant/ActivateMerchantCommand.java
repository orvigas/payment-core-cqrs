package com.orvigas.merchant;

import com.orvigas.shared.id.MerchantId;
import java.util.Objects;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Command to activate a merchant after onboarding, KYB verification, and
 * settlement-account verification are all complete.
 *
 * @param merchantId the aggregate identifier
 * @author orvigas@gmail.com
 */
public record ActivateMerchantCommand(
        @TargetAggregateIdentifier MerchantId merchantId) {

    public ActivateMerchantCommand {
        Objects.requireNonNull(merchantId, "merchantId must not be null");
    }
}
