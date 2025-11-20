package dev.shantanu.bankstatement.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.shantanu.bankstatement.error.AccountStatementException;
import dev.shantanu.bankstatement.error.ErrorCode;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Account statement configuration. Responsible to read the JSON configuration file.
 * The JSON configuration file used to describe the account statements.
 * The configuration file has root element which is mapped to bank-name + account-statement file format Excel (.xls, .xlsx).json
 * Root element can be described by 'sections' array. Sections array declares fields, tables or simply header value to read.
 */
public interface StatementConfiguration {
  Logger log = LoggerFactory.getLogger(StatementConfiguration.class);
  String STATEMENT_CONFIG = "StatementConfig";

  StatementType statementType();

  List<JsonObject> getSections();

  static Builder builder(StatementType type) {
    return new Builder(type);
  }

  static Builder withDefault() {
    return new Builder(StatementType.getDefault());
  }

  // Immutable record representing loaded configuration
  record Config(StatementType type, List<JsonObject> sections) implements StatementConfiguration {
    @Override
    public List<JsonObject> getSections() {
      return sections;
    }

    @Override
    public StatementType statementType() {
      return type;
    }
  }

  // Builder class
  final class Builder {
    private final StatementType type;
    private List<JsonObject> sections = new ArrayList<>();

    public Builder(StatementType type) {
      this.type = type;
    }

    public Config build() {
      final String jsonExtension = ".json";
      final String fileNamePrefix = type.getFileType().getTypeName();  // e.g. "excel"
      final String configFileName = fileNamePrefix.toLowerCase() + STATEMENT_CONFIG + jsonExtension;
      final String resourcePath = "dev/shantanu/bankstatement/" + configFileName;

      try (var inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
        if (inputStream == null) {
          throw new IllegalStateException("Configuration file not found: " + resourcePath);
        }

        JsonObject root = JsonParser.parseReader(new InputStreamReader(inputStream)).getAsJsonObject();
        JsonObject statementRoot = root.getAsJsonObject(type.getConfigRoot());
        JsonArray sectionArray = statementRoot.getAsJsonArray("sections");

        sections = sectionArray
          .asList()
          .stream()
          .map(JsonElement::getAsJsonObject)
          .toList();

        List<String> titles = sections.stream()
          .map(jo -> jo.get("title").getAsString())
          .toList();

        log.info("Loaded configuration for type={} file={} with sections={}",
          type, configFileName, titles);

        return new Config(type, Collections.unmodifiableList(sections));

      } catch (Exception e) {
        log.error("Error reading configuration for type={}. Exception = {}", type, e.getMessage());
        throw new AccountStatementException(ErrorCode.CONFIGURATION_ERROR,
          "Error building configuration", new IllegalStateException());
      }
    }

    public List<JsonObject> getSections() {
      return this.sections;
    }
  }

  // Optional inner model for Section metadata (if you later map JSON → POJO)
  record Section(
    String id,
    String title,
    int order,
    String name,
    Set<String> searchKeywords,
    Optional<SearchRangeConfig> searchRangeConfigOptional) {
  }

}

