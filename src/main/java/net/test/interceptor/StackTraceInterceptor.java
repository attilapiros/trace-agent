package net.test.interceptor;

import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;

import java.util.function.*;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.Callable;

class MyException extends Exception {
  public String toString() {
    return "TraceAgent (stack trace):";
  }
}

public class StackTraceInterceptor {

  public Object intercept(@Origin Method method, @SuperCall Callable<?> callable) throws Exception  {
    Exception e = new MyException();
    StackTraceElement[] stElements = Thread.currentThread().getStackTrace();
    e.setStackTrace(Arrays.copyOfRange(stElements, 2, stElements.length));
    e.printStackTrace(System.out);
    return callable.call();
  }
}
