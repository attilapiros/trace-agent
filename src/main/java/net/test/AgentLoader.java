package net.test;

import com.sun.tools.attach.VirtualMachine;

import java.io.File;
import java.util.Optional;

/** Agent Loader to attach to a PID. */
public class AgentLoader {

  public static void main(String[] args) {
    File agentFile = new File(args[0]);

    System.out.println(agentFile.getAbsolutePath());
    try {
      String pid = args[1];
      System.out.println("Attaching to target JVM with PID: " + pid);
      VirtualMachine jvm = VirtualMachine.attach(pid);
      jvm.loadAgent(agentFile.getAbsolutePath());
      jvm.detach();
      System.out.println("Succefully attached to target JVM and loaded Java agent");
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
