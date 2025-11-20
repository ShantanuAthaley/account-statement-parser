package dev.shantanu.bankstatement.common;

public enum FileType {
  XLS("excel", "xls"),
  XLSX("excel", "xlsx");

  private final String fileExtension;
  private final String typeName;

  private FileType(String typeName, String fileExtension) {
    this.fileExtension = fileExtension;
    this.typeName = typeName;
  }

  public String getFileExtension() {
    return this.fileExtension;
  }

  public String getTypeName() {
    return this.typeName;
  }
}
