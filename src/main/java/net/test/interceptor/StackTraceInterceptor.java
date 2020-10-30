package net.test.interceptor;

import net.test.ArgUtils;
import net.test.ArgumentsCollection;
import net.test.CommonActionArgs;
import net.test.DefaultArguments;

import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.function.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

class MyException extends Exception {

  private final String prefix;

  public MyException(String prefix) {
    this.prefix = prefix;
  }

  public String toString() {
    return prefix;
  }
}

public class StackTraceInterceptor {

  public static String NAME = "stack_trace";

  private static String LIMIT_COUNT = "limit_count";

  private static String LOG_THRESHOLD_MILLISECONDS = "log_threshold_ms";

  private static List<String> KNOWN_ARGS =
      Arrays.asList(CommonActionArgs.IS_DATE_LOGGED, LOG_THRESHOLD_MILLISECONDS, LIMIT_COUNT);

  private CommonActionArgs commonActionArgs;

  private final long logThresholdMs;

  private final int limitCount;

  private volatile int count = 0;

  public StackTraceInterceptor(String actionArgs, DefaultArguments defaults) {
    ArgumentsCollection parsed = ArgUtils.parseOptionalArgs(KNOWN_ARGS, actionArgs);
    this.commonActionArgs = new CommonActionArgs(parsed, defaults);
    this.logThresholdMs = parsed.parseLong(LOG_THRESHOLD_MILLISECONDS, 0);
    this.limitCount = parsed.parseInt(LIMIT_COUNT, -1);
  }

  @RuntimeType
  public Object intercept(@Origin Method method, @SuperCall Callable<?> callable) throws Exception {
    long start = (this.logThresholdMs == 0) ? 0 : System.currentTimeMillis();
    try {
      return callable.call();
    } finally {
      long end = (this.logThresholdMs == 0) ? 0 : System.currentTimeMillis();
      if ((this.logThresholdMs == 0 || end - start >= this.logThresholdMs)) {

        final boolean doPrint;
        if (limitCount == -1) {
          doPrint = true;
        } else if (count < limitCount) {
          doPrint = true;
          count++;
        } else {
          doPrint = false;
        }

        if (doPrint) {
          Exception e = new MyException(commonActionArgs.addPrefix("TraceAgent (stack trace)"));
          StackTraceElement[] stElements = Thread.currentThread().getStackTrace();
          e.setStackTrace(Arrays.copyOfRange(stElements, 2, stElements.length));
          e.printStackTrace(System.out);
        }
      }
    }
  }
}
