package com.orvigas.shared.money;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author orvigas@gmail.com
 */
class MoneyTest {

    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency EUR = Currency.getInstance("EUR");

    @Nested
    class Construction {

        @Test
        void createsAmountFromMinorUnitsAndCurrencyCode() {
            Money amount = Money.of(1_000L, "USD");

            assertThat(amount.minorUnits()).isEqualTo(1_000L);
            assertThat(amount.currency()).isEqualTo(USD);
        }

        @Test
        void allowsZeroAmount() {
            Money amount = Money.of(0L, "USD");

            assertThat(amount.isZero()).isTrue();
            assertThat(amount.isPositive()).isFalse();
        }

        @Test
        void rejectsNegativeAmount() {
            assertThatThrownBy(() -> Money.of(-1L, "USD"))
                    .isInstanceOf(InvalidMoneyAmountException.class)
                    .hasMessageContaining("negative");
        }

        @Test
        void rejectsNullCurrency() {
            assertThatThrownBy(() -> new Money(100L, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void rejectsUnknownCurrencyCode() {
            assertThatThrownBy(() -> Money.of(100L, "NOT_A_CURRENCY"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void zeroFactoryProducesZeroInGivenCurrency() {
            Money zero = Money.zero(EUR);

            assertThat(zero.minorUnits()).isZero();
            assertThat(zero.currency()).isEqualTo(EUR);
        }
    }

    @Nested
    class Addition {

        @Test
        void addsAmountsInTheSameCurrency() {
            Money sum = Money.of(500L, "USD").add(Money.of(250L, "USD"));

            assertThat(sum).isEqualTo(Money.of(750L, "USD"));
        }

        @Test
        void addingZeroIsIdentity() {
            Money amount = Money.of(500L, "USD");

            assertThat(amount.add(Money.zero(USD))).isEqualTo(amount);
        }

        @Test
        void rejectsCurrencyMismatch() {
            Money usd = Money.of(500L, "USD");
            Money eur = Money.of(500L, "EUR");

            assertThatThrownBy(() -> usd.add(eur))
                    .isInstanceOf(CurrencyMismatchException.class)
                    .hasMessageContaining("USD")
                    .hasMessageContaining("EUR");
        }

        @Test
        void rejectsOverflow() {
            Money nearMax = Money.of(Long.MAX_VALUE, "USD");

            assertThatThrownBy(() -> nearMax.add(Money.of(1L, "USD")))
                    .isInstanceOf(InvalidMoneyAmountException.class)
                    .hasMessageContaining("overflow");
        }
    }

    @Nested
    class Subtraction {

        @Test
        void subtractsAmountsInTheSameCurrency() {
            Money difference = Money.of(500L, "USD").subtract(Money.of(200L, "USD"));

            assertThat(difference).isEqualTo(Money.of(300L, "USD"));
        }

        @Test
        void subtractingToExactlyZeroIsAllowed() {
            Money result = Money.of(500L, "USD").subtract(Money.of(500L, "USD"));

            assertThat(result.isZero()).isTrue();
        }

        @Test
        void rejectsResultBelowZero() {
            Money small = Money.of(100L, "USD");
            Money large = Money.of(200L, "USD");

            assertThatThrownBy(() -> small.subtract(large))
                    .isInstanceOf(InvalidMoneyAmountException.class)
                    .hasMessageContaining("negative");
        }

        @Test
        void rejectsCurrencyMismatch() {
            Money usd = Money.of(500L, "USD");
            Money eur = Money.of(100L, "EUR");

            assertThatThrownBy(() -> usd.subtract(eur))
                    .isInstanceOf(CurrencyMismatchException.class);
        }
    }

    @Nested
    class Comparison {

        @ParameterizedTest
        @CsvSource({
                "100, 200, -1",
                "200, 100, 1",
                "150, 150, 0"
        })
        void comparesAmountsInTheSameCurrency(long left, long right, int expectedSign) {
            int result = Money.of(left, "USD").compareTo(Money.of(right, "USD"));

            assertThat(Integer.signum(result)).isEqualTo(expectedSign);
        }

        @Test
        void rejectsComparisonAcrossCurrencies() {
            Money usd = Money.of(100L, "USD");
            Money eur = Money.of(100L, "EUR");

            assertThatThrownBy(() -> usd.compareTo(eur))
                    .isInstanceOf(CurrencyMismatchException.class);
        }
    }

    @Nested
    class Predicates {

        @Test
        void isPositiveForNonZeroAmount() {
            assertThat(Money.of(1L, "USD").isPositive()).isTrue();
        }

        @Test
        void isNotPositiveForZeroAmount() {
            assertThat(Money.of(0L, "USD").isPositive()).isFalse();
        }
    }
}
