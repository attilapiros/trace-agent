package net.test;

import java.io.PrintStream;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Map;

public class TraceAgentArgs implements DefaultArguments {

  private static final String EXTERNAL_ACTION_FILE_PATH = "actionsFile";
  private static final String ENABLE_AGENT_LOG = "enableAgentLog";
  private static final String DATE_TIME_FORMAT = "dateTimeFormat";
  private static final String TARGET_STREAM = "targetStream";

  private final String externalActionFilePath;

  private final Boolean enableAgentLog;

  private final DateTimeFormatter dateTimeFormatter;

  private final Boolean isDateLoggedFlag;

  private final PrintStream targetStream;

  public TraceAgentArgs(String arguments) {
    Map<String, String> parsedArgs =
        ArgUtils.parseOptionalArgs(Arrays.asList(EXTERNAL_ACTION_FILE_PATH, ENABLE_AGENT_LOG, DATE_TIME_FORMAT, CommonActionArgs.IS_DATE_LOGGED, TARGET_STREAM), arguments);
    this.externalActionFilePath = parsedArgs.get(EXTERNAL_ACTION_FILE_PATH);
    if (parsedArgs.getOrDefault(TARGET_STREAM, "stdout").equals("stderr")) {
      this.targetStream = System.err;
    } else {
      this.targetStream = System.out;
    }
    this.enableAgentLog = Boolean.valueOf(parsedArgs.get(ENABLE_AGENT_LOG));

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

  public boolean isAgentLogEnabled() {
    return this.enableAgentLog;
  }

  public DateTimeFormatter getDateTimeFormatter() {
    return this.dateTimeFormatter;
  }

  public boolean isDateLogged() {
    return this.isDateLoggedFlag;
  }

  public PrintStream getTargetStream() {
    return targetStream;
  }
}
