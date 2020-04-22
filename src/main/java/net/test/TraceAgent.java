package net.test;

import net.test.interceptor.*;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.matcher.ElementMatcher;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import static net.bytebuddy.matcher.ElementMatchers.*;

public class TraceAgent {

  public static <T extends NamedElement> ElementMatcher.Junction<T> toMatcher(String inputExpression) {
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

  public static void premain(String arguments, Instrumentation instrumentation) {
    try {
      try(BufferedReader buffReader =
          new BufferedReader(
            new InputStreamReader(
              TraceAgent.class.getResourceAsStream("/actions.txt")))) {
        String line = null;
        while ((line = buffReader.readLine()) != null) {
          String[] actionWithArgs = line.split("\\s+");
          final Object interceptor;
          if (actionWithArgs.length == 3) {
            final String action = actionWithArgs[0];
            final String classMatcherExp = actionWithArgs[1];
            final String methodMatcherExp= actionWithArgs[2];

            if (action.equals("elapsed_time_in_nano")) {
              interceptor = new TimingInterceptorNano();
            } else if (action.equals("elapsed_time_in_ms")) {
              interceptor = new TimingInterceptorMs();
            } else if (action.equals("stack_trace")) {
              interceptor = new StackTraceInterceptor();
            } else if (action.equals("trace_args")) {
              interceptor = new TraceArgsInterceptor();
            } else if (action.equals("trace_retval")) {
              interceptor = new TraceRetValueInterceptor();
            } else {
              interceptor = null;
            }
            if (interceptor != null) {
              new AgentBuilder.Default()
                .type(toMatcher(classMatcherExp))
                .transform((builder, type, classLoader, module) ->
                    builder.method(toMatcher(methodMatcherExp))
                    .intercept(MethodDelegation.to(interceptor)))
                .installOn(instrumentation);
            }
          } else {
            System.err.println("TraceAgent skips the rule: " + line);
          }
        }
      }
    } catch (IOException e) {
      e.printStackTrace(System.err);
    }
  }

  public static String getTimeStamp() {
    return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(Calendar.getInstance().getTime());
  }
}
