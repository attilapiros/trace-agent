package net.test;

import net.test.interceptor.*;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.implementation.MethodDelegation;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.lang.instrument.Instrumentation;

import static net.bytebuddy.matcher.ElementMatchers.named;

public class TraceAgent {

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
            final String className = actionWithArgs[1];
            final String methodName = actionWithArgs[2];

            if (action.equals("elapsed_time_in_nano")) {
              interceptor = new TimingInterceptorNano();
            } else if (action.equals("elapsed_time_in_ms")) {
              interceptor = new TimingInterceptorMs();
            } else if (action.equals("stack_trace")) {
              interceptor = new StackTraceInterceptor();
            } else if (action.equals("trace_args")) {
              interceptor = new TraceArgsInterceptor();
            } else {
              interceptor = null;
            }
            if (interceptor != null) {
              new AgentBuilder.Default()
                .type(named(className))
                .transform((builder, type, classLoader, module) ->
                    builder.method(named(methodName))
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
}
