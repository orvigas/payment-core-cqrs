package com.orvigas.merchant;

import com.orvigas.shared.id.MerchantId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Event emitted when a merchant registers for the platform. The merchant enters
 * the {@link MerchantStatus#ONBOARDING} state with
 * {@link KybStatus#PENDING}.
 *
 * <p>TODO(#???): encrypt legalName, tradingName, and country at rest when the
 * platform has a key-management service. These are classified per
 * SECURITY_POLICY.md §5.
 *
 * @param merchantId          the aggregate identifier
 * @param legalName           registered company name (PII — classified)
 * @param tradingName         name shown on customer statements (PII — classified)
 * @param country             ISO 3166 country code (PII — classified)
 * @param mcc                 Merchant Category Code
 * @param supportedCurrencies ISO 4217 currency codes the merchant may charge in
 * @param settlementAccount   bank account for payouts
 * @param feeSchedule         pricing configuration
 * @param settlementSchedule  payout cadence
 * @param reserveConfig       rolling reserve configuration
 * @param occurredAt          when the event was raised
 * @author orvigas@gmail.com
 */
public record MerchantRegistered(
        MerchantId merchantId,
        String legalName,
        String tradingName,
        String country,
        String mcc,
        List<String> supportedCurrencies,
        SettlementAccount settlementAccount,
        FeeSchedule feeSchedule,
        SettlementSchedule settlementSchedule,
        ReserveConfig reserveConfig,
        Instant occurredAt) {

    public MerchantRegistered {
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
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        if (supportedCurrencies.isEmpty()) {
            throw new IllegalArgumentException("supportedCurrencies must not be empty");
        }
    }

    @Override
    public String toString() {
        return "MerchantRegistered["
                + "merchantId=" + merchantId
                + ", mcc=" + mcc
                + ", supportedCurrencies=" + supportedCurrencies
                + ", settlementAccount=" + settlementAccount
                + ", feeSchedule=" + feeSchedule
                + ", settlementSchedule=" + settlementSchedule
                + ", reserveConfig=" + reserveConfig
                + ", occurredAt=" + occurredAt
                + "]";
    }
}
