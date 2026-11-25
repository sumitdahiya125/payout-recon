package com.chronon.recon.ingest.normalizer;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DateParserTest {

    @Test
    void dayFirstOrder() {
        assertThat(DateParser.parse("04/03/2025", DateOrder.DAY_FIRST))
                .contains(LocalDate.of(2025, 3, 4));
    }

    @Test
    void monthFirstOrder() {
        assertThat(DateParser.parse("04/03/2025", DateOrder.MONTH_FIRST))
                .contains(LocalDate.of(2025, 4, 3));
    }

    @Test
    void isoFormat() {
        assertThat(DateParser.parse("2025-03-04", DateOrder.DAY_FIRST))
                .contains(LocalDate.of(2025, 3, 4));
    }

    @Test
    void textualMonth() {
        assertThat(DateParser.parse("4-Mar-2025", DateOrder.DAY_FIRST))
                .contains(LocalDate.of(2025, 3, 4));
    }

    @Test
    void twoDigitYear() {
        assertThat(DateParser.parse("04/03/25", DateOrder.DAY_FIRST))
                .contains(LocalDate.of(2025, 3, 4));
    }

    @Test
    void excelSerial() {
        assertThat(DateParser.fromExcelSerial(java.math.BigDecimal.valueOf(45720)))
                .contains(LocalDate.of(2025, 3, 4));
    }

    @Test
    void detectsDayFirstEvidence() {
        assertThat(DateParser.detectOrder(java.util.List.of("04/03/2025", "15/03/2025")))
                .isEqualTo(DateOrder.DAY_FIRST);
    }

    @Test
    void detectsMonthFirstEvidence() {
        assertThat(DateParser.detectOrder(java.util.List.of("03/15/2025", "03/04/2025")))
                .isEqualTo(DateOrder.MONTH_FIRST);
    }
}
