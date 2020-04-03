package net.test.interceptor;

import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;

import java.util.function.*;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.Callable;

public class TraceRetValueInterceptor {

  @RuntimeType
  public Object intercept(@Origin Method method, @AllArguments Object[] allArguments, @SuperCall Callable<?> callable) throws Exception  {
    Object retVal = callable.call();
    System.out.println("TraceAgent (trace_retval): `" + method + " returns with " + retVal);
    return retVal;
  }
}
