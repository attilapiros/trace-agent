package net.test;

import java.util.ArrayList;
import java.util.List;

public class ArgUtils {

  public static ArgumentsCollection parseOptionalArgs(List<String> knownKeys, String arguments) {
    List<String> usedKeys = new ArrayList<String>();
    ArgumentsCollection parsedArgs = new ArgumentsCollection();
    if (arguments != null && !arguments.isEmpty()) {
      for (String keyValue : arguments.split(",")) {
        String[] kv = keyValue.split(":");
        if (kv.length >= 2) {
          final String key = kv[0];
          final String value = keyValue.substring(key.length() + 1);
          if (knownKeys.contains(key)) {
            if (usedKeys.contains(key)) {
              System.err.println("TraceAgent skips the reused key: " + kv[0]);
            } else {
              parsedArgs.put(key, value);
              usedKeys.add(key);
            }
          } else {
            System.err.println("TraceAgent detected an unknown argument key: " + kv[0]);
          }
        } else {
          System.err.println("TraceAgent detected a wrong argument format: " + keyValue);
        }
      }
    }
    return parsedArgs;
  }
}
