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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
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

public record TransformTransactionRecord(ExcelSearchStatementParser excelSearchStatementParser) {

  public static final String CONFIG_KEY_DISPLAY_NAME = "displayName";
  public static final String CONFIG_KEY_DATA_TYPE = "dataType";
  public static final String CONFIG_KEY_VALUE = "value";
  private static final Logger logger = LoggerFactory.getLogger(TransformTransactionRecord.class);

  private static @NotNull JsonObject parseTransactionRow(JsonObject jsonObject) {
    JsonObject outputJson = new JsonObject();
    String dataType = jsonObject.get(CONFIG_KEY_DATA_TYPE).getAsString();

    String recordKey = jsonObject.get(CONFIG_KEY_MAPPED_TO).getAsString();
    String recordValue = jsonObject.get(CONFIG_KEY_VALUE).getAsString();
    final String error = "error";
    switch (dataType) {
      case "int" -> {
        try {
          int value = Integer.parseInt(recordValue);
          outputJson.addProperty(recordKey, value);
        } catch (NumberFormatException _) {
          outputJson.addProperty(error, String.format("Error parsing  %s as integer value.", recordValue));
          outputJson.addProperty(recordKey, recordValue);
        }
      }
      case "LocalDate" -> {
        LocalDate parsed = null;
        String[] patterns = {"dd/MM/yyyy", "dd-MM-yyyy", "yyyy-MM-dd", "d/M/yyyy"};
        for (String p : patterns) {
          try {
            parsed = LocalDate.parse(recordValue, DateTimeFormatter.ofPattern(p));
            break;
          } catch (DateTimeParseException _) {
            //ignore
          }
        }
        if (parsed != null) {
          outputJson.addProperty(recordKey, String.valueOf(parsed));
        } else {
          outputJson.addProperty(error, String.format("Error parsing  %s as LocalDate value.", recordValue));
          outputJson.addProperty(recordKey, String.valueOf(recordValue));
        }
      }
      case "BigDecimal" -> {
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
      case "String" -> outputJson.addProperty(recordKey, recordValue);
      default -> throw new IllegalStateException("Not supported data-type for conversion: " + dataType);
    }
    return outputJson;
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
      String stringValueInCurrentRow = excelSearchStatementParser.findStringValueInCurrentRow(sheet, row, searchFor);
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
      if (Objects.nonNull(excelSearchStatementParser.findStringValueInCurrentRow(sheet, row, displayNames)))
        break;
    }
    Row headerRow = sheet.getRow(sheet.getActiveCell().getRow());
    DataFormatter headerFormatter = new DataFormatter();
    Map<String, Integer> headerIndexMap = new HashMap<>();
    for (int c = headerRow.getFirstCellNum(); c < headerRow.getLastCellNum(); c++) {
      Cell hc = headerRow.getCell(c);
      String hv = (hc == null) ? "" : headerFormatter.formatCellValue(hc);
      String norm = hv == null ? "" : hv.trim().toLowerCase().replaceAll("\\s+", " ");
      if (!norm.isEmpty()) headerIndexMap.put(norm, c);
    }

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

  @NotNull
  Set<TransactionRecord> readAndMapTransactions(Sheet sheet, int startingRow, TransactionTableConfig transactionTableConfig,
                                                Map<ColumnField, Integer> columnNameToIndexMap) {

    Set<TransactionRecord> transactionRecords = new LinkedHashSet<>();
    DataFormatter formatter = new DataFormatter();
    FormulaEvaluator evaluator = sheet.getWorkbook().getCreationHelper().createFormulaEvaluator();
    int blankRowStreak = 0;

    for (int i = startingRow; i < sheet.getPhysicalNumberOfRows(); i++) {
      Row currentRow = sheet.getRow(i);
      if (currentRow == null) {
        blankRowStreak++;
        if (blankRowStreak >= 3) break;
        else continue;
      }
      boolean allBlank = columnNameToIndexMap.values().stream().allMatch(colIdx -> {
        if (colIdx == null || colIdx < 0) return true;
        Cell c = currentRow.getCell(colIdx);
        String v = (c == null) ? "" : formatter.formatCellValue(c, evaluator).trim();
        return v.isEmpty();
      });
      if (allBlank) {
        blankRowStreak++;
        if (blankRowStreak >= 3) break;
        else continue;
      } else {
        blankRowStreak = 0;
      }

      // Map Row to TransactionRecord
      final BiFunction<Row, Map<ColumnField, Integer>, TransactionRecord> transactionRecordFunction = (rowToRead, colNameToIdx) -> {
        Optional<JsonObject> finalJsonForRow = colNameToIdx.entrySet()
          .stream()
          .map(entry -> applyColumnFieldConfig(rowToRead, entry, formatter, evaluator))
          .map(TransformTransactionRecord::parseTransactionRow)
          .reduce((jo1, jo2) -> {
            jo2.keySet()
              .forEach(key -> jo1.asMap().putIfAbsent(key, jo2.get(key)));

            //For error at field level
            jo2.keySet().stream()
              .filter(key -> Strings.CI.equals("error", key))
              .forEach(key -> jo1.asMap()
                .computeIfPresent(key, (key1, value) ->
                  new JsonPrimitive(value.getAsString() + "|" + jo2.get(key1).getAsString())
                ));
            return jo1;
          });

        return finalJsonForRow.map(jsonObject -> {
          if (isValidTransactionRecord(jsonObject, transactionTableConfig.columnFields()))
            return GSON.instance().fromJson(jsonObject, TransactionRecord.class);
          else
            return null;
        }).orElse(null);
      };
      TransactionRecord transactionRecord = transactionRecordFunction.apply(currentRow, columnNameToIndexMap);
      if (transactionRecord != null) {
        transactionRecords.add(transactionRecord);
      }
    }
    logger.debug("Parsed {} transactions", transactionRecords.size());

    List<String> errorList = transactionRecords.stream().map(TransactionRecord::error).filter(Objects::nonNull).toList();
    if (!errorList.isEmpty()) {
      String first = errorList.getFirst();
      String last = errorList.getLast();
      long totalErrors = errorList.size();
      logger.debug("Errors encountered  = {}. First Error = {}. Last Error = {} ", totalErrors, first, last);
    }

    return transactionRecords;
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
        if (Strings.CI.startsWith("Withdrawal Amount", key.trim())) {

        }
        return el != null && !el.isJsonNull() && StringUtils.isNotBlank(el.getAsString());
      })
      .count();
    JsonElement errors = jsonObject.get("error");
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