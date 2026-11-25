package com.chronon.recon.ingest.normalizer;

import org.junit.jupiter.api.Test;

import com.chronon.recon.domain.Money;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyParserTest {

    @Test
    void plainAmount() {
        assertThat(MoneyParser.parse("1234.50")).isNotNull();
    }

    @Test
    void withCommas() {
        assertThat(MoneyParser.parse("12,345.60")).isNotNull();
    }

    @Test
    void accountingNegative() {
        assertThat(MoneyParser.parse("(1,200.00)")).isNotNull();
    }

    @Test
    void rupeePrefixAndSuffix() {
        assertThat(MoneyParser.parse("₹ 2,500.00")).isNotNull();
        assertThat(MoneyParser.parse("500.00 Cr")).isNotNull();
        assertThat(MoneyParser.parse("300.00 Dr")).isNotNull();
    }

    @Test
    void blankIsEmpty() {
        assertThat(MoneyParser.parse(null)).isEmpty();
        assertThat(MoneyParser.parse("")).isEmpty();
        assertThat(MoneyParser.parse("   ")).isEmpty();
    }

    @Test
    void garbageIsEmpty() {
        assertThat(MoneyParser.parse("abc")).isEmpty();
    }
}