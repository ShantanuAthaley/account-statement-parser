package dev.shantanu.bankstatement.parser;

import dev.shantanu.bankstatement.common.AccountInformation;

public interface AccountStatementParser {
  AccountInformation parse();
}
