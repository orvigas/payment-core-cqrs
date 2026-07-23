package com.orvigas.merchant;

import com.orvigas.shared.id.MerchantId;
import java.util.Objects;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Command to close a merchant account. The merchant must not have any open
 * settlements. This is a terminal operation — historical data is retained for
 * the regulatory period.
 *
 * <p>TODO(T-011): replace the {@code hasOpenSettlements} flag with a real
 * settlement-projection check.
 *
 * @param merchantId         the aggregate identifier
 * @param hasOpenSettlements pass {@code false} when the projection confirms
 *                           no open settlements exist
 * @author orvigas@gmail.com
 */
public record CloseMerchantCommand(
        @TargetAggregateIdentifier MerchantId merchantId,
        boolean hasOpenSettlements) {

    public CloseMerchantCommand {
        Objects.requireNonNull(merchantId, "merchantId must not be null");
    }
}
