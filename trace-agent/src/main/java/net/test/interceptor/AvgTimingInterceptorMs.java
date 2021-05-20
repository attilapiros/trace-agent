package net.test.interceptor;

import net.test.ArgUtils;
import net.test.ArgumentsCollection;
import net.test.CommonActionArgs;
import net.test.DefaultArguments;

import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

public class AvgTimingInterceptorMs {

  public static String NAME = "avg_timing";

  private static String WINDOW_LENGTH = "window_length";

  private static List<String> KNOWN_ARGS =
      Arrays.asList(CommonActionArgs.IS_DATE_LOGGED, WINDOW_LENGTH);

  private CommonActionArgs commonActionArgs;

  private final int window_length;

  private volatile long window_min = 0;
  private volatile long window_max = 0;
  private volatile long window_sum = 0;
  private volatile int window_index = 0;

  public AvgTimingInterceptorMs(String actionArgs, DefaultArguments defaults) {
    ArgumentsCollection parsed = ArgUtils.parseOptionalArgs(KNOWN_ARGS, actionArgs);
    this.commonActionArgs = new CommonActionArgs(parsed, defaults);
    this.window_length = parsed.parseInt(WINDOW_LENGTH, 100);
  }

  @RuntimeType
  public Object intercept(@Origin Method method, @SuperCall Callable<?> callable) throws Exception {
    long start = System.currentTimeMillis();
    try {
      return callable.call();
    } finally {
      long end = System.currentTimeMillis();
      final long elapsedTime = end - start;
      window_sum += elapsedTime;
      window_index++;
      if (this.window_index == 1) {
        window_min = elapsedTime;
        window_max = elapsedTime;
      } else if (elapsedTime < this.window_min) {
        window_min = elapsedTime;
      } else if (elapsedTime > this.window_max) {
        window_max = elapsedTime;
      }
      if (window_index == window_length) {
        System.out.println(
            commonActionArgs.addPrefix(
                "TraceAgent ("
                    + NAME
                    + "): `"
                    + method
                    + "` window_length: "
                    + window_length
                    + " min: "
                    + window_min
                    + " avg: "
                    + window_sum / window_length
                    + " max: "
                    + window_max));
        window_index = 0;
        window_sum = 0;
      }
    }
  }
}
