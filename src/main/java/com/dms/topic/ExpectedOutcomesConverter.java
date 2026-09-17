package com.dms.topic;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * "RESEARCH_PAPER,PATENT" in the column, an EnumSet in the entity. Written in
 * enum order so the same set always produces the same string, which keeps the
 * provenance digest stable across saves.
 */
@Converter
public class ExpectedOutcomesConverter implements AttributeConverter<Set<ExpectedOutcome>, String> {

    @Override
    public String convertToDatabaseColumn(Set<ExpectedOutcome> outcomes) {
        if (outcomes == null || outcomes.isEmpty()) {
            return null;
        }
        EnumSet<ExpectedOutcome> ordered = EnumSet.noneOf(ExpectedOutcome.class);
        ordered.addAll(outcomes);
        return ordered.stream()
                .map(Enum::name)
                .collect(Collectors.joining(","));
    }

    @Override
    public Set<ExpectedOutcome> convertToEntityAttribute(String column) {
        if (column == null || column.isBlank()) {
            return EnumSet.noneOf(ExpectedOutcome.class);
        }
        return Arrays.stream(column.split(","))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .map(ExpectedOutcome::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(ExpectedOutcome.class)));
    }
}
