package dev.shantanu.bankstatement.config;

import com.google.gson.JsonObject;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


class StatementConfigTest {

  @Test
  void testStatementConfiguration() {
    StatementConfiguration configuration = new IciciSearchStatementConfig();
    List<JsonObject> sections = configuration.getSections();
    Assertions.assertNotNull(sections);
    Assertions.assertTrue(sections.size() > 1);
    Assertions.assertEquals(4, sections.size());
  }
}