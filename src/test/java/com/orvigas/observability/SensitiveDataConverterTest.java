package com.orvigas.observability;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SensitiveDataConverter}.
 *
 * @author orvigas@gmail.com
 */
class SensitiveDataConverterTest {

    @Test
    void masksJsonPasswordValue() {
        String result = SensitiveDataConverter.maskSensitiveData(
                "{\"user\":\"admin\",\"password\":\"super-secret\"}");
        assertThat(result).contains("\"password\":\"[REDACTED]\"");
        assertThat(result).doesNotContain("super-secret");
    }

    @Test
    void masksJsonTokenValue() {
        String result = SensitiveDataConverter.maskSensitiveData(
                "{\"token\":\"eyJhbGciOiJIUzI1NiJ9.test\"}");
        assertThat(result).contains("\"token\":\"[REDACTED]\"");
    }

    @Test
    void masksQueryParamPassword() {
        String result = SensitiveDataConverter.maskSensitiveData(
                "password=my-secret-pwd&action=login");
        assertThat(result).contains("password=[REDACTED]");
        assertThat(result).doesNotContain("my-secret-pwd");
    }

    @Test
    void masksCreditCardPan() {
        String result = SensitiveDataConverter.maskSensitiveData(
                "{\"pan\":\"4111111111111111\"}");
        assertThat(result).contains("\"pan\":\"[REDACTED]\"");
        assertThat(result).doesNotContain("4111111111111111");
    }

    @Test
    void masksCvv() {
        String result = SensitiveDataConverter.maskSensitiveData(
                "{\"cvv\":\"123\"}");
        assertThat(result).contains("\"cvv\":\"[REDACTED]\"");
    }

    @Test
    void masksSsn() {
        String result = SensitiveDataConverter.maskSensitiveData(
                "{\"ssn\":\"123-45-6789\"}");
        assertThat(result).contains("\"ssn\":\"[REDACTED]\"");
    }

    @Test
    void masksTaxId() {
        String result = SensitiveDataConverter.maskSensitiveData(
                "taxId=12-3456789");
        assertThat(result).contains("taxId=[REDACTED]");
    }

    @Test
    void leavesNormalMessagesUnchanged() {
        String message = "Payment 12345 completed successfully";
        assertThat(SensitiveDataConverter.maskSensitiveData(message))
                .isEqualTo(message);
    }

    @Test
    void handlesNullInput() {
        assertThat(SensitiveDataConverter.maskSensitiveData(null)).isNull();
    }
}
