package dev.shantanu.bankstatement.parser;

import dev.shantanu.bankstatement.config.SearchRangeConfig;
import java.util.Iterator;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

public record ParserUtils() {
  static String getStringValueOf(Cell cell) {
    FormulaEvaluator evaluator = cell.getSheet().getWorkbook().getCreationHelper().createFormulaEvaluator();
    DataFormatter formatter = new DataFormatter();
    final CellType cellType = cell.getCellType();
    return switch (cellType) {
      case BLANK, _NONE, ERROR -> "";
      case STRING -> cell.getStringCellValue();
      case BOOLEAN -> cell.getBooleanCellValue() ? "true" : "false";
      case NUMERIC -> formatter.formatCellValue(cell);
      case FORMULA -> evaluator.evaluate(cell).formatAsString();
    };
  }

  static String findStringValueInCurrentRow(Sheet sheet, Row row, List<String> searchFor,
                                            int startCol, SearchRangeConfig range) {
    Iterator<Cell> cellIterator = row.cellIterator();
    while (cellIterator.hasNext()) {
      Cell next = cellIterator.next();

      if (reachedEndOfSearchableArea(startCol, range, next)) {
        return null;
      }

      String cellValue = getStringValueOf(next).toLowerCase();
      boolean foundHeader = searchFor.stream().map(str -> str.trim().toLowerCase()).anyMatch(cellValue::contains);
      if (foundHeader) {
        sheet.setActiveCell(next.getAddress());
        return cellValue;
      }
    }
    return null;
  }

  static String findStringValueInCurrentRow(Sheet sheet, Row row, List<String> searchFor) {
    int startCol = row.getFirstCellNum();
    SearchRangeConfig searchRangeConfig = new SearchRangeConfig(sheet.getPhysicalNumberOfRows(), row.getPhysicalNumberOfCells());
    return findStringValueInCurrentRow(sheet, row, searchFor, startCol, searchRangeConfig);
  }

  static boolean reachedEndOfSearchableArea(int startCol, SearchRangeConfig range, Cell next) {
    return next.getAddress().getColumn() > (startCol + range.columns());
  }
}
