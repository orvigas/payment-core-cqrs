package com.orvigas.merchant;

/**
 * Lifecycle status of a merchant aggregate. Only {@link #ACTIVE} merchants can
 * initiate payments. {@link #SUSPENDED} blocks new payments but refunds and
 * settlements may still proceed. {@link #CLOSED} is terminal.
 *
 * @author orvigas@gmail.com
 */
public enum MerchantStatus {
    ONBOARDING,
    ACTIVE,
    SUSPENDED,
    CLOSED
}
