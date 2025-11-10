package dev.shantanu.bankstatement.parser;

import dev.shantanu.bankstatement.config.StatementType;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ExcelSearchStatementParserTest {
  static BankStatementParserFactory factory;

  @BeforeAll
  public static void getExcelStatementParser() {
    String fileName = "Shantanu-ICICI-01-Nov-2024-To-31-Oct-2025.xls";
    URL resource = URLClassLoader.getSystemClassLoader().getResource("dev.shantanu.bankstatement/parser/" + fileName);
    //File statementFile = new File(URI.create("dev.shantanu.bankstatement/parser/Shantanu-ICICI-01-Nov-2024-To-31-Oct-2025.xls"));
    assert resource != null;
    File statementFile = new File(resource.getFile());
    factory = new BankStatementParserFactory(StatementType.ICICI_BANK_SEARCH_STATEMENT, statementFile );
  }

  @Test
  public void testExcelFileParser(){
    AccountStatementParser parser = factory.getParser();
    parser.parse();
  }

}