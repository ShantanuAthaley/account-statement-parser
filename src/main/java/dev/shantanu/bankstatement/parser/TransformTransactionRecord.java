package dev.shantanu.bankstatement.parser;

import static dev.shantanu.bankstatement.common.GsonSingleton.GSON;
import static dev.shantanu.bankstatement.parser.ExcelSearchStatementParser.CONFIG_KEY_MAPPED_TO;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.shantanu.bankstatement.common.TransactionRecord;
import dev.shantanu.bankstatement.config.ColumnField;
import dev.shantanu.bankstatement.config.TransactionTableConfig;
import dev.shantanu.bankstatement.error.AccountStatementException;
import dev.shantanu.bankstatement.error.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record TransformTransactionRecord() {

  public static final String CONFIG_KEY_DISPLAY_NAME = "displayName";
  public static final String CONFIG_KEY_DATA_TYPE = "dataType";
  public static final String CONFIG_KEY_VALUE = "value";
  public static final String ERROR = "error";
  private static final Logger logger = LoggerFactory.getLogger(TransformTransactionRecord.class);
  private static final int MAX_CONSECUTIVE_BLANK_ROWS = 3;
  private static final AtomicInteger consecutiveBlankRows = new AtomicInteger(0);

  private static @NotNull JsonObject parseDataInTransactionRow(JsonObject jsonObject) {
    JsonObject outputJson = new JsonObject();
    String dataType = jsonObject.get(CONFIG_KEY_DATA_TYPE).getAsString();

    String recordKey = jsonObject.get(CONFIG_KEY_MAPPED_TO).getAsString();
    String recordValue = jsonObject.get(CONFIG_KEY_VALUE).getAsString();

    switch (dataType) {
      case "int" -> parseNumericValues(recordValue, outputJson, recordKey, ERROR);
      case "LocalDate" -> parseDateValues(recordValue, outputJson, recordKey, ERROR);
      case "BigDecimal" -> parseDecimalValues(recordValue, outputJson, recordKey, ERROR);
      case "String" -> outputJson.addProperty(recordKey, recordValue);
      default -> throw new IllegalStateException("Not supported data-type for conversion: " + dataType);
    }
    return outputJson;
  }

  private static void parseNumericValues(String recordValue, JsonObject outputJson, String recordKey, String error) {
    try {
      int value = Integer.parseInt(recordValue);
      outputJson.addProperty(recordKey, value);
    } catch (NumberFormatException _) {
      outputJson.addProperty(error, String.format("Error parsing  %s as integer value.", recordValue));
      outputJson.addProperty(recordKey, recordValue);
    }
  }

  private static void parseDecimalValues(String recordValue, JsonObject outputJson, String recordKey, String error) {
    try {
      String s = recordValue == null ? "" : recordValue.trim();
      s = s.replace("₹", "");
      s = s.replaceAll("(?i)INR|RS|CR|DR", "");
      boolean negative = false;
      if (s.startsWith("(") && s.endsWith(")")) {
        negative = true;
        s = s.substring(1, s.length() - 1);
      }
      s = s.replace(",", "").replace("\\s+", "");
      BigDecimal value = StringUtils.isBlank(s) ? BigDecimal.valueOf(0.0) : new BigDecimal(s);
      if (negative) value = value.negate();
      outputJson.addProperty(recordKey, value);
    } catch (Exception _) {
      outputJson.addProperty(error, String.format("Error parsing  %s as BigDecimal value", recordValue));
      outputJson.addProperty(recordKey, recordValue);
    }
  }

  private static void parseDateValues(String recordValue, JsonObject outputJson, String recordKey, String error) {
    final String[] patterns = {"dd/MM/yyyy", "dd-MM-yyyy", "yyyy-MM-dd", "d/M/yyyy"};
    Optional<LocalDate> parsedDate = Arrays.stream(patterns)
      .map(pattern -> {
        try {
          return Optional.of(LocalDate.parse(recordValue, DateTimeFormatter.ofPattern(pattern)));
        } catch (DateTimeParseException _) {
          return Optional.<LocalDate>empty();
        }
      })
      .filter(Optional::isPresent)
      .map(Optional::get)
      .findFirst();

    if (parsedDate.isPresent()) {
      outputJson.addProperty(recordKey, String.valueOf(parsedDate.get()));
    } else {
      outputJson.addProperty(error, String.format("Error parsing %s as LocalDate value.", recordValue));
      outputJson.addProperty(recordKey, recordValue);
    }
  }

  private static @NotNull JsonObject applyColumnFieldConfig(Row rowToRead, Entry<ColumnField, Integer> entry, DataFormatter formatter, FormulaEvaluator evaluator) {
    String columnName = entry.getKey().displayName();
    String mappedTo = entry.getKey().mappedTo();
    String dataType = entry.getKey().dataType();
    Cell cell = rowToRead.getCell(entry.getValue());

    String recordValue = (cell == null) ? "" : formatter.formatCellValue(cell, evaluator);

    JsonObject jsonObject = new JsonObject();
    jsonObject.addProperty(CONFIG_KEY_MAPPED_TO, mappedTo);
    jsonObject.addProperty(CONFIG_KEY_VALUE, recordValue);
    jsonObject.addProperty(CONFIG_KEY_DISPLAY_NAME, columnName);
    jsonObject.addProperty(CONFIG_KEY_DATA_TYPE, dataType);
    return jsonObject;
  }

  Set<TransactionRecord> getTransactions(Sheet sheet, List<String> searchFor,
                                         TransactionTableConfig transactionTableConfig) {

    int startRowNumber = sheet.getActiveCell().getRow();
    int physicalNumberOfRows = sheet.getPhysicalNumberOfRows();

    //Look for Title (can be skipped) = Transactions List
    for (int i = startRowNumber; i < physicalNumberOfRows; i++) {
      Row row = sheet.getRow(i);
      String stringValueInCurrentRow = ParserUtils.findStringValueInCurrentRow(sheet, row, searchFor);
      if (StringUtils.isNotEmpty(stringValueInCurrentRow)) {
        startRowNumber = sheet.getActiveCell().getRow() + 1;
        break;
      }
    }
    //Look for transaction-header row and map the columnIndex
    List<ColumnField> columnFields = transactionTableConfig.columnFields();

    for (int i = startRowNumber; i < physicalNumberOfRows; i++) {
      Row row = sheet.getRow(i);
      List<String> displayNames = columnFields.stream().map(ColumnField::displayName).toList();
      if (Objects.nonNull(ParserUtils.findStringValueInCurrentRow(sheet, row, displayNames)))
        break;
    }
    Row headerRow = sheet.getRow(sheet.getActiveCell().getRow());
    Map<String, Integer> headerIndexMap = buildTransactionHeaderRowToIndexMap(headerRow);

    Map<ColumnField, Integer> columnNameToIndexMap = columnFields.stream().collect(
      Collectors.toMap(
        Function.identity(), cf -> {
          String target = cf.displayName().trim().toLowerCase().replaceAll("\\s+", " ");
          Integer idx = headerIndexMap.get(target);
          if (idx != null) return idx;
          // fallback: contains
          for (Map.Entry<String, Integer> e : headerIndexMap.entrySet()) {
            if (e.getKey().contains(target) || target.contains(e.getKey())) return e.getValue();
          }
          return -1;
        }
      ));

    if (columnNameToIndexMap.isEmpty()) {
      throw new AccountStatementException(ErrorCode.ERROR_PARSING_FILE,
        "Could not find transactions in the input file",
        new IllegalStateException());
    }
    int transactionStartRow = sheet.getActiveCell().getRow() + 1;
    return readAndMapTransactions(sheet, transactionStartRow, transactionTableConfig, columnNameToIndexMap);
  }

  private static @NotNull Map<String, Integer> buildTransactionHeaderRowToIndexMap(Row headerRow) {
    DataFormatter headerFormatter = new DataFormatter();
    Map<String, Integer> headerIndexMap = new HashMap<>();

    for (int c = headerRow.getFirstCellNum(); c < headerRow.getLastCellNum(); c++) {
      Cell hc = headerRow.getCell(c);
      String hv = (hc == null) ? "" : headerFormatter.formatCellValue(hc);
      String norm = hv == null ? "" : hv.trim().toLowerCase().replaceAll("\\s+", " ");
      if (!norm.isEmpty()) headerIndexMap.put(norm, c);
    }

    return headerIndexMap;
  }

  /**
   * Reads and maps rows from the sheet into TransactionRecord objects.
   * Stops processing after encountering 3 or more consecutive blank rows.
   *
   * @param sheet                  The sheet containing transaction data
   * @param startingRow            The row number to start processing from (0-based)
   * @param transactionTableConfig Configuration for the transaction table
   * @param columnNameToIndexMap   Mapping of column fields to their indices
   * @return Set of parsed TransactionRecord objects
   */
  @NotNull
  Set<TransactionRecord> readAndMapTransactions(Sheet sheet, int startingRow,
                                                TransactionTableConfig transactionTableConfig,
                                                Map<ColumnField, Integer> columnNameToIndexMap) {

    DataFormatter formatter = new DataFormatter();
    FormulaEvaluator evaluator = sheet.getWorkbook().getCreationHelper().createFormulaEvaluator();

    Set<TransactionRecord> transactionRecords = IntStream.range(startingRow, sheet.getPhysicalNumberOfRows())
      .mapToObj(sheet::getRow)
      .takeWhile(row -> shouldContinueProcessing(row, columnNameToIndexMap, formatter, evaluator))
      .map(row -> createTransactionRecord(row, columnNameToIndexMap, formatter, evaluator, transactionTableConfig))
      .filter(Objects::nonNull)
      .collect(Collectors.toCollection(LinkedHashSet::new));

    logProcessingResults(transactionRecords);
    return transactionRecords;
  }

  /**
   * Determines if processing should continue based on blank row detection.
   */
  private boolean shouldContinueProcessing(Row currentRow,
                                           Map<ColumnField, Integer> columnMap,
                                           DataFormatter formatter,
                                           FormulaEvaluator evaluator) {
    if (currentRow == null || isRowBlank(columnMap, currentRow, formatter, evaluator)) {
      if (consecutiveBlankRows.incrementAndGet() > MAX_CONSECUTIVE_BLANK_ROWS) {
        return false;
      }
    } else {
      consecutiveBlankRows.set(0);
    }
    return true;
  }

  /**
   * Creates a TransactionRecord from a row if it's valid.
   */
  private TransactionRecord createTransactionRecord(Row row,
                                                    Map<ColumnField, Integer> columnMap,
                                                    DataFormatter formatter,
                                                    FormulaEvaluator evaluator,
                                                    TransactionTableConfig config) {
    return Optional.ofNullable(processRowToJson(row, columnMap, formatter, evaluator))
      .filter(json -> isValidTransactionRecord(json, config.columnFields()))
      .map(json -> GSON.instance().fromJson(json, TransactionRecord.class))
      .orElse(null);
  }

  /**
   * Processes a single row into a JsonObject.
   */
  private JsonObject processRowToJson(Row row,
                                      Map<ColumnField, Integer> columnMap,
                                      DataFormatter formatter,
                                      FormulaEvaluator evaluator) {
    return columnMap.entrySet().stream()
      .map(entry -> applyColumnFieldConfig(row, entry, formatter, evaluator))
      .map(TransformTransactionRecord::parseDataInTransactionRow)
      .reduce(this::mergeJsonObjects)
      .orElse(null);
  }

  /**
   * Merges two JsonObjects, combining error messages when necessary.
   */
  private JsonObject mergeJsonObjects(JsonObject first, JsonObject second) {
    second.keySet().forEach(key -> {
      if (Strings.CI.equals(ERROR, key)) {
        first.asMap().computeIfPresent(key, (k, v) ->
          new JsonPrimitive(v.getAsString() + "|" + second.get(key).getAsString())
        );
      } else {
        first.asMap().putIfAbsent(key, second.get(key));
      }
    });
    return first;
  }

  /**
   * Logs the results of transaction processing.
   */
  private void logProcessingResults(Set<TransactionRecord> records) {
    logger.debug("Parsed {} transactions", records.size());

    List<String> errors = records.stream()
      .map(TransactionRecord::error)
      .filter(Objects::nonNull)
      .toList();

    if (!errors.isEmpty()) {
      logger.debug("Errors encountered = {}. First Error = {}. Last Error = {}",
        errors.size(), errors.getFirst(), errors.getLast());
    }
  }

  private static boolean isRowBlank(Map<ColumnField, Integer> columnNameToIndexMap, Row currentRow, DataFormatter formatter, FormulaEvaluator evaluator) {
    return columnNameToIndexMap.values().stream().allMatch(colIdx -> {
      if (colIdx == null || colIdx < 0) return true;
      Cell c = currentRow.getCell(colIdx);
      String v = (c == null) ? "" : formatter.formatCellValue(c, evaluator).trim();
      return v.isEmpty();
    });
  }

  /**
   * Helps to determine if we are reading beyond transaction table rows.
   *
   * @param jsonObject   JsonObject
   * @param columnFields List<ColumnField>
   * @return boolean true - when we can read at least 4 non null/non empty values else false
   */
  private boolean isValidTransactionRecord(JsonObject jsonObject, List<ColumnField> columnFields) {
    List<String> mappedToPropertiesList = columnFields.stream().map(ColumnField::mappedTo).toList();
    Map<String, JsonElement> map = jsonObject.asMap();

    //If we read less than 4 values, we are sure we are out of the transaction table range.
    long numberOfColumnsParsed = mappedToPropertiesList.stream()
      .filter(key -> {
        JsonElement el = map.get(key);
        return el != null && !el.isJsonNull() && StringUtils.isNotBlank(el.getAsString());
      })
      .count();
    JsonElement errors = jsonObject.get(ERROR);
    if (errors != null && errors.getAsString().split("\\|").length > 3) {
      return false;
    }
    return numberOfColumnsParsed >= 5;
  }

  /**
   * Returns the {@code Optional<CellRangeAddress>} if the cell is in the merged-region
   *
   * @param sheet sheet of the account statement
   * @param cell  cell to check
   * @return CellRangeAddress of the merged-region or else Optional.empty()
   */
  Optional<CellRangeAddress> mergedCellAddressOptional(Sheet sheet, Cell cell) {
    return sheet.getMergedRegions()
      .stream()
      .filter(cellAddress -> cellAddress.isInRange(cell))
      .findFirst();
  }

  TransactionTableConfig getTransactionTableConfig(JsonObject table) {
    if (Objects.nonNull(table) && !table.isEmpty()) {
      List<ColumnField> columnFields = table.get("columns")
        .getAsJsonArray()
        .asList()
        .stream()
        .map(JsonElement::getAsJsonObject)
        .map(jsonObject -> GSON.instance().fromJson(jsonObject, ColumnField.class))
        .toList();
      return new TransactionTableConfig(table.get("headerRowOffset").getAsInt(), columnFields);
    }
    return null;
  }
}