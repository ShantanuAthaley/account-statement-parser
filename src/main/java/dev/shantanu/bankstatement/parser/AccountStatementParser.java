package dev.shantanu.bankstatement.parser;

import dev.shantanu.bankstatement.common.AccountStatement;
import java.io.IOException;

public interface AccountStatementParser {
  AccountStatement getTransactionInformation() throws IOException;
}
