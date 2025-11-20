package dev.shantanu.bankstatement.parser;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;

public record ParserUtils() {
  static String getStringValueOf(Cell cell) {
    final CellType cellType = cell.getCellType();
    return switch (cellType) {
      case STRING -> cell.getStringCellValue();
      case BLANK, _NONE -> "";
      case BOOLEAN -> cell.getBooleanCellValue() ? "true" : "false";
      case NUMERIC -> String.valueOf(cell.getNumericCellValue());
      case ERROR -> null;
      case FORMULA -> String.valueOf(cell.getCellFormula()); // TODO: Evaluate formula?
    };
  }
}
