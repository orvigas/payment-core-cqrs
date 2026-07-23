package com.orvigas.merchant;

import com.orvigas.shared.id.MerchantId;
import java.util.Objects;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Command to complete Know-Your-Business verification for a merchant.
 *
 * @param merchantId the aggregate identifier
 * @param kybResult  the verification outcome — {@link KybStatus#VERIFIED} or
 *                   {@link KybStatus#REJECTED}
 * @author orvigas@gmail.com
 */
public record CompleteMerchantKybCommand(
        @TargetAggregateIdentifier MerchantId merchantId,
        KybStatus kybResult) {

    public CompleteMerchantKybCommand {
        Objects.requireNonNull(merchantId, "merchantId must not be null");
        Objects.requireNonNull(kybResult, "kybResult must not be null");
        if (kybResult != KybStatus.VERIFIED && kybResult != KybStatus.REJECTED) {
            throw new IllegalArgumentException("kybResult must be VERIFIED or REJECTED");
        }
    }
}
