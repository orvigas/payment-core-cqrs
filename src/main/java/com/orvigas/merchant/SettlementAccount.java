package com.orvigas.merchant;

import java.util.Objects;

/**
 * Bank account used for merchant payouts. Changing the account is the highest-risk
 * operation on the aggregate — changes always reset verification to {@link
 * SettlementAccountVerificationStatus#PENDING}.
 *
 * <p>TODO(#???): encrypt accountHolder and iban at rest when the platform has a
 * key-management service. These fields are classified per SECURITY_POLICY.md §5.
 *
 * @param accountHolder name of the account holder (PII — classified, encrypt at rest)
 * @param iban          or local bank account identifier (classified data)
 * @param currency      ISO 4217 currency code for the payout account
 * @param verificationStatus current verification state
 * @author orvigas@gmail.com
 */
public record SettlementAccount(
        String accountHolder,
        String iban,
        String currency,
        SettlementAccountVerificationStatus verificationStatus) {

    public SettlementAccount {
        Objects.requireNonNull(accountHolder, "accountHolder must not be null");
        Objects.requireNonNull(iban, "iban must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(verificationStatus, "verificationStatus must not be null");
    }

    @Override
    public String toString() {
        return "SettlementAccount["
                + "currency=" + currency
                + ", verificationStatus=" + verificationStatus
                + "]";
    }
}
