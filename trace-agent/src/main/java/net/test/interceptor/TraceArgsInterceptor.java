package net.test.interceptor;

import net.test.ArgUtils;
import net.test.ArgumentsCollection;
import net.test.CommonActionArgs;
import net.test.DefaultArguments;
import net.test.GlobalArguments;

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

public class TraceArgsInterceptor {

  public static String NAME = "trace_args";

  private static String LOG_THRESHOLD_MILLISECONDS = "log_threshold_ms";

  private static List<String> KNOWN_ARGS = Arrays.asList(CommonActionArgs.IS_DATE_LOGGED, LOG_THRESHOLD_MILLISECONDS);

  private CommonActionArgs commonActionArgs;

  private final long logThresholdMs;

  private final GlobalArguments globalArguments;

  public TraceArgsInterceptor(GlobalArguments globalArguments, String actionArgs, DefaultArguments defaults) {
    ArgumentsCollection parsed = ArgUtils.parseOptionalArgs(KNOWN_ARGS, actionArgs);
    this.commonActionArgs = new CommonActionArgs(parsed, defaults);
    this.logThresholdMs = parsed.parseLong(LOG_THRESHOLD_MILLISECONDS, 0);
    this.globalArguments = globalArguments;
  }

  @RuntimeType
  public Object intercept(@Origin Method method, @AllArguments Object[] allArguments, @SuperCall Callable<?> callable) throws Exception {
    long start = (this.logThresholdMs == 0) ? 0 : System.currentTimeMillis();
    try {
      return callable.call();
    } finally {
      long end = (this.logThresholdMs == 0) ? 0 : System.currentTimeMillis();
      if (this.logThresholdMs == 0 || end - start >= this.logThresholdMs) {
        globalArguments.getTargetStream().println(commonActionArgs.addPrefix("TraceAgent (trace_args): `" + method + " called with " + Arrays.toString(allArguments)));
      }
    }
  }
}
