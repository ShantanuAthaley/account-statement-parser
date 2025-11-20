package dev.shantanu.bankstatement.common;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.apache.commons.lang3.StringUtils;

public record TransactionRecord(int serialNumber, LocalDate valueDate, LocalDate transactionDate, String checkNumber,
                                String transactionRemarks, BigDecimal withdrawalAmount, BigDecimal depositAmount,
                                BigDecimal balance, String error) {
  static final DateTimeFormatter formatter;

  public TransactionRecord(int serialNumber, String valueDate, String transactionDate, String checkNumber, String transactionRemarks, String withdrawalAmount, String depositAmount, BigDecimal balance, String error) {
    LocalDate valueLocalDate = LocalDate.parse(valueDate, formatter);
    LocalDate transactionLocalDate = LocalDate.parse(transactionDate, formatter);
    BigDecimal withdrawal = null;
    BigDecimal deposit = null;
    if (StringUtils.isBlank(withdrawalAmount)) {
      withdrawal = BigDecimal.valueOf(0.0);
    }
    if (StringUtils.isBlank(depositAmount)) {
      deposit = BigDecimal.valueOf(0.0);
    }
    this(serialNumber, valueLocalDate, transactionLocalDate, checkNumber, transactionRemarks, withdrawal, deposit, balance, error);
  }

  static {
    formatter = DateTimeFormatter.ISO_LOCAL_DATE;
  }
}
