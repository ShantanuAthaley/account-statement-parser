package dev.shantanu.bankstatement.config;

import dev.shantanu.bankstatement.common.FileType;

public enum StatementType {
  ICICI_BANK_SEARCH_STATEMENT("iciciBankSearchStatementConfig", FileType.XLS);

  private final String configurationRoot;
  private final FileType fileType;

  StatementType(String configurationRoot, FileType fileType) {
    this.configurationRoot = configurationRoot;
    this.fileType = fileType;
  }

  public static StatementType getDefault() {
    return StatementType.ICICI_BANK_SEARCH_STATEMENT;
  }

  public FileType getFileType() {
    return fileType;
  }

  String getConfigRoot() {
    return this.configurationRoot;
  }
}
