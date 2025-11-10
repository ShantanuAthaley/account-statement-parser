package dev.shantanu.bankstatement.config;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface StatementConfiguration {
  Logger log = LoggerFactory.getLogger(StatementConfiguration.class);
  String STATEMENT_CONFIG = "StatementConfig";

  default ConfigurationBuilder builder(StatementType type, StatementConfiguration statementConfiguration) {
    return new ConfigurationBuilder(type);
  }

  default ConfigurationBuilder withDefault() {
    return new ConfigurationBuilder(StatementType.getDefault());
  }

  default List<JsonObject> getSections() {
     return ConfigurationBuilder.sections.stream()
       .map(JsonValue::asJsonObject)
       .toList();
  }

  StatementType getStatementType();

  record ConfigurationBuilder(StatementType type) {
    private static JsonArray sections;

    public void build() {

      final String jsonExtension = ".json";
      final String fileNamePrefix = type.getFileType().getTypeName(); // [excel | pdf] + StatementConfig
      final String configFileName = fileNamePrefix.toLowerCase() + STATEMENT_CONFIG + jsonExtension;
      final String fullPath = "dev.shantanu.bankstatement" + "/" + configFileName;
      final URL configFileUrl = URLClassLoader.getSystemClassLoader().getResource(fullPath);

      try {
        if (Objects.nonNull(configFileUrl)) {
          JsonReader jsonReader = Json.createReader(configFileUrl.openStream());
          JsonObject jsonObject = jsonReader.readObject();
          JsonObject root = jsonObject.getJsonObject(type().getConfigRoot());
          JsonArray sections = root.getJsonArray("sections");
          List<String> sectionsList = sections.stream().map(sectionJson -> sectionJson.asJsonObject().getString("title")).toList();
          log.info("Found configuration file={} with sections={}", configFileName, sectionsList);
          ConfigurationBuilder.sections = sections;
        } else {
          throw new IllegalStateException("Can not read configuration file. Configuration File URL is null for configFileName " + configFileName);
        }
      } catch (IOException e) {
        log.error("Exception while reading configuration file={}", configFileName, e);
        throw new RuntimeException(e);
      }
    }
  }
}

