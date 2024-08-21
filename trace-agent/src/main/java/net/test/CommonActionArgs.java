package net.test;

import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.Map;

public class CommonActionArgs {
  public static final String IS_DATE_LOGGED = "isDateLogged";

  public static final String IS_THREADNAME_LOGGED = "isThreadnameLogged";

  private final DateTimeFormatter dateTimeFormatter;

  private final boolean threadnameLogged;

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
    final String isThreadNameLoggedStr = parsedActionArgs.get(IS_THREADNAME_LOGGED);
    if (isThreadNameLoggedStr == null) {
      threadnameLogged = defaults.isThreadNameLogged();
    } else {
      threadnameLogged = Boolean.valueOf(isThreadNameLoggedStr);
    }
  }

  public String addPrefix(String str) {
    StringBuilder prefixed = new StringBuilder();
    if (dateTimeFormatter != null) {
      prefixed.append(dateTimeFormatter.format(LocalDateTime.now())).append(" ");
    }
    if (threadnameLogged) {
      prefixed.append("[").append(Thread.currentThread().getName()).append("] ");
    }
    prefixed.append(str);
    return prefixed.toString();
  }
}
