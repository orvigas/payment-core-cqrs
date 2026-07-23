package com.orvigas.merchant;

import com.orvigas.shared.id.MerchantId;
import java.util.List;
import java.util.Objects;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Command to register a new merchant and begin the onboarding flow.
 *
 * <p>TODO(#???): encrypt legalName, tradingName, and country at rest when the
 * platform has a key-management service. These are classified per
 * SECURITY_POLICY.md §5.
 *
 * @param merchantId          the aggregate identifier
 * @param legalName           registered company name (PII — classified)
 * @param tradingName         name shown on customer statements (PII — classified)
 * @param country             ISO 3166 country code of incorporation (PII — classified)
 * @param mcc                 Merchant Category Code (4-digit ISO 18245)
 * @param supportedCurrencies ISO 4217 currency codes the merchant may charge in
 * @param settlementAccount   bank account for payouts
 * @param feeSchedule         pricing configuration
 * @param settlementSchedule  payout cadence
 * @param reserveConfig       rolling reserve configuration
 * @author orvigas@gmail.com
 */
public record RegisterMerchantCommand(
        @TargetAggregateIdentifier MerchantId merchantId,
        String legalName,
        String tradingName,
        String country,
        String mcc,
        List<String> supportedCurrencies,
        SettlementAccount settlementAccount,
        FeeSchedule feeSchedule,
        SettlementSchedule settlementSchedule,
        ReserveConfig reserveConfig) {

    public RegisterMerchantCommand {
        Objects.requireNonNull(merchantId, "merchantId must not be null");
        Objects.requireNonNull(legalName, "legalName must not be null");
        Objects.requireNonNull(tradingName, "tradingName must not be null");
        Objects.requireNonNull(country, "country must not be null");
        Objects.requireNonNull(mcc, "mcc must not be null");
        Objects.requireNonNull(supportedCurrencies, "supportedCurrencies must not be null");
        Objects.requireNonNull(settlementAccount, "settlementAccount must not be null");
        Objects.requireNonNull(feeSchedule, "feeSchedule must not be null");
        Objects.requireNonNull(settlementSchedule, "settlementSchedule must not be null");
        Objects.requireNonNull(reserveConfig, "reserveConfig must not be null");
        if (supportedCurrencies.isEmpty()) {
            throw new IllegalArgumentException("supportedCurrencies must not be empty");
        }
    }

    @Override
    public String toString() {
        return "RegisterMerchantCommand["
                + "merchantId=" + merchantId
                + ", mcc=" + mcc
                + ", supportedCurrencies=" + supportedCurrencies
                + ", settlementAccount=" + settlementAccount
                + ", feeSchedule=" + feeSchedule
                + ", settlementSchedule=" + settlementSchedule
                + ", reserveConfig=" + reserveConfig
                + "]";
    }
}
