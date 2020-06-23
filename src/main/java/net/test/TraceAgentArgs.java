package net.test;

import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class TraceAgentArgs implements DefaultArguments {

  private static final String EXTERNAL_ACTION_FILE_PATH = "actionsFile";
  private static final String DATE_TIME_FORMAT = "dateTimeFormat";

  private final String externalActionFilePath;

  private final DateTimeFormatter dateTimeFormatter;
  
  private final Boolean isDateLoggedFlag;

  public TraceAgentArgs(String arguments) {
    Map<String, String> parsedArgs =
      ArgUtils.parseOptionalArgs(
        Arrays.asList(
          EXTERNAL_ACTION_FILE_PATH,
          DATE_TIME_FORMAT,
          CommonActionArgs.IS_DATE_LOGGED),
        arguments);
    this.externalActionFilePath = parsedArgs.get(EXTERNAL_ACTION_FILE_PATH);
    final String dateTimeFormatStr = parsedArgs.get(DATE_TIME_FORMAT);
    if (dateTimeFormatStr == null) {
      dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    } else {
      dateTimeFormatter = DateTimeFormatter.ofPattern(dateTimeFormatStr);
    }
    // parse the common arguments
    this.isDateLoggedFlag = Boolean.valueOf(parsedArgs.get(CommonActionArgs.IS_DATE_LOGGED));
  }

  public String getExternalActionFilePath() {
    return this.externalActionFilePath;
  }
  
  public DateTimeFormatter getDateTimeFormatter() {
    return this.dateTimeFormatter;
  }

  public boolean isDateLogged() {
    return this.isDateLoggedFlag;
  }

}
