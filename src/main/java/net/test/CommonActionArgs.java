package net.test;

import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommonActionArgs {
  public static final String IS_DATE_LOGGED = "isDateLogged";
  public static final String USE_LOG4J = "use_log4j";
  private static Logger logger;

  private boolean useLog4j;
  private final DateTimeFormatter dateTimeFormatter;

  public CommonActionArgs(Map<String, String> parsedActionArgs, DefaultArguments defaults) {
    final String isDateLoggedStr = parsedActionArgs.get(IS_DATE_LOGGED);
    final String useLog4jStr = parsedActionArgs.get(USE_LOG4J);

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

    if (useLog4jStr == null) useLog4j = defaults.useLog4j();
    else useLog4j = Boolean.valueOf(useLog4jStr);

    if (useLog4j) logger = LoggerFactory.getLogger("TraceAgent");
  }

  private String addPrefix(String str) {
    final String prefixed;
    if (dateTimeFormatter == null) {
      prefixed = str;
    } else {
      prefixed = dateTimeFormatter.format(LocalDateTime.now()) + " " + str;
    }
    return prefixed;
  }

  public void printMsg(String msg) {

    if (useLog4j) logger.info(msg);
    else System.out.println(addPrefix(msg));
  }
}
