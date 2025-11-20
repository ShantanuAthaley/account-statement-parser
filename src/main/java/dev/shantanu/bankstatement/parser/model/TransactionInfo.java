package dev.shantanu.bankstatement.parser.model;

import dev.shantanu.bankstatement.common.Currency;
import java.time.LocalDate;

public record TransactionInfo(String accountNumber, String fullName, Currency currency, LocalDate transactionFrom,
                              LocalDate transactionTo) {
}
