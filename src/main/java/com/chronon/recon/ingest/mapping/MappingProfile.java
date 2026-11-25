package com.chronon.recon.ingest.mapping;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A declarative description of one spreadsheet format. Adding support for a new bank means adding
 * a YAML file like this one — no matcher or parser code changes.
 */
public record MappingProfile(
        String id,
        String description,
        MappingTarget target,
        Integer headerScanRows,
        String sheetNameContains,
        Map<String, FieldSpec> fields,
        List<String> skipRowPatterns,
        List<String> nonPayoutKeywords,
        Boolean defaultProfile
) {

    public static final List<String> DEFAULT_NON_PAYOUT_KEYWORDS = List.of(
            "salary", "interest", "charges", "gst", "tds", "atm", "cash withdrawal",
            "refund", "cashback", "dividend", "rent", "vendor", "sweep", "fixed deposit",
            "recurring deposit", "emi", "insurance", "tax", "commission", "opening balance",
            "closing balance"
    );

    @JsonCreator
    public MappingProfile(
            @JsonProperty("id") String id,
            @JsonProperty("description") String description,
            @JsonProperty("target") MappingTarget target,
            @JsonProperty("headerScanRows") Integer headerScanRows,
            @JsonProperty("sheetNameContains") String sheetNameContains,
            @JsonProperty("fields") Map<String, FieldSpec> fields,
            @JsonProperty("skipRowPatterns") List<String> skipRowPatterns,
            @JsonProperty("nonPayoutKeywords") List<String> nonPayoutKeywords,
            @JsonProperty("default") Boolean defaultProfile
    ) {
        this.id = id;
        this.description = description == null ? "" : description;
        this.target = target;
        this.headerScanRows = headerScanRows == null ? 25 : Math.max(1, headerScanRows);
        this.sheetNameContains = sheetNameContains;
        this.fields = fields == null ? Map.of() : new LinkedHashMap<>(fields);
        this.skipRowPatterns = skipRowPatterns == null ? List.of() : List.copyOf(skipRowPatterns);
        this.nonPayoutKeywords = nonPayoutKeywords == null || nonPayoutKeywords.isEmpty()
                ? DEFAULT_NON_PAYOUT_KEYWORDS
                : List.copyOf(nonPayoutKeywords);
        this.defaultProfile = defaultProfile != null && defaultProfile;
        validate();
    }

    private void validate() {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Mapping profile is missing an 'id'");
        }
        if (target == null) {
            throw new IllegalArgumentException("Mapping profile '" + id + "' is missing a 'target'");
        }
        if (fields.isEmpty()) {
            throw new IllegalArgumentException("Mapping profile '" + id + "' defines no fields");
        }
        if (target == MappingTarget.PAYOUT_REGISTER) {
            requireKeys(Fields.PAYOUT_ID, Fields.AMOUNT, Fields.PAYOUT_DATE);
        } else {
            if (!fields.containsKey(Fields.TXN_DATE) && !fields.containsKey(Fields.VALUE_DATE)) {
                throw new IllegalArgumentException("Bank profile '" + id
                        + "' must map at least one of txnDate or valueDate");
            }
            boolean separateColumns = fields.containsKey(Fields.DEBIT) || fields.containsKey(Fields.CREDIT);
            boolean combinedColumn = fields.containsKey(Fields.AMOUNT) && fields.containsKey(Fields.DR_CR);
            if (!separateColumns && !combinedColumn) {
                throw new IllegalArgumentException("Bank profile '" + id
                        + "' must map debit/credit columns, or an amount column plus a drCr column");
            }
        }
    }

    private void requireKeys(String... keys) {
        for (String key : keys) {
            if (!fields.containsKey(key)) {
                throw new IllegalArgumentException("Payout profile '" + id + "' must map '" + key + "'");
            }
        }
    }

    public Optional<FieldSpec> field(String key) {
        return Optional.ofNullable(fields.get(key));
    }

    public boolean has(String key) {
        return fields.containsKey(key);
    }

    public FieldSpec spec(String key) {
        return fields.get(key);
    }

    public Set<String> fieldKeys() {
        return fields.keySet();
    }

    public Set<String> requiredKeys() {
        return fields.entrySet().stream()
                .filter(entry -> entry.getValue().isRequired())
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    public int scanRows() {
        return headerScanRows;
    }

    public boolean isDefault() {
        return Boolean.TRUE.equals(defaultProfile);
    }
}
