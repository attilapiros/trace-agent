package net.test.interceptor;

import net.test.ArgUtils;
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

  private static List<String> KNOWN_ARGS = 
    Arrays.asList(CommonActionArgs.IS_DATE_LOGGED);

  private CommonActionArgs commonActionArgs;

  public StackTraceInterceptor(String actionArgs, DefaultArguments defaults) {
    Map<String, String> parsed = ArgUtils.parseOptionalArgs(KNOWN_ARGS, actionArgs);
    this.commonActionArgs = new CommonActionArgs(parsed, defaults);
  }

  @RuntimeType
  public Object intercept(@Origin Method method, @SuperCall Callable<?> callable) throws Exception  {
    Exception e = new MyException(commonActionArgs.addPrefix("TraceAgent (stack trace)"));
    StackTraceElement[] stElements = Thread.currentThread().getStackTrace();
    e.setStackTrace(Arrays.copyOfRange(stElements, 2, stElements.length));
    e.printStackTrace(System.out);
    return callable.call();
  }
}
