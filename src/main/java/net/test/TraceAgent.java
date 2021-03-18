package net.test;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.implementation.MethodDelegation;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class TraceAgent {

  private TraceAgentArgs traceAgentArgs;

  private Instrumentation instrumentation;

  private TraceAction readAction(String line) {
    String[] actionWithArgs = line.split("\\s+");
    final TraceAction traceAction;
    if (actionWithArgs.length == 4) {
      traceAction =
          new TraceAction(
              actionWithArgs[0], actionWithArgs[1], actionWithArgs[2], actionWithArgs[3]);
    } else if (actionWithArgs.length == 3) {
      traceAction = new TraceAction(actionWithArgs[0], actionWithArgs[1], actionWithArgs[2]);
    } else {
      traceAction = null;
    }
    return traceAction;
  }

  private static boolean isBlank(String line) {
    char[] chars = line.toCharArray();
    for (char c : chars) {
      if (c != ' ' && c != '\t') {
        return false;
      }
    }
    return true;
  }

  private static boolean isComment(String line) {
    return line.charAt(0) == '#';
  }

  private List<TraceAction> readActions(InputStream in) {
    List<TraceAction> actions = new ArrayList<TraceAction>();
    try {
      try (BufferedReader buffReader = new BufferedReader(new InputStreamReader(in))) {
        String line = null;
        while ((line = buffReader.readLine()) != null) {
          // blank lines and comments are skipped
          if (!isBlank(line) && !isComment(line)) {
            TraceAction traceAction = readAction(line);
            if (traceAction == null) {
              System.err.println("TraceAgent skips the rule: " + line);
            } else {
              actions.add(traceAction);
            }
          }
        }
      }
    } catch (IOException e) {
      e.printStackTrace(System.err);
    }
    return actions;
  }

  private void installActions(List<TraceAction> actions) {
    System.out.println("TraceAgent tries to install actions: " + actions);
    for (TraceAction action : actions) {
      final Object interceptor = action.getActionInterceptor(traceAgentArgs);
      if (interceptor != null) {
        new AgentBuilder.Default()
            .with(new AgentBuilder.InitializationStrategy.SelfInjection.Eager())
            .type(action.getClassMatcher())
            .transform(
                (builder, type, classLoader, module) ->
                    builder
                        .method(action.getMethodMatcher())
                        .intercept(MethodDelegation.to(interceptor)))
            .installOn(instrumentation);
      }
    }
    System.out.println("TraceAgent installed actions successfully");
  }

  private void install() {
    List<TraceAction> allActions =
        readActions(TraceAgent.class.getResourceAsStream("/actions.txt"));
    String externalActionFile = traceAgentArgs.getExternalActionFilePath();
    if (externalActionFile != null) {
      try {
        allActions.addAll(readActions(new FileInputStream(externalActionFile)));
      } catch (FileNotFoundException fnf) {
        System.err.println(
            "TraceAgent does not find the external action file: " + externalActionFile);
      }
    }
    installActions(allActions);
  }

  private TraceAgent(TraceAgentArgs traceAgentArgs, Instrumentation instrumentation) {
    this.traceAgentArgs = traceAgentArgs;
    this.instrumentation = instrumentation;
  }

  public static void premain(String arguments, Instrumentation instrumentation) {
    System.out.println("TraceAgent is initializing");
    TraceAgentArgs traceAgentArgs = new TraceAgentArgs(arguments);
    TraceAgent traceAgent = new TraceAgent(traceAgentArgs, instrumentation);
    traceAgent.install();
  }

  public static void agentmain(String arguments, Instrumentation instrumentation) {
    premain(arguments, instrumentation);
  }
}
