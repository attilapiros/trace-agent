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

public class TraceRetValueInterceptor {

  private static List<String> KNOWN_ARGS = 
    Arrays.asList(CommonActionArgs.IS_DATE_LOGGED);

  private CommonActionArgs commonActionArgs;

  public TraceRetValueInterceptor(String actionArgs, DefaultArguments defaults) {
    Map<String, String> parsed = ArgUtils.parseOptionalArgs(KNOWN_ARGS, actionArgs);
    this.commonActionArgs = new CommonActionArgs(parsed, defaults);
  }


  @RuntimeType
  public Object intercept(@Origin Method method, @AllArguments Object[] allArguments, @SuperCall Callable<?> callable) throws Exception  {
    Object retVal = callable.call();
    System.out.println(
      commonActionArgs.addPrefix("TraceAgent (trace_retval): `" + method + " returns with " + retVal));
    return retVal;
  }
}
