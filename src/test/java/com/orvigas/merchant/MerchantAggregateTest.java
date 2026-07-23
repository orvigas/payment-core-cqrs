package com.orvigas.merchant;

import static org.assertj.core.api.Assertions.assertThat;

import com.orvigas.shared.id.MerchantId;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the Merchant aggregate using Axon test fixtures.
 *
 * @author orvigas@gmail.com
 */
@DisplayName("Merchant aggregate")
class MerchantAggregateTest {

    private FixtureConfiguration<Merchant> fixture;

    private static final String DEFAULT_LEGAL_NAME = "Acme Corp Ltd";
    private static final String DEFAULT_TRADING_NAME = "Acme";
    private static final String DEFAULT_COUNTRY = "US";
    private static final String DEFAULT_MCC = "5734";
    private static final List<String> DEFAULT_CURRENCIES = List.of("USD", "EUR");
    private static final BigDecimal DEFAULT_RESERVE_PCT = BigDecimal.valueOf(5);
    private static final Duration DEFAULT_HOLD_DURATION = Duration.ofDays(90);
    private static final int DEFAULT_DELAY_DAYS = 2;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(Merchant.class);
    }

    // -- value object helpers --

    private static SettlementAccount verifiedSettlementAccount() {
        return new SettlementAccount("Acme Corp", "US123456789", "USD",
                SettlementAccountVerificationStatus.VERIFIED);
    }

    private static SettlementAccount pendingSettlementAccount() {
        return new SettlementAccount("Acme Corp", "US123456789", "USD",
                SettlementAccountVerificationStatus.PENDING);
    }

    private static SettlementAccount failedSettlementAccount() {
        return new SettlementAccount("Acme Corp", "US123456789", "USD",
                SettlementAccountVerificationStatus.FAILED);
    }

    private static FeeEntry defaultFeeEntry() {
        return new FeeEntry(BigDecimal.valueOf(2.9), 30, "USD");
    }

    private static FeeSchedule defaultFeeSchedule() {
        return new FeeSchedule(defaultFeeEntry(), Map.of());
    }

    private static SettlementSchedule defaultSettlementSchedule() {
        return new SettlementSchedule(SettlementFrequency.DAILY, DEFAULT_DELAY_DAYS);
    }

    private static ReserveConfig defaultReserveConfig() {
        return new ReserveConfig(DEFAULT_RESERVE_PCT, DEFAULT_HOLD_DURATION);
    }

    private static SuspensionReason defaultSuspensionReason() {
        return new SuspensionReason(SuspensionReasonCode.COMPLIANCE, "Periodic review triggered");
    }

    // -- event helpers --

    private static MerchantRegistered registeredEvent(MerchantId id, SettlementAccount account) {
        return new MerchantRegistered(
                id, DEFAULT_LEGAL_NAME, DEFAULT_TRADING_NAME, DEFAULT_COUNTRY,
                DEFAULT_MCC, DEFAULT_CURRENCIES, account, defaultFeeSchedule(),
                defaultSettlementSchedule(), defaultReserveConfig(), Instant.now());
    }

    private static MerchantKybCompleted kybCompletedEvent(
            MerchantId id, KybStatus status, Instant now) {
        return new MerchantKybCompleted(id, status, now);
    }

    private static MerchantActivated activatedEvent(MerchantId id, Instant now) {
        return new MerchantActivated(id, now);
    }

    private static MerchantSuspended suspendedEvent(MerchantId id, Instant now) {
        return new MerchantSuspended(id, defaultSuspensionReason(), now);
    }

    private static MerchantReinstated reinstatedEvent(MerchantId id, Instant now) {
        return new MerchantReinstated(id, now);
    }

    private static MerchantSettlementAccountChanged settlementAccountChangedEvent(
            MerchantId id, SettlementAccount account, Instant now) {
        return new MerchantSettlementAccountChanged(id, account, now);
    }

    private static MerchantFeeScheduleChanged feeScheduleChangedEvent(
            MerchantId id, FeeSchedule schedule, Instant effectiveFrom, Instant now) {
        return new MerchantFeeScheduleChanged(id, schedule, effectiveFrom, now);
    }

    private static MerchantClosed closedEvent(MerchantId id, Instant now) {
        return new MerchantClosed(id, now);
    }

    private static RegisterMerchantCommand registerCommand(MerchantId id, SettlementAccount account) {
        return new RegisterMerchantCommand(
                id, DEFAULT_LEGAL_NAME, DEFAULT_TRADING_NAME, DEFAULT_COUNTRY,
                DEFAULT_MCC, DEFAULT_CURRENCIES, account, defaultFeeSchedule(),
                defaultSettlementSchedule(), defaultReserveConfig());
    }

    // -- tests --

    @Test
    @DisplayName("registers a new merchant with ONBOARDING status")
    void testRegister() {
        var id = MerchantId.newId();
        var command = registerCommand(id, pendingSettlementAccount());

        fixture.givenNoPriorActivity()
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectState(m -> {
                    assertThat(m.getMerchantId()).isEqualTo(id);
                    assertThat(m.getStatus()).isEqualTo(MerchantStatus.ONBOARDING);
                    assertThat(m.getKybStatus()).isEqualTo(KybStatus.PENDING);
                    assertThat(m.getLegalName()).isEqualTo(DEFAULT_LEGAL_NAME);
                    assertThat(m.getTradingName()).isEqualTo(DEFAULT_TRADING_NAME);
                    assertThat(m.getCountry()).isEqualTo(DEFAULT_COUNTRY);
                    assertThat(m.getMcc()).isEqualTo(DEFAULT_MCC);
                    assertThat(m.getSupportedCurrencies()).containsExactly("USD", "EUR");
                    assertThat(m.getSettlementAccount()).isNotNull();
                    assertThat(m.getFeeSchedule()).isNotNull();
                    assertThat(m.getSettlementSchedule()).isNotNull();
                    assertThat(m.getReserveConfig()).isNotNull();
                    assertThat(m.getCreatedAt()).isNotNull();
                    assertThat(m.getUpdatedAt()).isNotNull();
                });
    }

    @Test
    @DisplayName("completes KYB with VERIFIED status")
    void testCompleteKybVerified() {
        var id = MerchantId.newId();
        var now = Instant.now();

        fixture.given(registeredEvent(id, pendingSettlementAccount()))
                .when(new CompleteMerchantKybCommand(id, KybStatus.VERIFIED))
                .expectSuccessfulHandlerExecution()
                .expectState(m -> assertThat(m.getKybStatus()).isEqualTo(KybStatus.VERIFIED));
    }

    @Test
    @DisplayName("completes KYB with REJECTED status")
    void testCompleteKybRejected() {
        var id = MerchantId.newId();
        var now = Instant.now();

        fixture.given(registeredEvent(id, pendingSettlementAccount()))
                .when(new CompleteMerchantKybCommand(id, KybStatus.REJECTED))
                .expectSuccessfulHandlerExecution()
                .expectState(m -> assertThat(m.getKybStatus()).isEqualTo(KybStatus.REJECTED));
    }

    @Test
    @DisplayName("rejects KYB completion when merchant is not in ONBOARDING status")
    void testCompleteKybOnlyInOnboarding() {
        var id = MerchantId.newId();
        var now = Instant.now();

        fixture.given(
                        registeredEvent(id, verifiedSettlementAccount()),
                        kybCompletedEvent(id, KybStatus.VERIFIED, now))
                .when(new CompleteMerchantKybCommand(id, KybStatus.VERIFIED))
                .expectException(IllegalStateException.class);
    }

    @Test
    @DisplayName("activates a merchant when KYB is VERIFIED and settlement account is VERIFIED")
    void testActivateWhenReady() {
        var id = MerchantId.newId();
        var now = Instant.now();

        fixture.given(
                        registeredEvent(id, verifiedSettlementAccount()),
                        kybCompletedEvent(id, KybStatus.VERIFIED, now))
                .when(new ActivateMerchantCommand(id))
                .expectSuccessfulHandlerExecution()
                .expectState(m -> {
                    assertThat(m.getStatus()).isEqualTo(MerchantStatus.ACTIVE);
                    assertThat(m.getActivatedAt()).isNotNull();
                });
    }

    @Test
    @DisplayName("rejects activation when KYB is not VERIFIED")
    void testActivateWithoutKybVerified() {
        var id = MerchantId.newId();
        var now = Instant.now();

        fixture.given(
                        registeredEvent(id, verifiedSettlementAccount()),
                        kybCompletedEvent(id, KybStatus.REJECTED, now))
                .when(new ActivateMerchantCommand(id))
                .expectException(IllegalStateException.class);
    }

    @Test
    @DisplayName("rejects activation when settlement account is not VERIFIED")
    void testActivateWithoutSettlementVerified() {
        var id = MerchantId.newId();
        var now = Instant.now();

        fixture.given(
                        registeredEvent(id, pendingSettlementAccount()),
                        kybCompletedEvent(id, KybStatus.VERIFIED, now))
                .when(new ActivateMerchantCommand(id))
                .expectException(IllegalStateException.class);
    }

    @Test
    @DisplayName("suspends an active merchant")
    void testSuspend() {
        var id = MerchantId.newId();
        var now = Instant.now();
        var reason = defaultSuspensionReason();

        fixture.given(
                        registeredEvent(id, verifiedSettlementAccount()),
                        kybCompletedEvent(id, KybStatus.VERIFIED, now),
                        activatedEvent(id, now))
                .when(new SuspendMerchantCommand(id, reason))
                .expectSuccessfulHandlerExecution()
                .expectState(m -> {
                    assertThat(m.getStatus()).isEqualTo(MerchantStatus.SUSPENDED);
                    assertThat(m.getSuspensionReason()).isEqualTo(reason);
                });
    }

    @Test
    @DisplayName("rejects suspension when merchant is not ACTIVE")
    void testSuspendOnlyWhenActive() {
        var id = MerchantId.newId();

        fixture.given(registeredEvent(id, pendingSettlementAccount()))
                .when(new SuspendMerchantCommand(id, defaultSuspensionReason()))
                .expectException(IllegalStateException.class);
    }

    @Test
    @DisplayName("reinstates a suspended merchant to ACTIVE")
    void testReinstate() {
        var id = MerchantId.newId();
        var now = Instant.now();

        fixture.given(
                        registeredEvent(id, verifiedSettlementAccount()),
                        kybCompletedEvent(id, KybStatus.VERIFIED, now),
                        activatedEvent(id, now),
                        suspendedEvent(id, now))
                .when(new ReinstateMerchantCommand(id))
                .expectSuccessfulHandlerExecution()
                .expectState(m -> {
                    assertThat(m.getStatus()).isEqualTo(MerchantStatus.ACTIVE);
                    assertThat(m.getSuspensionReason()).isNull();
                });
    }

    @Test
    @DisplayName("rejects reinstatement when merchant is not SUSPENDED")
    void testReinstateOnlyWhenSuspended() {
        var id = MerchantId.newId();
        var now = Instant.now();

        fixture.given(
                        registeredEvent(id, verifiedSettlementAccount()),
                        kybCompletedEvent(id, KybStatus.VERIFIED, now),
                        activatedEvent(id, now))
                .when(new ReinstateMerchantCommand(id))
                .expectException(IllegalStateException.class);
    }

    @Test
    @DisplayName("updates the settlement account and resets to PENDING")
    void testUpdateSettlementAccount() {
        var id = MerchantId.newId();
        var now = Instant.now();
        var newAccount = new SettlementAccount("New Co", "GB987654321", "GBP",
                SettlementAccountVerificationStatus.VERIFIED);

        fixture.given(
                        registeredEvent(id, verifiedSettlementAccount()),
                        kybCompletedEvent(id, KybStatus.VERIFIED, now),
                        activatedEvent(id, now))
                .when(new UpdateSettlementAccountCommand(id, newAccount))
                .expectSuccessfulHandlerExecution()
                .expectState(m -> {
                    assertThat(m.getSettlementAccount().accountHolder()).isEqualTo("New Co");
                    assertThat(m.getSettlementAccount().iban()).isEqualTo("GB987654321");
                    assertThat(m.getSettlementAccount().currency()).isEqualTo("GBP");
                    assertThat(m.getSettlementAccount().verificationStatus())
                            .isEqualTo(SettlementAccountVerificationStatus.PENDING);
                });
    }

    @Test
    @DisplayName("updates the fee schedule with a future effective date")
    void testUpdateFeeScheduleFuture() {
        var id = MerchantId.newId();
        var now = Instant.now();
        var effectiveFrom = now.plusSeconds(86400);
        var newSchedule = new FeeSchedule(
                new FeeEntry(BigDecimal.valueOf(1.5), 10, "USD"), Map.of());

        fixture.given(
                        registeredEvent(id, verifiedSettlementAccount()),
                        kybCompletedEvent(id, KybStatus.VERIFIED, now),
                        activatedEvent(id, now))
                .when(new UpdateFeeScheduleCommand(id, newSchedule, effectiveFrom))
                .expectSuccessfulHandlerExecution()
                .expectState(m -> assertThat(m.getFeeSchedule()).isEqualTo(newSchedule));
    }

    @Test
    @DisplayName("rejects fee schedule update with a past effective date")
    void testUpdateFeeSchedulePastDate() {
        var id = MerchantId.newId();
        var now = Instant.now();
        var effectiveFrom = now.minusSeconds(86400);
        var newSchedule = new FeeSchedule(
                new FeeEntry(BigDecimal.valueOf(1.5), 10, "USD"), Map.of());

        fixture.given(
                        registeredEvent(id, verifiedSettlementAccount()),
                        kybCompletedEvent(id, KybStatus.VERIFIED, now),
                        activatedEvent(id, now))
                .when(new UpdateFeeScheduleCommand(id, newSchedule, effectiveFrom))
                .expectException(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("closes a merchant with no open settlements")
    void testClose() {
        var id = MerchantId.newId();
        var now = Instant.now();

        fixture.given(
                        registeredEvent(id, verifiedSettlementAccount()),
                        kybCompletedEvent(id, KybStatus.VERIFIED, now),
                        activatedEvent(id, now))
                .when(new CloseMerchantCommand(id, false))
                .expectSuccessfulHandlerExecution()
                .expectState(m -> {
                    assertThat(m.getStatus()).isEqualTo(MerchantStatus.CLOSED);
                    assertThat(m.getClosedAt()).isNotNull();
                });
    }

    @Test
    @DisplayName("rejects close when merchant has open settlements")
    void testCloseWithOpenSettlements() {
        var id = MerchantId.newId();
        var now = Instant.now();

        fixture.given(
                        registeredEvent(id, verifiedSettlementAccount()),
                        kybCompletedEvent(id, KybStatus.VERIFIED, now),
                        activatedEvent(id, now))
                .when(new CloseMerchantCommand(id, true))
                .expectException(IllegalStateException.class);
    }

    @Test
    @DisplayName("rejects close when already CLOSED")
    void testCloseAlreadyClosed() {
        var id = MerchantId.newId();
        var now = Instant.now();

        fixture.given(
                        registeredEvent(id, verifiedSettlementAccount()),
                        kybCompletedEvent(id, KybStatus.VERIFIED, now),
                        activatedEvent(id, now),
                        closedEvent(id, now))
                .when(new CloseMerchantCommand(id, false))
                .expectException(IllegalStateException.class);
    }

    @Test
    @DisplayName("blocks all commands after closure")
    void testClosedMerchantBlocksCommands() {
        var id = MerchantId.newId();
        var now = Instant.now();

        var givenEvents = new Object[]{
                registeredEvent(id, verifiedSettlementAccount()),
                kybCompletedEvent(id, KybStatus.VERIFIED, now),
                activatedEvent(id, now),
                closedEvent(id, now)
        };

        fixture.given(givenEvents)
                .when(new ActivateMerchantCommand(id))
                .expectException(IllegalStateException.class);
    }

    @Test
    @DisplayName("rejects closing a merchant that was just registered with open settlements")
    void testCloseRegisteredWithOpenSettlements() {
        var id = MerchantId.newId();

        fixture.given(registeredEvent(id, pendingSettlementAccount()))
                .when(new CloseMerchantCommand(id, true))
                .expectException(IllegalStateException.class);
    }

    @Test
    @DisplayName("KYB can be completed when status is REVIEW_REQUIRED")
    void testCompleteKybFromReviewRequired() {
        var id = MerchantId.newId();
        var now = Instant.now();

        fixture.given(
                        registeredEvent(id, pendingSettlementAccount()),
                        kybCompletedEvent(id, KybStatus.REVIEW_REQUIRED, now))
                .when(new CompleteMerchantKybCommand(id, KybStatus.VERIFIED))
                .expectSuccessfulHandlerExecution()
                .expectState(m -> assertThat(m.getKybStatus()).isEqualTo(KybStatus.VERIFIED));
    }
}
