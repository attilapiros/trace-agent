package net.test;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * CLI entry point for offline JAR instrumentation.
 *
 * <p>Usage: java -jar trace-agent.jar --input <app.jar> --output <instrumented.jar> --actions <actions.txt> [--dateTimeFormat <pattern>] [--targetStream stdout|stderr]
 * [--isDateLogged true|false]
 */
public class TraceAgentCli {

  private static final String OPT_INPUT = "--input";
  private static final String OPT_OUTPUT = "--output";
  private static final String OPT_ACTIONS = "--actions";
  private static final String OPT_DATE_FORMAT = "--dateTimeFormat";
  private static final String OPT_TARGET_STREAM = "--targetStream";
  private static final String OPT_IS_DATE_LOGGED = "--isDateLogged";

  public static void main(String[] args) throws Exception {
    String inputJar = null;
    String outputJar = null;
    String actionsFile = null;
    String dateTimeFormat = null;
    String targetStream = null;
    String isDateLogged = null;

    for (int i = 0; i < args.length - 1; i++) {
      switch (args[i]) {
        case OPT_INPUT:
          inputJar = args[++i];
          break;
        case OPT_OUTPUT:
          outputJar = args[++i];
          break;
        case OPT_ACTIONS:
          actionsFile = args[++i];
          break;
        case OPT_DATE_FORMAT:
          dateTimeFormat = args[++i];
          break;
        case OPT_TARGET_STREAM:
          targetStream = args[++i];
          break;
        case OPT_IS_DATE_LOGGED:
          isDateLogged = args[++i];
          break;
        default:
          System.err.println("Unknown option: " + args[i]);
          printUsageAndExit();
      }
    }

    if (inputJar == null || outputJar == null || actionsFile == null) {
      System.err.println("Missing required argument(s).");
      printUsageAndExit();
    }

    // Build a TraceAgentArgs-compatible argument string from CLI flags
    StringBuilder agentArgStr = new StringBuilder();
    if (dateTimeFormat != null) appendArg(agentArgStr, "dateTimeFormat", dateTimeFormat);
    if (targetStream != null) appendArg(agentArgStr, "targetStream", targetStream);
    if (isDateLogged != null) appendArg(agentArgStr, "isDateLogged", isDateLogged);
    TraceAgentArgs traceAgentArgs = new TraceAgentArgs(agentArgStr.length() > 0 ? agentArgStr.toString() : null);

    List<TraceAction> actions = readActions(traceAgentArgs, actionsFile);
    System.out.println("TraceAgentCli loaded " + actions.size() + " action(s)");

    System.out.println("TraceAgentCli transforming: " + inputJar + " -> " + outputJar);
    JarTransformer.transform(actions, traceAgentArgs, inputJar, outputJar);
    System.out.println("TraceAgentCli done: " + outputJar);
  }

  private static List<TraceAction> readActions(TraceAgentArgs traceAgentArgs, String actionsFile) {
    TraceAgent helper = new TraceAgent(traceAgentArgs);
    List<TraceAction> actions = new ArrayList<TraceAction>();

    // Load bundled default (usually empty)
    InputStream bundled = TraceAgent.class.getResourceAsStream("/actions.txt");
    if (bundled != null) {
      actions.addAll(helper.readActions(bundled));
    }

    // Load the user-supplied file
    try {
      actions.addAll(helper.readActions(new FileInputStream(actionsFile)));
    } catch (FileNotFoundException e) {
      System.err.println("TraceAgentCli: actions file not found: " + actionsFile);
      System.exit(1);
    }
    return actions;
  }

  private static void appendArg(StringBuilder sb, String key, String value) {
    if (sb.length() > 0) sb.append(',');
    sb.append(key).append(':').append(value);
  }

  private static void printUsageAndExit() {
    System.err.println("Usage: java -jar trace-agent.jar \\");
    System.err.println("         --input  <app.jar>          (input JAR to instrument)");
    System.err.println("         --output <instrumented.jar> (output JAR path)");
    System.err.println("         --actions <actions.txt>     (actions file)");
    System.err.println("       Optional:");
    System.err.println("         --dateTimeFormat  <pattern>        (e.g. yyyy-MM-dd HH:mm:ss)");
    System.err.println("         --targetStream    stdout|stderr");
    System.err.println("         --isDateLogged    true|false");
    System.exit(1);
  }
}
