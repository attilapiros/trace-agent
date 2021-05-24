package net.test;

import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.Map;

public class CommonActionArgs {
  public static final String IS_DATE_LOGGED = "isDateLogged";

  private final DateTimeFormatter dateTimeFormatter;

  public CommonActionArgs(Map<String, String> parsedActionArgs, DefaultArguments defaults) {
    final String isDateLoggedStr = parsedActionArgs.get(IS_DATE_LOGGED);
    if (isDateLoggedStr == null) {
      if (defaults.isDateLogged()) {
        dateTimeFormatter = defaults.getDateTimeFormatter();
      } else {
        dateTimeFormatter = null;
      }
    } else {
      if (Boolean.valueOf(isDateLoggedStr)) {
        dateTimeFormatter = defaults.getDateTimeFormatter();
      } else {
        dateTimeFormatter = null;
      }
    }
  }

  public String addPrefix(String str) {
    final String prefixed;
    if (dateTimeFormatter == null) {
      prefixed = str;
    } else {
      prefixed = dateTimeFormatter.format(LocalDateTime.now()) + " " + str;
    }
    return prefixed;
  }
}
