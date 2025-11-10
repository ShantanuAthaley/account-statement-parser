package dev.shantanu.bankstatement.parser;

import static java.util.Comparator.comparingInt;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import dev.shantanu.bankstatement.common.AccountInformation;
import dev.shantanu.bankstatement.common.FileType;
import dev.shantanu.bankstatement.config.StatementConfiguration;
import dev.shantanu.bankstatement.config.StatementType;
import dev.shantanu.bankstatement.error.AccountStatementException;
import dev.shantanu.bankstatement.error.ErrorCode;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.ToIntFunction;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellAddress;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/*
Parser for ICICI bank (advance) search statement
 */
public class ExcelSearchStatementParser implements AccountStatementParser {

  private final StatementConfiguration statementConfiguration;
  private final File statementFile;

  public ExcelSearchStatementParser(File statementFile, StatementConfiguration statementConfiguration) {
    this.statementFile = statementFile;
    this.statementConfiguration = statementConfiguration;
  }

  @Override
  public AccountInformation parse() {
    StatementType statementType = statementConfiguration.getStatementType();
    try (InputStream inputStream = new FileInputStream(this.statementFile)) {
      FileType fileType = statementType.getFileType();
      Workbook workbook = null;
      switch (fileType) {
        case FileType.XLS -> workbook = new HSSFWorkbook(inputStream);

        case FileType.XLSX -> workbook = new XSSFWorkbook(inputStream);
      }

      int numberOfSheets = workbook.getNumberOfSheets();
      if (numberOfSheets == 0) {
        throw new AccountStatementException(ErrorCode.EMPTY_FILE, "No worksheet found in the input file = " + statementFile, new IllegalStateException());
      }
      Sheet sheet = workbook.getSheetAt(0);
      parseSheet(sheet);
      workbook.close();

    } catch (IOException e) {
      throw new RuntimeException(e); // TODO: throw AccountStatementException
    }
    return null;
  }

  private void parseSheet(Sheet sheet) {
    boolean isEmpty = isEmptySheet(sheet);
    if (isEmpty) {
      throw new AccountStatementException(ErrorCode.EMPTY_FILE, "Empty sheet", new IllegalStateException());
    }

    ToIntFunction<JsonObject> getOrder = jo -> jo.getInt("order");
    Comparator<JsonObject> sectionComparatorByOrder = comparingInt(getOrder);
    List<JsonObject> sections = this.statementConfiguration.getSections().stream().sorted(sectionComparatorByOrder).toList();

//    int physicalNumberOfRows = sheet.getPhysicalNumberOfRows();
//    int firstRowNum = sheet.getFirstRowNum();
//    int lastRowNum = sheet.getLastRowNum();


    var writeObjectBuilder = Json.createBuilderFactory(Collections.emptyMap());

    for (var section : sections) {
      String title = section.getString("title");
      List<String> searchFor = section.getJsonArray("searchKeywords").stream().map(JsonValue::toString).toList();
      SearchRange range = getSearchRange(section.getJsonObject("relativeSearchRange"));
      List<FieldConfiguration> fields = getFieldListForSection(section.getJsonArray("fields"));
      TransactionTableConfig transactionTable = getTransactionTableConfig(section.getJsonObject("table"));


      if (Strings.CI.equals("header", section.getString("id"))) {
        String header = readHeaderSection(sheet, section);
        writeTo(writeObjectBuilder, section.getString("id"), section.getString("name"), header);
      }

      if (nonNull(fields) && !fields.isEmpty()) {
        fields.forEach(field -> readAndMapFieldValue(sheet, field, writeObjectBuilder));
        JsonObject build = writeObjectBuilder.createObjectBuilder().build();
      }
      if (nonNull(transactionTable)) {
      }
    }
    JsonObject writeJson = writeObjectBuilder.createObjectBuilder().build();

  }

  private void readAndMapFieldValue(Sheet sheet, FieldConfiguration fieldConfig, JsonBuilderFactory writeObjectBuilderFactory) {

    CellAddress activeCell = sheet.getActiveCell();
    String labelToSearch = fieldConfig.label();
    boolean valueAfterText = fieldConfig.valueAfterText();

    int startRow = activeCell.getRow();

    for (int i = startRow; i < startRow + 5; i++) {
      CellAddress cellAddressOfLabel = findFieldLabelFromRow(sheet, i, labelToSearch);
      if (isNull(cellAddressOfLabel)) {
        continue;
      }

      Row row = sheet.getRow(cellAddressOfLabel.getRow());
      Cell cell = row.getCell(cellAddressOfLabel.getColumn());
      String fieldLabel = cell.getStringCellValue();
      String fieldValue = getAdjacentValue(sheet, row, cell, cellAddressOfLabel.getColumn());
      System.out.println("fieldLabel = " + fieldLabel + " fieldValue = " + fieldValue);
      writeTo(writeObjectBuilderFactory, fieldLabel, fieldConfig.name(), fieldValue);
      sheet.setActiveCell(cellAddressOfLabel);
      return;
    }
  }

  private String getAdjacentValue(Sheet sheet, Row row, Cell cell, int column) {
    int adjacentColumn = mergedCellAddressOptional(sheet, cell)
      .map(CellRangeAddress::getLastColumn)
      .map(col -> col + 1)
      .orElse(column + 1);

    return sheet.getRow(row.getRowNum()).getCell(adjacentColumn).getStringCellValue();
  }

  private Optional<CellRangeAddress> mergedCellAddressOptional(Sheet sheet, Cell cell) {
    return sheet.getMergedRegions()
      .stream()
      .filter(cellAddress -> cellAddress.isInRange(cell))
      .findFirst();
  }


  private void writeTo(JsonBuilderFactory writeObjectBuilderFactory, String fieldLabel, String fieldName, String fieldValue) {

    JsonObject nameValueJson = writeObjectBuilderFactory.createObjectBuilder().add(fieldName, fieldValue).build();
    writeObjectBuilderFactory.createObjectBuilder().add(fieldLabel, nameValueJson);

  }

  private CellAddress findFieldLabelFromRow(Sheet sheet, int startRowNum, String fieldLabel) {
    if (StringUtils.isEmpty(fieldLabel)) {
      return null;
    }
    for (int i = startRowNum; i <= sheet.getLastRowNum(); i++) {
      Row row = sheet.getRow(i);
      if (row != null) { // Check if the row exists
        // Iterate over cells in the current row
        for (int j = row.getFirstCellNum(); j < row.getLastCellNum(); j++) {
          Cell cell = row.getCell(j);
          if (cell != null && CellType.STRING == cell.getCellType()) {
            String stringCellValue = cell.getStringCellValue();
            if (Strings.CI.contains(stringCellValue, fieldLabel)) {
              return cell.getAddress();
            }
//            System.out.print(cell.toString() + "\t");
          }
        }
      }
    }
    return null;
  }

  private String readHeaderSection(Sheet sheet, JsonObject section) {
    CellAddress activeCell = sheet.getActiveCell();
    SearchRange range = getSearchRange(section.getJsonObject("relativeSearchRange"));
    List<String> searchFor = section.getJsonArray("searchKeywords").getValuesAs(JsonString::getString).stream().toList();

    int startRow = activeCell.getRow();
    int startCol = activeCell.getColumn();

    for (int i = startRow; i < startRow + range.rows(); i++) {
      Row row = sheet.getRow(i);
      String cellValue = findInCurrentRow(sheet, row, searchFor, startCol, range);
      if (cellValue != null) {
        return cellValue;
      }
    }
    return null;
  }

  private static String findInCurrentRow(Sheet sheet, Row row, List<String> searchFor, int startCol, SearchRange range) {
    Iterator<Cell> cellIterator = row.cellIterator();
    while (cellIterator.hasNext()) {
      Cell next = cellIterator.next();
      switch (next.getCellType()) {
        case STRING -> {
          String cellValue = next.getStringCellValue().toLowerCase();
          boolean foundHeader = searchFor.stream().map(str -> str.trim().toLowerCase()).anyMatch(cellValue::contains);
          if (foundHeader) {
            sheet.setActiveCell(next.getAddress());
            return cellValue;
          } else if (next.getAddress().getColumn() > (startCol + range.columns())) { //Check ff we are going over searchable-area
            return null;
          }
        }
        case BLANK -> {
          if (next.getAddress().getColumn() > (startCol + range.columns())) { //Check ff we are going over searchable-area
            return null;
          }
        }
      }


    }
    return null;
  }

  private TransactionTableConfig getTransactionTableConfig(JsonObject table) {
    if (nonNull(table) && !table.isEmpty()) {
      return new TransactionTableConfig(table.getInt("headerRowOffset"), table.getJsonArray("columns").getValuesAs(JsonString::getString));
    }
    return null;
  }

  private List<FieldConfiguration> getFieldListForSection(JsonArray fields) {
    if (nonNull(fields) && !fields.isEmpty()) {
      List<FieldConfiguration> list = fields.stream().map(JsonValue::asJsonObject).map(field -> new FieldConfiguration(field.getString("name"), field.getString("label"), field.getString("pattern"), field.getBoolean("valueAfterText"))).toList();
      return list;
    }
    return null;
  }

  private SearchRange getSearchRange(JsonObject relativeSearchRange) {
    if (nonNull(relativeSearchRange) && !relativeSearchRange.isEmpty()) {
      return new SearchRange(relativeSearchRange.getInt("rows"), relativeSearchRange.getInt("columns"));
    }
    return new SearchRange(0, 0); //can be default configuration
  }

  private boolean isEmptySheet(Sheet sheet) {
    return sheet.getLastRowNum() == -1;
  }

  record FieldConfiguration(String name, String label, String pattern, boolean valueAfterText) {

  }

  private record SearchRange(int rows, int columns) {

  }

  private record TransactionTableConfig(int headerRowOffset, List<String> columnNames) {

  }
}
