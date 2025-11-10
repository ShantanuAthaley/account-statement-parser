package dev.shantanu.bankstatement.common;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record Transactions(AccountInformation accountInformation, TransactionPeriod transactionPeriod,
                           List<Transaction> transactionList) {
}
record TransactionPeriod(LocalDate from, LocalDate to) {}

record Transaction(long serialNumber, LocalDate valueDate, LocalDate transactionDate, String checkNumber,
                   String transactionRemarks, BigDecimal withdrawalAmount, BigDecimal depositAmount,
                   BigDecimal balance) {

}
