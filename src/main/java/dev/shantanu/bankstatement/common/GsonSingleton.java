package dev.shantanu.bankstatement.common;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.time.LocalDate;

public enum GsonSingleton {
  GSON;

  private final Gson gson = (new GsonBuilder()).registerTypeAdapter(LocalDate.class, new LocalDateAdapter()).create();

  private GsonSingleton() {
  }

  public Gson instance() {
    return this.gson;
  }
}
