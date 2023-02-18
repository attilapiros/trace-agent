package net.test;

import java.io.PrintStream;

public class GlobalArguments {

  private final PrintStream targetStream;

  public GlobalArguments(PrintStream targetStream) {
    this.targetStream = targetStream;
  }

  public PrintStream getTargetStream() {
    return targetStream;
  }
}
