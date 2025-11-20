package dev.shantanu.bankstatement.config;

import java.util.List;

public record FieldConfiguration(String name, String label, String pattern, List<String> patternMappedFields) {
}
