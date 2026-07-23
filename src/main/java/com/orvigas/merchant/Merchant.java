package com.orvigas.merchant;

import com.orvigas.shared.id.MerchantId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;

/**
 * Merchant aggregate root. Models the full merchant lifecycle from onboarding
 * through KYB verification, activation, suspension, reinstatement, settlement
 * account and fee schedule changes, to eventual closure.
 *
 * <p>Fields are mutable because Axon event sourcing rebuilds aggregate state
 * by replaying events through event handlers that mutate aggregate fields. This
 * is standard for event-sourced aggregates and is distinct from the aggregate's
 * public API, which enforces constraints via command handlers.
 *
 * @author orvigas@gmail.com
 */
@Aggregate
public class Merchant {

    @AggregateIdentifier
    private MerchantId merchantId;

    private String legalName;
    private String tradingName;
    private String country;
    private String mcc;
    private List<String> supportedCurrencies;
    private SettlementAccount settlementAccount;
    private FeeSchedule feeSchedule;
    private SettlementSchedule settlementSchedule;
    private ReserveConfig reserveConfig;
    private MerchantStatus status;
    private KybStatus kybStatus;
    private SuspensionReason suspensionReason;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant activatedAt;
    private Instant closedAt;

    /**
     * Protected no-arg constructor for Axon event sourcing. Not for application
     * use.
     */
    protected Merchant() {
    }

    /**
     * Constructor that handles the register command.
     *
     * @param command the register command
     */
    @CommandHandler
    public Merchant(RegisterMerchantCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        AggregateLifecycle.apply(new MerchantRegistered(
                command.merchantId(),
                command.legalName(),
                command.tradingName(),
                command.country(),
                command.mcc(),
                List.copyOf(command.supportedCurrencies()),
                command.settlementAccount(),
                command.feeSchedule(),
                command.settlementSchedule(),
                command.reserveConfig(),
                Instant.now()));
    }

    /**
     * Handles the complete KYB command.
     *
     * @param command the command
     */
    @CommandHandler
    public void handle(CompleteMerchantKybCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (status == MerchantStatus.CLOSED) {
            throw new IllegalStateException("merchant is closed");
        }
        boolean canCompleteKyb = (status == MerchantStatus.ONBOARDING && kybStatus == KybStatus.PENDING)
                || kybStatus == KybStatus.REVIEW_REQUIRED;
        if (!canCompleteKyb) {
            throw new IllegalStateException(
                    "KYB can only be completed in ONBOARDING with PENDING status or with REVIEW_REQUIRED status,"
                            + " current status: " + status + ", kybStatus: " + kybStatus);
        }
        AggregateLifecycle.apply(
                new MerchantKybCompleted(merchantId, command.kybResult(), Instant.now()));
    }

    /**
     * Handles the activate command.
     *
     * @param command the command
     */
    @CommandHandler
    public void handle(ActivateMerchantCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (status == MerchantStatus.CLOSED) {
            throw new IllegalStateException("merchant is closed");
        }
        if (kybStatus != KybStatus.VERIFIED) {
            throw new IllegalStateException(
                    "merchant KYB must be VERIFIED to activate, current: " + kybStatus);
        }
        if (settlementAccount.verificationStatus() != SettlementAccountVerificationStatus.VERIFIED) {
            throw new IllegalStateException(
                    "settlement account must be VERIFIED to activate, current: "
                            + settlementAccount.verificationStatus());
        }
        AggregateLifecycle.apply(new MerchantActivated(merchantId, Instant.now()));
    }

    /**
     * Handles the suspend command.
     *
     * @param command the command
     */
    @CommandHandler
    public void handle(SuspendMerchantCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (status == MerchantStatus.CLOSED) {
            throw new IllegalStateException("merchant is closed");
        }
        if (status != MerchantStatus.ACTIVE) {
            throw new IllegalStateException(
                    "only ACTIVE merchants can be suspended, current status: " + status);
        }
        AggregateLifecycle.apply(
                new MerchantSuspended(merchantId, command.reason(), Instant.now()));
    }

    /**
     * Handles the reinstate command.
     *
     * @param command the command
     */
    @CommandHandler
    public void handle(ReinstateMerchantCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (status == MerchantStatus.CLOSED) {
            throw new IllegalStateException("merchant is closed");
        }
        if (status != MerchantStatus.SUSPENDED) {
            throw new IllegalStateException(
                    "only SUSPENDED merchants can be reinstated, current status: " + status);
        }
        AggregateLifecycle.apply(new MerchantReinstated(merchantId, Instant.now()));
    }

    /**
     * Handles the update settlement account command.
     *
     * @param command the command
     */
    @CommandHandler
    public void handle(UpdateSettlementAccountCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (status == MerchantStatus.CLOSED) {
            throw new IllegalStateException("merchant is closed");
        }
        SettlementAccount pendingAccount = new SettlementAccount(
                command.newAccount().accountHolder(),
                command.newAccount().iban(),
                command.newAccount().currency(),
                SettlementAccountVerificationStatus.PENDING);
        AggregateLifecycle.apply(
                new MerchantSettlementAccountChanged(merchantId, pendingAccount, Instant.now()));
    }

    /**
     * Handles the update fee schedule command.
     *
     * @param command the command
     */
    @CommandHandler
    public void handle(UpdateFeeScheduleCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (status == MerchantStatus.CLOSED) {
            throw new IllegalStateException("merchant is closed");
        }
        if (!command.effectiveFrom().isAfter(Instant.now())) {
            throw new IllegalArgumentException(
                    "effectiveFrom must be in the future: " + command.effectiveFrom());
        }
        AggregateLifecycle.apply(new MerchantFeeScheduleChanged(
                merchantId, command.schedule(), command.effectiveFrom(), Instant.now()));
    }

    /**
     * Handles the close merchant command.
     *
     * @param command the command
     */
    @CommandHandler
    public void handle(CloseMerchantCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (status == MerchantStatus.CLOSED) {
            throw new IllegalStateException("merchant is already closed");
        }
        if (command.hasOpenSettlements()) {
            throw new IllegalStateException("cannot close merchant with open settlements");
        }
        AggregateLifecycle.apply(new MerchantClosed(merchantId, Instant.now()));
    }

    @EventSourcingHandler
    public void on(MerchantRegistered event) {
        merchantId = event.merchantId();
        legalName = event.legalName();
        tradingName = event.tradingName();
        country = event.country();
        mcc = event.mcc();
        supportedCurrencies = new ArrayList<>(event.supportedCurrencies());
        settlementAccount = event.settlementAccount();
        feeSchedule = event.feeSchedule();
        settlementSchedule = event.settlementSchedule();
        reserveConfig = event.reserveConfig();
        createdAt = event.occurredAt();
        updatedAt = event.occurredAt();
        status = MerchantStatus.ONBOARDING;
        kybStatus = KybStatus.PENDING;
    }

    @EventSourcingHandler
    public void on(MerchantKybCompleted event) {
        kybStatus = event.newStatus();
        updatedAt = event.occurredAt();
    }

    @EventSourcingHandler
    public void on(MerchantActivated event) {
        status = MerchantStatus.ACTIVE;
        activatedAt = event.occurredAt();
        updatedAt = event.occurredAt();
    }

    @EventSourcingHandler
    public void on(MerchantSuspended event) {
        status = MerchantStatus.SUSPENDED;
        suspensionReason = event.reason();
        updatedAt = event.occurredAt();
    }

    @EventSourcingHandler
    public void on(MerchantReinstated event) {
        status = MerchantStatus.ACTIVE;
        suspensionReason = null;
        updatedAt = event.occurredAt();
    }

    @EventSourcingHandler
    public void on(MerchantSettlementAccountChanged event) {
        settlementAccount = event.account();
        updatedAt = event.occurredAt();
    }

    @EventSourcingHandler
    public void on(MerchantFeeScheduleChanged event) {
        feeSchedule = event.schedule();
        updatedAt = event.occurredAt();
    }

    @EventSourcingHandler
    public void on(MerchantClosed event) {
        status = MerchantStatus.CLOSED;
        closedAt = event.occurredAt();
        updatedAt = event.occurredAt();
    }

    // Getters for testing and query purposes

    public MerchantId getMerchantId() {
        return merchantId;
    }

    public String getLegalName() {
        return legalName;
    }

    public String getTradingName() {
        return tradingName;
    }

    public String getCountry() {
        return country;
    }

    public String getMcc() {
        return mcc;
    }

    public List<String> getSupportedCurrencies() {
        return supportedCurrencies;
    }

    public SettlementAccount getSettlementAccount() {
        return settlementAccount;
    }

    public FeeSchedule getFeeSchedule() {
        return feeSchedule;
    }

    public SettlementSchedule getSettlementSchedule() {
        return settlementSchedule;
    }

    public ReserveConfig getReserveConfig() {
        return reserveConfig;
    }

    public MerchantStatus getStatus() {
        return status;
    }

    public KybStatus getKybStatus() {
        return kybStatus;
    }

    public SuspensionReason getSuspensionReason() {
        return suspensionReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getActivatedAt() {
        return activatedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }
}
