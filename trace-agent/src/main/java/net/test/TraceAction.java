package net.test;

import net.test.interceptor.*;

import net.bytebuddy.description.NamedElement;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.*;

class TraceAction {

  private final String actionId;

  private final String classMatcherExp;

  private final String methodMatcherExp;

  private final String actionArgs;

  public TraceAction(String actionId, String classMatcherExp, String methodMatcherExp, String actionArgs) {
    this.actionId = actionId;
    this.classMatcherExp = classMatcherExp;
    this.methodMatcherExp = methodMatcherExp;
    this.actionArgs = actionArgs;
  }

  public TraceAction(String actionId, String classMatcherExp, String methodMatcherExp) {
    this(actionId, classMatcherExp, methodMatcherExp, null);
  }

  @Override
  public String toString() {
    return "{" + "actionId='" + actionId + "', classMatcher='" + classMatcherExp + "', methodMatcher='" + methodMatcherExp + "', actionArgs='" + actionArgs + "'}";
  }

  public Object getActionInterceptor(DefaultArguments defaultArguments) {
    final Object interceptor;
    if (actionId.equals(TimingInterceptorNano.NAME)) {
      interceptor = new TimingInterceptorNano(actionArgs, defaultArguments);
    } else if (actionId.equals(TimingInterceptorMs.NAME)) {
      interceptor = new TimingInterceptorMs(actionArgs, defaultArguments);
    } else if (actionId.equals(StackTraceInterceptor.NAME)) {
      interceptor = new StackTraceInterceptor(actionArgs, defaultArguments);
    } else if (actionId.equals(TraceArgsInterceptor.NAME)) {
      interceptor = new TraceArgsInterceptor(actionArgs, defaultArguments);
    } else if (actionId.equals(TraceRetValueInterceptor.NAME)) {
      interceptor = new TraceRetValueInterceptor(actionArgs, defaultArguments);
    } else if (actionId.equals(CounterInterceptor.NAME)) {
      interceptor = new CounterInterceptor(actionArgs, defaultArguments);
    } else if (actionId.equals(AvgTimingInterceptorMs.NAME)) {
      interceptor = new AvgTimingInterceptorMs(actionArgs, defaultArguments);
    } else if (actionId.equals(TraceLoginConfigInterceptor.NAME)) {
      interceptor = new TraceLoginConfigInterceptor(actionArgs, defaultArguments);
    } else if (actionId.equals(TraceArgsWithMethodCallInterceptor.NAME)) {
      interceptor = new TraceArgsWithMethodCallInterceptor(actionArgs, defaultArguments);
    } else if (actionId.equals(DiagnosticCommandInterceptor.NAME)) {
      interceptor = new DiagnosticCommandInterceptor(actionArgs, defaultArguments);
    } else {
      System.err.println("TraceAgent detected an invalid action: " + actionId);
      interceptor = null;
    }
    return interceptor;
  }

  public <T extends NamedElement> ElementMatcher.Junction<T> getClassMatcher() {
    return toMatcher(classMatcherExp);
  }

  public <T extends NamedElement> ElementMatcher.Junction<T> getMethodMatcher() {
    return toMatcher(methodMatcherExp);
  }

  private <T extends NamedElement> ElementMatcher.Junction<T> toMatcher(String inputExpression) {
    final ElementMatcher.Junction<T> res;
    int i = inputExpression.indexOf('(');
    if (i == -1) {
      res = named(inputExpression);
    } else {
      String matchFn = inputExpression.substring(0, i);
      String pattern = inputExpression.substring(i + 1, inputExpression.length() - 1);
      if (matchFn.equals("REGEXP")) {
        res = nameMatches(pattern);
      } else {
        res = named(pattern);
      }
    }
    return res;
  }
}
