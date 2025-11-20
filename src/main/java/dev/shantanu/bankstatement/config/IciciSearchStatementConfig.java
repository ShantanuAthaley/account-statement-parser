package dev.shantanu.bankstatement.config;

import com.google.gson.JsonObject;
import java.util.List;

public final class IciciSearchStatementConfig implements StatementConfiguration {
  private static final Config CONFIG;

  public IciciSearchStatementConfig() {
  }

  @Override
  public StatementType statementType() {
    return CONFIG.statementType();
  }

  public List<JsonObject> getSections() {
    return CONFIG.getSections();
  }

  static {
    CONFIG = StatementConfiguration.builder(StatementType.ICICI_BANK_SEARCH_STATEMENT).build();
  }
}
