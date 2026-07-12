package net.test;

import java.time.format.DateTimeFormatter;

/** Simple DefaultArguments implementation used during offline JAR instrumentation. */
public class OfflineDefaultArguments implements DefaultArguments {

  public static final OfflineDefaultArguments INSTANCE = new OfflineDefaultArguments();

  @Override
  public DateTimeFormatter getDateTimeFormatter() {
    return DateTimeFormatter.ISO_LOCAL_DATE_TIME;
  }

  @Override
  public boolean isDateLogged() {
    return false;
  }
}
