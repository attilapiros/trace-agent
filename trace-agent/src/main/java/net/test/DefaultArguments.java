package net.test;

import java.time.format.DateTimeFormatter;

public interface DefaultArguments {

  DateTimeFormatter getDateTimeFormatter();

  boolean isDateLogged();

  boolean isThreadNameLogged();
}
