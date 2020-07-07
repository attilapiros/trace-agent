package net.test;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public interface DefaultArguments {

  DateTimeFormatter getDateTimeFormatter();

  boolean isDateLogged();
}
