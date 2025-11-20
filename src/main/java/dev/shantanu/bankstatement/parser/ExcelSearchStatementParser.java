package dev.shantanu.bankstatement.parser;

import static dev.shantanu.bankstatement.common.GsonSingleton.GSON;
import static java.util.Comparator.comparingInt;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.shantanu.bankstatement.common.AccountStatement;
import dev.shantanu.bankstatement.common.TransactionRecord;
import dev.shantanu.bankstatement.config.FieldConfiguration;
import dev.shantanu.bankstatement.config.SearchRangeConfig;
import dev.shantanu.bankstatement.config.StatementConfiguration;
import dev.shantanu.bankstatement.config.TransactionTableConfig;
import dev.shantanu.bankstatement.error.AccountStatementException;
import dev.shantanu.bankstatement.error.ErrorCode;
import dev.shantanu.bankstatement.parser.model.TransactionInfo;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellAddress;
import org.apache.poi.ss.util.CellRangeAddress;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
Parser for ICICI bank (advance) search statement
 */

class ExcelSearchStatementParser implements AccountStatementParser {

  public static final String CONFIG_KEY_MAPPED_TO = "mappedTo";
  public static final String CONFIG_KEY_TITLE = "title";
  public static final String ERROR = "error";
  public static final String CONFIG_KEY_ID = "id";
  public static final String CONFIG_KEY_SEARCH_KEYWORDS = "searchKeywords";
  public static final String CONFIG_KEY_FIELDS = "fields";
  private static final Logger logger = LoggerFactory.getLogger(ExcelSearchStatementParser.class);
  private final StatementConfiguration statementConfiguration;
  private final File statementFile;
  private final TransformTransactionRecord transformTransactionRecord = new TransformTransactionRecord(this);

  public ExcelSearchStatementParser(File statementFile, StatementConfiguration statementConfiguration) {
    this.statementFile = statementFile;
    this.statementConfiguration = statementConfiguration;
  }

  private static String findStringValueInCurrentRow(Sheet sheet, Row row, List<String> searchFor,
                                                    int startCol, SearchRangeConfig range) {
    Iterator<Cell> cellIterator = row.cellIterator();
    while (cellIterator.hasNext()) {
      Cell next = cellIterator.next();

      if (reachedEndOfSearchableArea(startCol, range, next)) {
        return null;
      }

      String cellValue = ParserUtils.getStringValueOf(next).toLowerCase();
      boolean foundHeader = searchFor.stream().map(str -> str.trim().toLowerCase()).anyMatch(cellValue::contains);
      if (foundHeader) {
        sheet.setActiveCell(next.getAddress());
        return cellValue;
      }
    }
    return null;
  }

  private static boolean reachedEndOfSearchableArea(int startCol, SearchRangeConfig range, Cell next) {
    return next.getAddress().getColumn() > (startCol + range.columns());
  }

  @Override
  public AccountStatement getTransactionInformation() {
    try (Workbook workbook = WorkbookFactory.create(statementFile)) {
      int numberOfSheets = workbook.getNumberOfSheets();
      if (numberOfSheets == 0) {
        throw new AccountStatementException(ErrorCode.EMPTY_FILE, "No worksheet found in the input file = " + statementFile, new IllegalStateException());
      }
      Sheet sheet = workbook.getSheetAt(0);
      return parseSheet(sheet);
    } catch (IOException e) {
      logger.error("Exception while reading file {}. Error message = {}, casued by = {} ", statementFile.getName(), e.getMessage(), e.getCause().getMessage());
      throw new AccountStatementException(ErrorCode.INVALID_FILE_FORMAT, "Could not open the workbook", new IllegalStateException());
    }

  }

  private AccountStatement parseSheet(Sheet sheet) {
    boolean isEmpty = isEmptySheet(sheet);
    if (isEmpty) {
      throw new AccountStatementException(ErrorCode.EMPTY_FILE, "Empty sheet", new IllegalStateException());
    }

    ToIntFunction<JsonObject> getOrder = jo -> jo.get("order").getAsInt();
    Comparator<JsonObject> sectionComparatorByOrder = comparingInt(getOrder);
    List<JsonObject> jsonSectionConfigList = this.statementConfiguration.getSections().stream().sorted(sectionComparatorByOrder).toList();

    int firstRowNum = sheet.getFirstRowNum();
    int lastRowNum = sheet.getLastRowNum();
    int physicalNumberOfRows = sheet.getPhysicalNumberOfRows();
    int physicalNumberOfCells = sheet.getRow(0).getPhysicalNumberOfCells();
    short firstCellNum = sheet.getRow(15).getFirstCellNum();
    short lastCellNum = sheet.getRow(15).getLastCellNum();

    logger.debug("firstRowNum={} lastRowNum={} physicalNumberOfRows={} physicalNumberOfCells={} firstCellNum={} lastCellNum={}", firstRowNum, lastRowNum, physicalNumberOfRows, physicalNumberOfCells, firstCellNum, lastCellNum);

    return parsedSections(jsonSectionConfigList, sheet);

  }

  /**
   * @param jsonSectionConfigList List of all section config from {@link resources/excelStatementConfig.json }
   * @param sheet                 represents input file sheet object
   * @return {@link AccountStatement}
   */
  private AccountStatement parsedSections(List<JsonObject> jsonSectionConfigList, Sheet sheet) {
    JsonObject parsedSections = new JsonObject();
    Set<TransactionRecord> transactions = Set.of();

    for (var sectionConfig : jsonSectionConfigList) {
      String sectionId = sectionConfig.get(CONFIG_KEY_ID).getAsString();
      JsonObject parsedJsonSection = new JsonObject();

      // Add from config, common across all section title, mappedTo
      parsedJsonSection.addProperty(CONFIG_KEY_TITLE, sectionConfig.get(CONFIG_KEY_TITLE).getAsString());
      JsonElement mappedToConfigValue = sectionConfig.get(CONFIG_KEY_MAPPED_TO);

      if (mappedToConfigValue != null) {
        parsedJsonSection.addProperty(CONFIG_KEY_MAPPED_TO, mappedToConfigValue.getAsString());
      }

      switch (sectionId) {
        case "header" -> {
          String headerTitle = readHeaderSection(sheet, sectionConfig);
          if (StringUtils.isNotEmpty(headerTitle)) {
            parsedJsonSection.addProperty(CONFIG_KEY_TITLE, headerTitle);
            parsedSections.add(sectionId, parsedJsonSection);
          } else {
            parsedJsonSection.addProperty(ERROR, "Could not find header");
          }
        }
        case "search_criteria" -> {
          List<FieldConfiguration> fieldConfigList = getFieldListForSection(sectionConfig.getAsJsonArray(CONFIG_KEY_FIELDS));
          var parsedFieldsJson = readAndMapFields(sheet, fieldConfigList);
          parsedFieldsJson.asMap().forEach(parsedJsonSection.asMap()::putIfAbsent);
          parsedSections.add(sectionId, parsedJsonSection);
        }
        case "advance_search" -> {
          boolean skip = sectionConfig.get("skip").getAsBoolean();
          if (!skip) {
            List<FieldConfiguration> fieldConfigList = getFieldListForSection(sectionConfig.getAsJsonArray(CONFIG_KEY_FIELDS));
            var parsedFieldsJson = readAndMapFields(sheet, fieldConfigList);
            parsedJsonSection.add(sectionId, parsedFieldsJson);
          }
        }
        case "transactions_table" -> {
          List<String> searchFor = sectionConfig.getAsJsonArray(CONFIG_KEY_SEARCH_KEYWORDS)
            .asList()
            .stream()
            .map(JsonElement::getAsString)
            .toList();

          TransactionTableConfig transactionTableConfig = transformTransactionRecord.getTransactionTableConfig(sectionConfig.get("table").getAsJsonObject());
          transactions = transformTransactionRecord.getTransactions(sheet, searchFor, transactionTableConfig);
        }
        default -> logger.info("Don't have capability to parse section with id = {} ", sectionId);
      }
    }
    TransactionInfo transactionInfo = GSON.instance().fromJson(parsedSections.get("search_criteria").getAsJsonObject(), TransactionInfo.class);
    return new AccountStatement(transactionInfo, transactions);


  }

  private @NotNull JsonObject readAndMapFields(Sheet sheet, List<FieldConfiguration> fields) {
    return fields.stream().map(fieldConfig -> getFieldValue(sheet, fieldConfig))
      .reduce(new JsonObject(), (accumulator, current) -> {
        Map<String, JsonElement> accumulatorMap = accumulator.asMap();
        current.asMap().forEach(accumulatorMap::putIfAbsent);
        return accumulator;
      });
  }

  String findStringValueInCurrentRow(Sheet sheet, Row row, List<String> searchFor) {
    int startCol = row.getFirstCellNum();
    SearchRangeConfig searchRangeConfig = new SearchRangeConfig(sheet.getPhysicalNumberOfRows(), row.getPhysicalNumberOfCells());
    return findStringValueInCurrentRow(sheet, row, searchFor, startCol, searchRangeConfig);
  }

  private JsonObject getFieldValue(Sheet sheet, FieldConfiguration fieldConfig) {

    CellAddress activeCell = sheet.getActiveCell();
    String labelToSearch = fieldConfig.label();
    String pattern = fieldConfig.pattern();
    List<String> patternMappedFields = fieldConfig.patternMappedFields();
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

      logger.debug("fieldLabel = {}  fieldValue = {}", fieldLabel, fieldValue);

      JsonObject multiFields = getPatternMappedFields(fieldValue, pattern, patternMappedFields);

      JsonObject field = new JsonObject();

      if (!multiFields.isEmpty()) {
        multiFields.asMap().forEach(field::add);
      } else {
        field.addProperty(fieldConfig.name(), fieldValue);
      }

      sheet.setActiveCell(cellAddressOfLabel);
      return field;
    }
    return new JsonObject();
  }

  private JsonObject getPatternMappedFields(String fieldValue, String pattern, List<String> patternMappedFields) {
    JsonObject result = new JsonObject();
    Pattern p = Pattern.compile(pattern);
    Matcher matcher = p.matcher(fieldValue);

    Map<String, Integer> namedGroups = p.namedGroups();

    if (!namedGroups.isEmpty() && matcher.matches()) {
      namedGroups.forEach((name, index) -> result.addProperty(name, matcher.group(name)));
    } else if (matcher.matches()) {
      result.addProperty(patternMappedFields.getFirst(), matcher.group());
    }
    return result;
  }

  private String getAdjacentValue(Sheet sheet, Row row, Cell cell, int column) {
    int adjacentColumn = transformTransactionRecord.mergedCellAddressOptional(sheet, cell).map(CellRangeAddress::getLastColumn).map(col -> col + 1).orElse(column + 1);

    Cell result = sheet.getRow(row.getRowNum()).getCell(adjacentColumn);
    return ParserUtils.getStringValueOf(result);

  }

  private CellAddress findFieldLabelFromRow(Sheet sheet, int startRowNum, String fieldLabel) {
    if (StringUtils.isEmpty(fieldLabel)) {
      return null;
    }
    for (int i = startRowNum; i <= sheet.getLastRowNum(); i++) {
      Row row = sheet.getRow(i);
      // Iterate over cells in the current row
      for (int j = row.getFirstCellNum(); j < row.getLastCellNum(); j++) {
        Cell cell = row.getCell(j);
        if (cell != null && CellType.STRING == cell.getCellType()) {
          String stringCellValue = cell.getStringCellValue();
          if (Strings.CI.contains(stringCellValue, fieldLabel)) {
            return cell.getAddress();
          }
        }
      }

    }
    return null;
  }

  private String readHeaderSection(Sheet sheet, JsonObject section) {
    CellAddress activeCell = sheet.getActiveCell();
    SearchRangeConfig range = getSearchRange(section.get("relativeSearchRange").getAsJsonObject());
    List<String> searchFor = section.get(CONFIG_KEY_SEARCH_KEYWORDS)
      .getAsJsonArray()
      .asList()
      .stream()
      .map(JsonElement::getAsString)
      .toList();
    if (activeCell == null) {
      Row row = sheet.getRow(sheet.getFirstRowNum());
      Cell cell = row.getCell(row.getFirstCellNum());
      sheet.setActiveCell(cell.getAddress());
      activeCell = cell.getAddress();
    }
    int startRow = activeCell.getRow();
    int startCol = activeCell.getColumn();

    for (int i = startRow; i < startRow + range.rows(); i++) {
      Row row = sheet.getRow(i);

      String cellValue = findStringValueInCurrentRow(sheet, row, searchFor, startCol, range);
      if (cellValue != null) {
        String cellReference = sheet.getActiveCell().formatAsR1C1String();
        section.addProperty("cellReference", cellReference);
        return cellValue;
      }
    }
    return null;
  }

  private List<FieldConfiguration> getFieldListForSection(JsonArray fields) {
    if (nonNull(fields) && !fields.isEmpty()) {
      return fields.asList().stream().map(JsonElement::getAsJsonObject)
        .map(jsonObject -> GSON.instance()
          .fromJson(jsonObject, FieldConfiguration.class))
        .toList();
    }
    return Collections.emptyList();
  }

  private SearchRangeConfig getSearchRange(JsonObject relativeSearchRange) {
    if (nonNull(relativeSearchRange) && !relativeSearchRange.isEmpty()) {
      Gson gson = GSON.instance();
      return gson.fromJson(relativeSearchRange, SearchRangeConfig.class);
    }
    return new SearchRangeConfig(0, 0); //can be default configuration
  }

  private boolean isEmptySheet(Sheet sheet) {
    return sheet.getLastRowNum() == -1;
  }
}
