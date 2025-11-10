package dev.shantanu.bankstatement.parser;

import static dev.shantanu.bankstatement.error.ErrorCode.NOT_SUPPORTED_STATEMENT_TYPE;

import dev.shantanu.bankstatement.config.IciciSearchStatementConfig;
import dev.shantanu.bankstatement.config.StatementConfiguration;
import dev.shantanu.bankstatement.config.StatementType;
import dev.shantanu.bankstatement.error.AccountStatementException;
import java.io.File;

public record BankStatementParserFactory(StatementType statementType, File statementFile) {

  public AccountStatementParser getParser() {
    switch (statementType) {
      case ICICI_BANK_SEARCH_STATEMENT -> {
        StatementConfiguration statementConfiguration = new IciciSearchStatementConfig();
        return new ExcelSearchStatementParser(statementFile, statementConfiguration);
      }
      default -> throw new AccountStatementException(NOT_SUPPORTED_STATEMENT_TYPE, "Not supported statement-type", null);
    }
  }

}
