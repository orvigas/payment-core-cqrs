package com.orvigas.merchant;

/**
 * Verification status of a merchant's settlement account. Account changes always
 * reset to {@link #PENDING} until the payout provider confirms the new details.
 *
 * @author orvigas@gmail.com
 */
public enum SettlementAccountVerificationStatus {
    PENDING,
    VERIFIED,
    FAILED
}
