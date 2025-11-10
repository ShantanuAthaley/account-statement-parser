package dev.shantanu.bankstatement.common;

public record AccountInformation(String accountNumber, Currency currency) {
  public AccountInformation(String accountNumber) {
    this(accountNumber, Currency.INR);
  }
}



