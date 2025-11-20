package dev.shantanu.bankstatement.parser;

import dev.shantanu.bankstatement.config.IciciSearchStatementConfig;
import dev.shantanu.bankstatement.config.StatementConfiguration;
import dev.shantanu.bankstatement.config.StatementType;
import dev.shantanu.bankstatement.error.AccountStatementException;
import dev.shantanu.bankstatement.error.ErrorCode;
import java.io.File;
import java.util.Objects;

public record BankStatementParserFactory(StatementType statementType, File statementFile) {
   public AccountStatementParser getParser() {
      if (Objects.requireNonNull(this.statementType) == StatementType.ICICI_BANK_SEARCH_STATEMENT) {
         StatementConfiguration statementConfiguration = new IciciSearchStatementConfig();
         return new ExcelSearchStatementParser(this.statementFile, statementConfiguration);
      } else {
         throw new AccountStatementException(ErrorCode.NOT_SUPPORTED_STATEMENT_TYPE, "Not supported statement-type", (Throwable) null);
      }
   }
}
