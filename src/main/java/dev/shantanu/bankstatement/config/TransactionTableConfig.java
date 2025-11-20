package dev.shantanu.bankstatement.config;

import java.util.List;

public record TransactionTableConfig(int headerRowOffset, List<ColumnField> columnFields) {
  public List<String> columnNames() {
    return this.columnFields.stream().map(ColumnField::displayName).toList();
  }
}
