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
import java.util.Collections;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
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

  private String runTraceAgent(String... actions) throws IOException {
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
        executor.execute(cmd);
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
  public void testTraceArgsWithArrayArg() throws IOException {
    String output = runTraceAgent("trace_args net.test.TestClass2nd methodWithArrayArg");
    Supplier<Stream<String>> streamSupplier = toStreamSupplier(output);
    assertTrue(
        streamSupplier
            .get()
            .anyMatch(s -> s.contains("TraceAgent (trace_args pre): `public void net.test.TestClass2nd.methodWithArrayArg(java.lang.String[]) called with [[foo, bar]]")));
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
    assertTrue(streamSupplier.get().anyMatch(s -> s.trim().matches("TraceAgent \\(timing\\): `public void net.test.TestClass.test\\(\\)` took [0-9]+ nano")));
    assertTrue(streamSupplier.get().anyMatch(s -> s.trim().matches("TraceAgent \\(timing\\): `public void net.test.TestClass2nd.anotherMethod\\(\\)` took [0-9]+ ms")));
  }

  @Test
  public void testMultipleActionsOnTheSameMethod() throws IOException {
    String output = runTraceAgent("elapsed_time_in_nano net.test.TestClass test", "elapsed_time_in_ms net.test.TestClass test");
    Supplier<Stream<String>> streamSupplier = toStreamSupplier(output);
    assertTrue(streamSupplier.get().anyMatch(s -> s.trim().matches("TraceAgent \\(timing\\): `public void net.test.TestClass.test\\(\\)` took [0-9]+ nano")));
    assertTrue(streamSupplier.get().anyMatch(s -> s.trim().matches("TraceAgent \\(timing\\): `public void net.test.TestClass.test\\(\\)` took [0-9]+ ms")));
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

  // ---- CLI mode (offline JAR instrumentation) tests ----

  private static final String INSTRUMENTED_JAR = "sampleApp-instrumented.jar";

  private static final String AGENT_JAR = "../../trace-agent/target/trace-agent-1.0-SNAPSHOT.jar";
  private static final String SAMPLE_APP_JAR = "../sampleApp/target/sampleApp-1.0-SNAPSHOT.jar";

  /** Runs the trace-agent CLI to instrument the sampleApp JAR, then runs the instrumented JAR and returns its stdout+stderr output. */
  private String runCliMode(String... actions) throws IOException {
    // Step 1: write actions file
    File actionFile = new File(ACTION_FILE_NAME);
    try (FileWriter actionWriter = new FileWriter(actionFile)) {
      for (String a : actions) {
        actionWriter.append(a + "\n");
      }
    }

    // Step 2: run CLI transformer
    CommandLine cliCmd = CommandLine.parse("java -jar " + AGENT_JAR + " --input " + SAMPLE_APP_JAR + " --output " + INSTRUMENTED_JAR + " --actions " + ACTION_FILE_NAME);
    try (ByteArrayOutputStream cliOut = new ByteArrayOutputStream()) {
      DefaultExecutor cliExecutor = new DefaultExecutor();
      cliExecutor.setStreamHandler(new PumpStreamHandler(cliOut));
      cliExecutor.execute(cliCmd);
      System.out.println("CLI transformer output:\n" + cliOut.toString());
    } finally {
      actionFile.delete();
    }

    // Step 3: run the instrumented JAR standalone (no -javaagent)
    CommandLine runCmd = CommandLine.parse("java -jar " + INSTRUMENTED_JAR);
    try (ByteArrayOutputStream appOut = new ByteArrayOutputStream()) {
      DefaultExecutor appExecutor = new DefaultExecutor();
      appExecutor.setStreamHandler(new PumpStreamHandler(appOut));
      appExecutor.execute(runCmd);
      return appOut.toString();
    } finally {
      new File(INSTRUMENTED_JAR).delete();
    }
  }

  @Test
  public void testCliModeElapsedTimeInMs() throws IOException {
    String output = runCliMode("elapsed_time_in_ms net.test.TestClass2nd anotherMethod");
    Supplier<Stream<String>> streamSupplier = toStreamSupplier(output);
    assertTrue(streamSupplier.get().anyMatch(s -> s.trim().matches("TraceAgent \\(timing\\): `public void net.test.TestClass2nd.anotherMethod\\(\\)` took [0-9]+ ms")));
  }

  /** Runs only the CLI transformer step (no app execution) and returns the output JAR path. */
  private String instrumentJar(String... actions) throws IOException {
    File actionFile = new File(ACTION_FILE_NAME);
    try (FileWriter actionWriter = new FileWriter(actionFile)) {
      for (String a : actions) {
        actionWriter.append(a + "\n");
      }
    }
    CommandLine cliCmd = CommandLine.parse("java -jar " + AGENT_JAR + " --input " + SAMPLE_APP_JAR + " --output " + INSTRUMENTED_JAR + " --actions " + ACTION_FILE_NAME);
    try (ByteArrayOutputStream cliOut = new ByteArrayOutputStream()) {
      DefaultExecutor cliExecutor = new DefaultExecutor();
      cliExecutor.setStreamHandler(new PumpStreamHandler(cliOut));
      cliExecutor.execute(cliCmd);
    } finally {
      actionFile.delete();
    }
    return INSTRUMENTED_JAR;
  }

  @Test
  public void testCliModeOutputJarMetaInf() throws IOException {
    String jarPath = instrumentJar("elapsed_time_in_ms net.test.TestClass2nd anotherMethod", "stack_trace net.test.TestClass test");
    try (JarFile jarFile = new JarFile(jarPath)) {
      // MANIFEST.MF must be present and preserve Main-Class from the input JAR
      Manifest manifest = jarFile.getManifest();
      assertNotNull("MANIFEST.MF must be present", manifest);
      java.util.jar.Attributes main = manifest.getMainAttributes();
      assertEquals("Main-Class must be preserved", "net.test.SampleApp", main.getValue("Main-Class"));

      // Applied actions must be recorded in the manifest
      assertEquals("Trace-Agent-Action-1 must record first action", "elapsed_time_in_ms net.test.TestClass2nd anotherMethod", main.getValue("Trace-Agent-Action-1"));
      assertEquals("Trace-Agent-Action-2 must record second action", "stack_trace net.test.TestClass test", main.getValue("Trace-Agent-Action-2"));

      List<String> entryNames = Collections.list(jarFile.entries()).stream().map(JarEntry::getName).collect(java.util.stream.Collectors.toList());

      // The bare META-INF/ directory entry must be present
      assertTrue("META-INF/ directory entry must exist", entryNames.contains("META-INF/"));

      // MANIFEST.MF must be reachable as a JAR entry
      assertTrue("META-INF/MANIFEST.MF entry must exist", entryNames.contains("META-INF/MANIFEST.MF"));

      // Maven metadata from the input JAR must be preserved
      assertTrue("META-INF/maven/ directory must be preserved", entryNames.contains("META-INF/maven/"));
    } finally {
      new File(jarPath).delete();
    }
  }

  @Test
  public void testCliModeTraceArgs() throws IOException {
    String output = runCliMode("trace_args net.test.TestClass2nd methodWithArgs");
    Supplier<Stream<String>> streamSupplier = toStreamSupplier(output);
    assertTrue(
        streamSupplier
            .get()
            .anyMatch(s -> s.contains("TraceAgent (trace_args pre): `public int net.test.TestClass2nd.methodWithArgs(java.lang.String,int) called with [secret, 42]")));
  }
}
