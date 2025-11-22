package dev.shantanu.bankstatement.common;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LocalDateAdapter implements JsonSerializer<LocalDate>, JsonDeserializer<LocalDate> {
  private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
  private static final DateTimeFormatter RAW_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private static final Logger logger = LoggerFactory.getLogger(LocalDateAdapter.class);

  @Override
  public JsonElement serialize(LocalDate src, Type typeOfSrc, JsonSerializationContext context) {
    try {
      return new JsonPrimitive(src.format(FORMATTER));
    } catch (Exception exception) {
      logger.error("Exception while parsing source date = {}. Exception = {} ", src, exception.getMessage());
    }
    return null;
  }

  @Override
  public LocalDate deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
    try {
      return LocalDate.parse(json.getAsString(), FORMATTER);
    } catch (Exception _) {
      try {
        return LocalDate.parse(json.getAsString(), RAW_FORMATTER);
      } catch (Exception exception) {
        logger.error("Exception while parsing source json = {}, exception is {}", json, exception.getMessage());
      }
    }
    return null;
  }

  static void main() {
    Gson gson = new GsonBuilder()
      .registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
      .create();

    LocalDate date = LocalDate.of(2025, 11, 13);
    String json = gson.toJson(date);
    logger.info("Serialized LocalDate: {}", json);

    LocalDate deserializedDate = gson.fromJson(json, LocalDate.class);
    logger.info("Deserialized LocalDate: {}", deserializedDate);
  }
}