package dev.shantanu.bankstatement.error;

public enum ErrorCode {
  NOT_SUPPORTED_FILE_FORMAT("NOT_SUPPORTED_FILE_FORMAT", "Provided input file format not supported."),
  ERROR_PARSING_FILE("PARSING_ERROR", "Can not parse file"),
  EMPTY_FILE("EMPTY_FILE", "File does not contain worksheet"),
  INVALID_FILE_FORMAT("INVALID_FILE_FORMAT", "Provided input file format is not valid. For Excel files supported file formats are .xls and .xlsx"),
  NOT_SUPPORTED_STATEMENT_TYPE("NOT_SUPPORTED_STATEMENT_TYPE", "Not supported statement type"),
  CONFIGURATION_ERROR("CONFIGURATION_ERROR", "Configuration error for bank account statement");

  private final String message;
  private final String code;

  private ErrorCode(String code, String message) {
    this.code = code;
    this.message = message;
  }

  public String getErrorMessage() {
    return "Error code = " + this.code + " error message = " + this.message;
  }
}
