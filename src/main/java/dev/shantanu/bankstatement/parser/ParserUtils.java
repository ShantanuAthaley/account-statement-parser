package dev.shantanu.bankstatement.parser;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;

public record ParserUtils() {
  static String getStringValueOf(Cell cell) {
    FormulaEvaluator evaluator = cell.getSheet().getWorkbook().getCreationHelper().createFormulaEvaluator();
    DataFormatter formatter = new DataFormatter();
    final CellType cellType = cell.getCellType();
    return switch (cellType) {
      case STRING -> cell.getStringCellValue();
      case BLANK, _NONE -> "";
      case BOOLEAN -> cell.getBooleanCellValue() ? "true" : "false";
      case NUMERIC -> formatter.formatCellValue(cell);
      case ERROR -> null;
      case FORMULA -> evaluator.evaluate(cell).formatAsString();
    };
  }
}
