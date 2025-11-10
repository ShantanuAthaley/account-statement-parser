package dev.shantanu.bankstatement.excel;

public interface ExcelTransactionStatementFormat {
  boolean hasAccountInformation();
  boolean hasTransactionPeriodInformation();
  boolean hasTransactions();
}
