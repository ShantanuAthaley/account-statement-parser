package dev.shantanu.bankstatement.parser;

import dev.shantanu.bankstatement.common.AccountStatement;
import dev.shantanu.bankstatement.config.StatementType;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ExcelSearchStatementParserTest {
  static BankStatementParserFactory factory;

  @BeforeAll
  static void getExcelStatementParser() {
    String fileName = "Test-Account-Statement.xlsx";
    URL resource = ClassLoader.getSystemClassLoader().getResource("dev/shantanu/bankstatement/" + fileName);
    assert resource != null;
    File statementFile = new File(resource.getFile());
    factory = new BankStatementParserFactory(StatementType.ICICI_BANK_SEARCH_STATEMENT, statementFile);
  }

  @Test
  void testExcelFileParser() throws IOException {
    AccountStatementParser parser = factory.getParser();
    AccountStatement transactionInformation = parser.getTransactionInformation();
    Assertions.assertNotNull(transactionInformation);
    Assertions.assertTrue(transactionInformation.transactionRecords().size() > 1);
    Assertions.assertNotNull(transactionInformation.transactionInfo());
  }

}