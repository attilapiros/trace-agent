package net.test.interceptor;

import net.test.ArgUtils;
import net.test.ArgumentsCollection;
import net.test.CommonActionArgs;
import net.test.DefaultArguments;
import net.test.GlobalArguments;

import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Arrays;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Callable;
import javax.management.*;
import javax.management.ObjectName;

import com.sun.management.HotSpotDiagnosticMXBean;

public class HeapDumpCommandInterceptor {

  public static final String NAME = "heap_dump";

  private static void dumpHeap(String filePath, boolean live) {
    try {
      MBeanServer server = ManagementFactory.getPlatformMBeanServer();
      HotSpotDiagnosticMXBean mxBean = ManagementFactory.newPlatformMXBeanProxy(server, "com.sun.management:type=HotSpotDiagnostic", HotSpotDiagnosticMXBean.class);
      mxBean.dumpHeap(filePath, live);
    } catch (IOException e) {
      System.err.println("TraceAgent: (heap_dump). IOException at file: " + filePath + "!");
      e.printStackTrace(System.out);
    }
  }

  private static String WHERE = "where";

  private static String LIVE_OBJECTS = "live_objects";

  private static List<String> KNOWN_ARGS = Arrays.asList(CommonActionArgs.IS_DATE_LOGGED, WHERE, LIVE_OBJECTS);

  private CommonActionArgs commonActionArgs;

  private final boolean isBefore;

  private final boolean isAfter;

  private final boolean liveObjects;

  private static final AtomicInteger index = new AtomicInteger(0);

  private final GlobalArguments globalArguments;

  public HeapDumpCommandInterceptor(GlobalArguments globalArguments, String actionArgs, DefaultArguments defaults) {
    ArgumentsCollection parsed = ArgUtils.parseOptionalArgs(KNOWN_ARGS, actionArgs);
    this.commonActionArgs = new CommonActionArgs(parsed, defaults);
    this.liveObjects = parsed.parseBoolean(LIVE_OBJECTS, true);
    final String where = parsed.getOrDefault(WHERE, "before");
    switch (where) {
      case "before":
        isBefore = true;
        isAfter = false;
        break;
      case "after":
        isBefore = false;
        isAfter = true;
        break;
      case "beforeAndAfter":
        isBefore = true;
        isAfter = true;
        break;
      default:
        globalArguments.getTargetStream().println("TraceAgent: (heap_dump) invalid value for `where`: " + where + ". Action is switched off!");
        isBefore = false;
        isAfter = false;
    }
    this.globalArguments = globalArguments;
  }

  private static String filename(String methodName, boolean isBefore, boolean liveObjects) {
    StringBuilder stringBuilder = new StringBuilder(methodName);
    stringBuilder.append("_");
    stringBuilder.append(index.getAndIncrement());
    if (isBefore) {
      stringBuilder.append("_before");
    } else {
      stringBuilder.append("_after");
    }
    if (liveObjects) {
      stringBuilder.append("_onlyLiveObjects");
    } else {
      stringBuilder.append("_includingUnreachableObjects");
    }
    stringBuilder.append(".hprof");
    return stringBuilder.toString();
  }

  @RuntimeType
  public Object intercept(@Origin Method method, @SuperCall Callable<?> callable) throws Exception {
    if (isBefore) {
      String fName = filename(method.getName(), true, liveObjects);
      globalArguments.getTargetStream().println(commonActionArgs.addPrefix("TraceAgent (heap_dump): at the beginning of `" + method + "` to " + fName));
      dumpHeap(fName, liveObjects);
    }
    try {
      return callable.call();
    } finally {
      if (isAfter) {
        String fName = filename(method.getName(), false, liveObjects);
        globalArguments.getTargetStream().println(commonActionArgs.addPrefix("TraceAgent (heap_dump): at the end of `" + method + "` to " + fName));
        dumpHeap(fName, liveObjects);
      }
    }
  }
}
