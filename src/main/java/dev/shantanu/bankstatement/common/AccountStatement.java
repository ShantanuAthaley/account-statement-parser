package dev.shantanu.bankstatement.common;

import dev.shantanu.bankstatement.parser.model.TransactionInfo;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;

public record AccountStatement(TransactionInfo transactionInfo, Set<TransactionRecord> transactionRecords) {
  public AccountStatement(TransactionInfo transactionInfo, Set<TransactionRecord> transactionRecords) {
    this.transactionInfo = transactionInfo;
    this.transactionRecords = Set.copyOf((Collection) Objects.requireNonNull(transactionRecords, "Transactions list cannot be null"));
  }
}
