package com.chronon.recon.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionHierarchyTest {

    @Test
    void ingestionExceptionCarriesDefaultHintAndCause() {
        Throwable cause = new RuntimeException("Underlying read error");
        IngestionException ex = new IngestionException("Corrupt workbook", cause);

        assertThat(ex).isInstanceOf(ReconciliationException.class);
        assertThat(ex.getMessage()).isEqualTo("Corrupt workbook");
        assertThat(ex.getHint()).contains("valid Excel workbook");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void columnMappingExceptionCarriesHint() {
        ColumnMappingException ex = new ColumnMappingException("Missing column 'Amount'");

        assertThat(ex).isInstanceOf(ReconciliationException.class);
        assertThat(ex.getMessage()).isEqualTo("Missing column 'Amount'");
        assertThat(ex.getHint()).contains("profiles");
    }

    @Test
    void validationExceptionRetainsMessageAndHint() {
        ValidationException ex = new ValidationException("No file selected", "Please choose a file.");

        assertThat(ex).isInstanceOf(ReconciliationException.class);
        assertThat(ex.getMessage()).isEqualTo("No file selected");
        assertThat(ex.getHint()).isEqualTo("Please choose a file.");
    }

    @Test
    void runNotFoundExceptionFormatsMessageAndHint() {
        RunNotFoundException ex = new RunNotFoundException("run-1234");

        assertThat(ex).isInstanceOf(ReconciliationException.class);
        assertThat(ex.getMessage()).isEqualTo("Run run-1234 is not available.");
        assertThat(ex.getHint()).contains("in memory");
    }
}
