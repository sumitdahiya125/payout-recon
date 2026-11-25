package com.chronon.recon.ingest.mapping;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ColumnResolverTest {

    private final ColumnResolver resolver = new ColumnResolver();

    @Test
    void mapsExactAliasesRegardlessOfColumnOrder() {
        ColumnMapping mapping = resolver.resolve(
                List.of("Description", "Withdrawal", "Deposit", "Transaction Date"),
                bankProfile(), 0, 1);

        assertThat(mapping.usable()).isTrue();
        assertThat(mapping.index(Fields.TXN_DATE)).isEqualTo(3);
        assertThat(mapping.index(Fields.NARRATION)).isEqualTo(0);
        assertThat(mapping.index(Fields.DEBIT)).isEqualTo(1);
        assertThat(mapping.index(Fields.CREDIT)).isEqualTo(2);
        assertThat(mapping.warnings()).isEmpty();
    }

    @Test
    void flagsFuzzyHeaderMappingsForHumanReview() {
        ColumnMapping mapping = resolver.resolve(
                List.of("Transacton Date", "Narraton", "Withdrwal", "Depost"),
                bankProfile(), 0, 1);

        assertThat(mapping.usable()).isTrue();
        assertThat(mapping.warnings()).hasSize(4);
        assertThat(mapping.score(Fields.TXN_DATE)).isLessThan(0.80);
        assertThat(mapping.score(Fields.NARRATION)).isLessThan(0.80);
    }

    @Test
    void reportsMissingRequiredFields() {
        ColumnMapping mapping = resolver.resolve(
                List.of("Narration", "Debit", "Credit"),
                bankProfile(), 0, 1);

        assertThat(mapping.usable()).isFalse();
        assertThat(mapping.missingRequired()).containsExactly(Fields.TXN_DATE);
    }

    @Test
    void bestHeaderSkipsPreambleRows() {
        ColumnResolver.HeaderCandidate candidate = resolver.bestHeader(
                List.of(
                        List.of("Statement of account", "", "", ""),
                        List.of("Generated on 01/03/2025", "", "", ""),
                        List.of("Transaction Date", "Narration", "Withdrawal", "Deposit")),
                bankProfile());

        assertThat(candidate.index()).isEqualTo(2);
        assertThat(candidate.mapping().headerRowNumber()).isEqualTo(3);
        assertThat(candidate.mapping().usable()).isTrue();
    }

    private MappingProfile bankProfile() {
        Map<String, FieldSpec> fields = new LinkedHashMap<>();
        fields.put(Fields.TXN_DATE, FieldSpec.required(FieldType.DATE, "Txn Date", "Transaction Date"));
        fields.put(Fields.NARRATION, FieldSpec.required(FieldType.STRING, "Narration", "Description"));
        fields.put(Fields.DEBIT, FieldSpec.of("Debit", "Withdrawal"));
        fields.put(Fields.CREDIT, FieldSpec.of("Credit", "Deposit"));
        return new MappingProfile("test-bank", "Test bank statement", MappingTarget.BANK_STATEMENT,
                10, null, fields, List.of(), List.of(), false);
    }
}
