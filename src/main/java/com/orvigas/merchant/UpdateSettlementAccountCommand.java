package com.orvigas.merchant;

import com.orvigas.shared.id.MerchantId;
import java.util.Objects;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Command to change a merchant's settlement account. The new account enters
 * {@link SettlementAccountVerificationStatus#PENDING} and payouts pause until
 * verified.
 *
 * @param merchantId the aggregate identifier
 * @param newAccount the new bank account details
 * @author orvigas@gmail.com
 */
public record UpdateSettlementAccountCommand(
        @TargetAggregateIdentifier MerchantId merchantId,
        SettlementAccount newAccount) {

    public UpdateSettlementAccountCommand {
        Objects.requireNonNull(merchantId, "merchantId must not be null");
        Objects.requireNonNull(newAccount, "newAccount must not be null");
    }
}
