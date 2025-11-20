package dev.shantanu.bankstatement.config;

import com.google.gson.JsonObject;
import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class JacksonStatementConfiguration implements StatementConfiguration {
  private StatementType statementType;

  private JacksonStatementConfiguration(StatementType statementType) {
    this.statementType = statementType;
  }

  static StatementConfiguration buildJacksonConfiguration(StatementType statementType) {
    return new JacksonStatementConfiguration(statementType);
  }

  public StatementConfiguration.Builder builder(StatementType type) {
    return StatementConfiguration.builder(type);
  }

  public JsonNode getRootJsonNode() throws URISyntaxException {
    String configFileName = this.statementType.getFileType().getTypeName() + "StatementConfig.json";
    String fullPath = "dev/shantanu/bankstatement/" + configFileName;
    URL configFileUrl = URLClassLoader.getSystemClassLoader().getResource(fullPath);
    File configFile = new File(configFileUrl.toURI());
    JsonNode jsonNode = getJsonObjectFrom(configFile, this.statementType.getConfigRoot());
    JsonNode sectionsJsonNode = jsonNode.get("sections");

    for (JsonNode section : sectionsJsonNode) {
      String id = section.get("id").asString();
      String title = section.get("title").asString();
      Set<String> searchKeywords = (Set) section.get("searchKeywords").valueStream().map(JsonNode::asString).collect(Collectors.toSet());
      boolean skip = section.has("skip") && section.get("skip").asBoolean();
      System.out.printf("Section id = %s, title = %s, skip = %b, searchKeywords = %s %n", id, title, skip, searchKeywords);
    }

    return sectionsJsonNode;
  }

  public StatementType getStatementType() {
    return this.statementType;
  }

  @Override
  public StatementType statementType() {
    return this.statementType;
  }

  public List<JsonObject> getSections() {
    return List.of();
  }

  private static JsonNode getJsonObjectFrom(File configFile, String configRoot) {
    ObjectMapper objectMapper = new ObjectMapper();
    return objectMapper.readTree(configFile).get(configRoot);
  }
}
