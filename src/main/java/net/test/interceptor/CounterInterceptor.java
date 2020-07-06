package net.test.interceptor;

import net.test.ArgUtils;
import net.test.CommonActionArgs;
import net.test.DefaultArguments;

import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;

import java.util.function.*;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

public class CounterInterceptor {

  private static String COUNT_FREQUENCY = "count_frequency";

  private static List<String> KNOWN_ARGS =
    Arrays.asList(CommonActionArgs.IS_DATE_LOGGED, COUNT_FREQUENCY);

  private CommonActionArgs commonActionArgs;

  private final int countFrequency;

  private long counter = 0;

  public CounterInterceptor(String actionArgs, DefaultArguments defaults) {
    Map<String, String> parsed = ArgUtils.parseOptionalArgs(KNOWN_ARGS, actionArgs);
    this.commonActionArgs = new CommonActionArgs(parsed, defaults);
    String countFrequencyStr = parsed.get(COUNT_FREQUENCY);
    int countFrequencyInt = 100;
    if (countFrequencyStr != null) {
      try {
        countFrequencyInt = Integer.valueOf(countFrequencyStr);
      } catch (NumberFormatException nfe) {
        System.err.println("TraceAgent (counter) invalid `" + COUNT_FREQUENCY + "` param value: `" + countFrequencyStr + "` using the default: " + countFrequencyInt);
      }
    }
    this.countFrequency = countFrequencyInt;
  }


  @RuntimeType
  public Object intercept(@Origin Method method, @AllArguments Object[] allArguments, @SuperCall Callable<?> callable) throws Exception  {
    counter++;
    if (counter % countFrequency == 0) {
      System.out.println(commonActionArgs.addPrefix("TraceAgent (counter): " + counter));
    }
    return callable.call();
  }
}
