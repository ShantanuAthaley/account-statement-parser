package dev.shantanu.bankstatement.excel;

import dev.shantanu.bankstatement.error.AccountStatementException;
import dev.shantanu.bankstatement.error.ErrorCode;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExcelBankAccountStatement implements ExcelTransactionStatementFormat {
  private static final Logger LOGGER = LoggerFactory.getLogger(ExcelBankAccountStatement.class);

  private boolean hasAccountInformation;
  private boolean hasTransactionPeriodInformation;
  private boolean hasTransactions;

  ExcelBankAccountStatement(Path pathToExcelFile) {
    File file = pathToExcelFile.toFile();
    verifyFile(file);
  }

  private void verifyFile(File file) {
    if (!file.isFile()) {
      throw new AccountStatementException(ErrorCode.INVALID_FILE_FORMAT, null, null);
    }
    try (InputStream inputStream = Files.newInputStream(file.toPath(), StandardOpenOption.READ)) {
      Workbook workbook = getWorkbook(file, inputStream);
      int numberOfSheets = workbook.getNumberOfSheets();

      if (numberOfSheets < 1) {
        final String errorMessage = String.format("No sheets found for file = %s", file.getName());
        LOGGER.error(errorMessage);
        throw new AccountStatementException(ErrorCode.EMPTY_FILE, errorMessage, new IllegalStateException());
      }
      Sheet mainSheet = workbook.getSheetAt(workbook.getActiveSheetIndex());

      for (var row : mainSheet) {
        if (row.getRowNum() == 0) continue; //
        for (var column : row) {
          if (column.getCellType() == CellType.STRING) {

          }
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static Workbook getWorkbook(File file, InputStream inputStream) throws IOException {
    Workbook workbook = WorkbookFactory.create(inputStream);
    if (!(workbook instanceof HSSFWorkbook) && !(workbook instanceof XSSFWorkbook)) {
      final String errorMessage = String.format("File format not supported for file = %s", file.getName());
      throw new AccountStatementException(ErrorCode.NOT_SUPPORTED_FILE_FORMAT, errorMessage, new IllegalStateException());
    }
    return workbook;
  }

  @Override
  public boolean hasAccountInformation() {
    return false;
  }

  @Override
  public boolean hasTransactionPeriodInformation() {
    return false;
  }

  @Override
  public boolean hasTransactions() {
    return false;
  }
}
