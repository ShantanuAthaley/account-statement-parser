package dev.shantanu.bankstatement.config;

public record IciciSearchStatementConfig() implements StatementConfiguration {
  static StatementConfiguration.ConfigurationBuilder configurationBuilder = new ConfigurationBuilder(StatementType.ICICI_BANK_SEARCH_STATEMENT);
  public IciciSearchStatementConfig() {
    configurationBuilder.build();
  }

  @Override
  public StatementType getStatementType() {
    return configurationBuilder.type();
  }
}
