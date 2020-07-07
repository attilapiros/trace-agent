package net.test.interceptor;

import net.test.ArgUtils;
import net.test.ArgumentsCollection;
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

public class TraceRetValueInterceptor {

  private static String LOG_THRESHOLD_MILLISECONDS = "log_threshold_ms";

  private static List<String> KNOWN_ARGS =
      Arrays.asList(CommonActionArgs.IS_DATE_LOGGED, LOG_THRESHOLD_MILLISECONDS);

  private CommonActionArgs commonActionArgs;

  private final long logThresholdMs;

  public TraceRetValueInterceptor(String actionArgs, DefaultArguments defaults) {
    ArgumentsCollection parsed = ArgUtils.parseOptionalArgs(KNOWN_ARGS, actionArgs);
    this.commonActionArgs = new CommonActionArgs(parsed, defaults);
    this.logThresholdMs = parsed.parseLong(LOG_THRESHOLD_MILLISECONDS, 0);
  }

  @RuntimeType
  public Object intercept(
      @Origin Method method, @AllArguments Object[] allArguments, @SuperCall Callable<?> callable)
      throws Exception {
    long start = (this.logThresholdMs == 0) ? 0 : System.currentTimeMillis();
    Object retVal = callable.call();
    long end = (this.logThresholdMs == 0) ? 0 : System.currentTimeMillis();
    if (this.logThresholdMs == 0 || end - start >= this.logThresholdMs) {
      System.out.println(
          commonActionArgs.addPrefix(
              "TraceAgent (trace_retval): `" + method + " returns with " + retVal));
    }
    return retVal;
  }
}
