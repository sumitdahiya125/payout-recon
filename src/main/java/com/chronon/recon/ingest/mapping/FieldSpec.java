package com.chronon.recon.ingest.mapping;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * One logical field in a mapping profile.
 *
 * @param aliases  header spellings seen in the wild, e.g. ["Txn Date", "Transaction Date", "Txn Dt"]
 * @param required whether the profile cannot be used without resolving this field
 * @param type     how to interpret the cell value
 */
public record FieldSpec(
        List<String> aliases,
        Boolean required,
        FieldType type
) {

    @JsonCreator
    public FieldSpec(
            @JsonProperty("aliases") List<String> aliases,
            @JsonProperty("required") Boolean required,
            @JsonProperty("type") FieldType type
    ) {
        this.aliases = aliases == null ? List.of() : List.copyOf(aliases);
        this.required = required != null && required;
        this.type = type == null ? FieldType.STRING : type;
    }

    public static FieldSpec of(String... aliases) {
        return new FieldSpec(List.of(aliases), false, FieldType.STRING);
    }

    public static FieldSpec required(FieldType type, String... aliases) {
        return new FieldSpec(List.of(aliases), true, type);
    }

    public boolean isRequired() {
        return Boolean.TRUE.equals(required);
    }
}
