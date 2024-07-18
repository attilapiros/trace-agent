import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.FrameworkMethod;
import org.junit.rules.TestWatchman;
import org.junit.rules.MethodRule;

import java.io.*;
import java.util.Arrays;
import java.util.stream.*;
import java.util.function.Supplier;

import static org.junit.Assert.*;

public class TestTraceAgent {

  private static final String ACTION_FILE_NAME = "actions.txt";

  private static CommandLine cmd =
      CommandLine.parse(
          "java -XX:NativeMemoryTracking=detail -javaagent:../../trace-agent/target/trace-agent-1.0-SNAPSHOT.jar=\"actionsFile:./"
              + ACTION_FILE_NAME
              + "\" "
              + "-jar ../sampleApp/target/sampleApp-1.0-SNAPSHOT.jar");

  private static CommandLine cmdWithThreadNameArgs =
      CommandLine.parse(
          "java -XX:NativeMemoryTracking=detail -javaagent:../../trace-agent/target/trace-agent-1.0-SNAPSHOT.jar=\"isThreadnameLogged:true,actionsFile:./"
              + ACTION_FILE_NAME
              + "\" "
              + "-jar ../sampleApp/target/sampleApp-1.0-SNAPSHOT.jar");

  private String runTraceAgent(String... actions) throws IOException {
    return runTraceAgent(false, actions);
  }

  private String runTraceAgent(boolean isThreadNameLogged, String... actions) throws IOException {
    String res = "";
    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      DefaultExecutor executor = new DefaultExecutor();
      executor.setStreamHandler(new PumpStreamHandler(outputStream));
      File actionFile = new File(ACTION_FILE_NAME);
      try (FileWriter actionWriter = new FileWriter(actionFile)) {
        for (String a : actions) {
          actionWriter.append(a + "\n");
        }
        actionWriter.close();
        if (isThreadNameLogged) {
          executor.execute(cmdWithThreadNameArgs);
        } else {
          executor.execute(cmd);
        }
      } finally {
        actionFile.delete();
      }
      res = outputStream.toString();
    }
    return res;
  }

  @Rule
  public MethodRule watchman =
      new TestWatchman() {
        public void starting(FrameworkMethod method) {
          System.out.println("=== BEGIN " + method.getName());
        }

        public void finished(FrameworkMethod method) {
          System.out.println("==== END " + method.getName());
        }
      };

  private Supplier<Stream<String>> toStreamSupplier(String output) {
    System.out.println(output);
    String lines[] = output.split("\n");
    return () -> Arrays.stream(lines);
  }

  @Test
  public void testStackTrace() throws IOException {
    String output = runTraceAgent("stack_trace net.test.TestClass2nd anotherMethod");
    Supplier<Stream<String>> streamSupplier = toStreamSupplier(output);
    assertTrue(streamSupplier.get().anyMatch(s -> s.contains("TraceAgent (stack trace)")));
    assertTrue(streamSupplier.get().anyMatch(s -> s.contains("at net.test.TestClass2nd.anotherMethod(SampleApp.java)")));
    assertTrue(streamSupplier.get().anyMatch(s -> s.contains("at net.test.SampleApp.main(SampleApp.java")));
  }

  @Test
  public void testTraceArgs() throws IOException {
    String output = runTraceAgent("trace_args net.test.TestClass2nd methodWithArgs");
    Supplier<Stream<String>> streamSupplier = toStreamSupplier(output);
    assertTrue(
        streamSupplier
            .get()
            .anyMatch(s -> s.contains("TraceAgent (trace_args pre): `public int net.test.TestClass2nd.methodWithArgs(java.lang.String,int) called with [secret, 42]")));
  }

  @Test
  public void testTraceRetVal() throws IOException {
    String output = runTraceAgent("trace_retval net.test.TestClass2nd methodWithArgs");
    Supplier<Stream<String>> streamSupplier = toStreamSupplier(output);
    assertTrue(streamSupplier.get().anyMatch(s -> s.contains("TraceAgent (trace_retval): `public int net.test.TestClass2nd.methodWithArgs(java.lang.String,int) returns with 12")));
  }

  @Test
  public void testElapsedTime() throws IOException {
    String output = runTraceAgent("elapsed_time_in_nano net.test.TestClass test", "elapsed_time_in_ms net.test.TestClass2nd anotherMethod");
    Supplier<Stream<String>> streamSupplier = toStreamSupplier(output);
    assertTrue(streamSupplier.get().anyMatch(s -> s.matches("TraceAgent \\(timing\\): `public void net.test.TestClass.test\\(\\)` took [0-9]+ nano")));
    assertTrue(streamSupplier.get().anyMatch(s -> s.matches("TraceAgent \\(timing\\): `public void net.test.TestClass2nd.anotherMethod\\(\\)` took [0-9]+ ms")));
  }

  @Test
  public void testMultipleActionsOnTheSameMethod() throws IOException {
    String output = runTraceAgent("elapsed_time_in_nano net.test.TestClass test", "elapsed_time_in_ms net.test.TestClass test");
    Supplier<Stream<String>> streamSupplier = toStreamSupplier(output);
    assertTrue(streamSupplier.get().anyMatch(s -> s.matches("TraceAgent \\(timing\\): `public void net.test.TestClass.test\\(\\)` took [0-9]+ nano")));
    assertTrue(streamSupplier.get().anyMatch(s -> s.matches("TraceAgent \\(timing\\): `public void net.test.TestClass.test\\(\\)` took [0-9]+ ms")));
  }

  @Test
  public void testCounter() throws IOException {
    String output = runTraceAgent("counter net.test.TestClass2nd anotherMethod count_frequency:1");
    Supplier<Stream<String>> streamSupplier = toStreamSupplier(output);
    assertTrue(streamSupplier.get().anyMatch(s -> s.matches("TraceAgent \\(counter\\):(.)*anotherMethod(.)*called 1(.|\\s)*")));
  }

  @Test
  public void testDiagnosticCommandBeforeAndAfter() throws IOException {
    String output = runTraceAgent("diagnostic_command net.test.TestClass2nd anotherMethod cmd:gcClassHistogram,limit_output_lines:5,where:beforeAndAfter");
    Supplier<Stream<String>> streamSupplier = toStreamSupplier(output);
    assertTrue(
        streamSupplier
            .get()
            .anyMatch(s -> s.contains("TraceAgent (diagnostic_command / gcClassHistogram): at the beginning of `public void net.test.TestClass2nd.anotherMethod()`:")));
    assertTrue(
        streamSupplier.get().anyMatch(s -> s.contains("TraceAgent (diagnostic_command / gcClassHistogram): at the end of `public void net.test.TestClass2nd.anotherMethod()`:")));
    assertEquals(2, streamSupplier.get().filter(s -> s.contains(" num     #instances         #bytes  class name")).count());
  }

  @Test
  public void testDiagnosticCommandBefore() throws IOException {
    String output = runTraceAgent("diagnostic_command net.test.TestClass2nd anotherMethod cmd:gcClassHistogram,limit_output_lines:5,where:before");
    Supplier<Stream<String>> streamSupplier = toStreamSupplier(output);
    assertTrue(
        streamSupplier
            .get()
            .anyMatch(s -> s.contains("TraceAgent (diagnostic_command / gcClassHistogram): at the beginning of `public void net.test.TestClass2nd.anotherMethod()`:")));
    assertEquals(1, streamSupplier.get().filter(s -> s.contains(" num     #instances         #bytes  class name")).count());
  }

  @Test
  public void testDiagnosticCommandAfter() throws IOException {
    String output = runTraceAgent("diagnostic_command net.test.TestClass2nd anotherMethod cmd:gcClassHistogram,limit_output_lines:5,where:after");
    Supplier<Stream<String>> streamSupplier = toStreamSupplier(output);
    assertTrue(
        streamSupplier.get().anyMatch(s -> s.contains("TraceAgent (diagnostic_command / gcClassHistogram): at the end of `public void net.test.TestClass2nd.anotherMethod()`:")));
    assertEquals(1, streamSupplier.get().filter(s -> s.contains(" num     #instances         #bytes  class name")).count());
  }

  @Test
  public void testDiagnosticCommandThreadPrint() throws IOException {
    String output = runTraceAgent("diagnostic_command net.test.TestClass2nd anotherMethod cmd:threadPrint,limit_output_lines:15");
    Supplier<Stream<String>> streamSupplier = toStreamSupplier(output);
    assertTrue(
        streamSupplier.get().anyMatch(s -> s.equals("TraceAgent (diagnostic_command / threadPrint): at the beginning of `public void net.test.TestClass2nd.anotherMethod()`:")));
    assertTrue(streamSupplier.get().anyMatch(s -> s.contains("Full thread dump")));
  }

  @Test
  public void testDiagnosticCommandNativeMemory() throws IOException {
    String output = runTraceAgent("diagnostic_command net.test.TestClass2nd anotherMethod cmd:vmNativeMemory");
    Supplier<Stream<String>> streamSupplier = toStreamSupplier(output);
    assertTrue(
        streamSupplier.get().anyMatch(s -> s.equals("TraceAgent (diagnostic_command / vmNativeMemory): at the beginning of `public void net.test.TestClass2nd.anotherMethod()`:")));
    assertTrue(streamSupplier.get().anyMatch(s -> s.equals("Native Memory Tracking:")));
  }

  @Test
  public void testTraceArgsWithThreadNameAsJVMArg() throws IOException {
    String output = runTraceAgent(true, "trace_args net.test.TestClass2nd methodWithArgs");
    Supplier<Stream<String>> streamSupplier = toStreamSupplier(output);
    assertTrue(
        streamSupplier
            .get()
            .anyMatch(s -> s.contains("[main] TraceAgent (trace_args pre): `public int net.test.TestClass2nd.methodWithArgs(java.lang.String,int) called with [secret, 42]")));
  }

  @Test
  public void testTraceArgsWithThreadNameAsActionArg() throws IOException {
    String output = runTraceAgent("trace_args net.test.TestClass2nd methodWithArgs isThreadnameLogged:true");
    Supplier<Stream<String>> streamSupplier = toStreamSupplier(output);
    assertTrue(
        streamSupplier
            .get()
            .anyMatch(s -> s.contains("[main] TraceAgent (trace_args pre): `public int net.test.TestClass2nd.methodWithArgs(java.lang.String,int) called with [secret, 42]")));
  }
}
