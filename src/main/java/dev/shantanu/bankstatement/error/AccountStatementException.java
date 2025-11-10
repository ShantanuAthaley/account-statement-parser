package dev.shantanu.bankstatement.error;

public class AccountStatementException extends RuntimeException {
  public AccountStatementException(ErrorCode code, String message, Throwable cause) {
    super(code.getErrorMessage() + " ." + message, cause);
  }
}

