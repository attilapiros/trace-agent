package net.test.interceptor;

import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;

import java.util.function.*;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;

public class TimingInterceptorMs {

  @RuntimeType
  public Object intercept(@Origin Method method, @SuperCall Callable<?> callable) throws Exception  {
    long start = System.currentTimeMillis();
    try {
      return callable.call();
    } finally {
      System.out.println("TraceAgent (timing): `" + method + "` took " + (System.currentTimeMillis() - start) + " ms");
    }
  }
}
