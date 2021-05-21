import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.*;

import static org.junit.Assert.assertTrue;

public class TestTraceAgent {
  CommandLine cmd =
      CommandLine.parse(
          "java -javaagent:../../trace-agent/target/trace-agent-1.0-SNAPSHOT.jar=\"actionsFile:./actions.txt\" "
              + "-jar ../sampleApp/target/sampleApp-1.0-SNAPSHOT.jar");

  DefaultExecutor executor = new DefaultExecutor();
  ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
  File actionFile = new File("actions.txt");

  @Before
  public void setUp() throws IOException {
    executor.setStreamHandler(new PumpStreamHandler(outputStream));
    actionFile.createNewFile();
  }

  @After
  public void tearDown() {
    actionFile.delete();
  }

  @Test
  public void testStackTrace() throws IOException {
    createActions("stack_trace net.test.TestClass2nd anotherMethod");
    executor.execute(cmd);
    assertTrue(outputStream.toString().contains("TraceAgent (stack trace)"));
    assertTrue(
        outputStream.toString().contains("at net.test.TestClass2nd.anotherMethod(SampleApp.java)"));
    assertTrue(outputStream.toString().contains("at net.test.SampleApp.main"));
  }

  @Test
  public void testTraceArgs() throws IOException {
    createActions("trace_args net.test.TestClass2nd methodWithArgs");
    executor.execute(cmd);
    assertTrue(outputStream.toString().contains("TraceAgent (trace_args)"));
    assertTrue(outputStream.toString().contains("net.test.TestClass2nd.methodWithArgs"));
    assertTrue(outputStream.toString().contains("[secret, 42]"));
  }

  @Test
  public void testTraceRetVal() throws IOException {
    createActions("trace_retval net.test.TestClass2nd methodWithArgs");
    executor.execute(cmd);
    assertTrue(outputStream.toString().contains("TraceAgent (trace_retval)"));
    assertTrue(outputStream.toString().contains("net.test.TestClass2nd.methodWithArgs"));
    assertTrue(outputStream.toString().contains("returns with 12"));
  }

  @Test
  public void testElapsedTime() throws IOException {
    createActions(
        "elapsed_time_in_nano net.test.TestClass test",
        "elapsed_time_in_ms net.test.TestClass2nd anotherMethod");
    executor.execute(cmd);
    assertTrue(outputStream.toString().contains("TraceAgent (timing)"));
    assertTrue(
        outputStream
            .toString()
            .matches("(.|\\s)*net.test.TestClass.test(.)*took [0-9]+ nano(.|\\s)*"));
    assertTrue(
        outputStream
            .toString()
            .matches("(.|\\s)*net.test.TestClass2nd.anotherMethod(.)*took [0-9]+ ms(.|\\s)*"));
  }

  @Test
  public void testCounter() throws IOException {
    createActions("counter net.test.TestClass2nd anotherMethod count_frequency:1");
    executor.execute(cmd);
    assertTrue(
        outputStream
            .toString()
            .matches("(.|\\s)*TraceAgent \\(counter\\):(.)*anotherMethod(.)*called 1(.|\\s)*"));
  }

  private void createActions(String... actions) throws IOException {
    FileWriter actionWriter = new FileWriter(actionFile);
    for (String a : actions) {
      actionWriter.append(a + "\n");
    }
    actionWriter.close();
  }
}
