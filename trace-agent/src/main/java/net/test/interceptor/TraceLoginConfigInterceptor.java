package net.test.interceptor;

import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.test.ArgUtils;
import net.test.ArgumentsCollection;
import net.test.CommonActionArgs;
import net.test.DefaultArguments;
import net.test.GlobalArguments;

import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

public class TraceLoginConfigInterceptor {

  public static String NAME = "trace_login_config";

  private static String ENTRY_NAME = "entry_name";

  private static List<String> KNOWN_ARGS = Arrays.asList(CommonActionArgs.IS_DATE_LOGGED, CommonActionArgs.IS_THREADNAME_LOGGED, ENTRY_NAME);

  private CommonActionArgs commonActionArgs;

  private final String entryName;

  private final GlobalArguments globalArguments;

  public TraceLoginConfigInterceptor(GlobalArguments globalArguments, String actionArgs, DefaultArguments defaults) {
    ArgumentsCollection parsed = ArgUtils.parseOptionalArgs(KNOWN_ARGS, actionArgs);
    this.commonActionArgs = new CommonActionArgs(parsed, defaults);
    this.entryName = parsed.get(ENTRY_NAME);
    this.globalArguments = globalArguments;
  }

  @RuntimeType
  public Object intercept(@Origin Method method, @AllArguments Object[] allArguments, @SuperCall Callable<?> callable) throws Exception {
    Configuration config = Configuration.getConfiguration();
    AppConfigurationEntry[] appConfigurationEntries = config.getAppConfigurationEntry(entryName);
    String entries = "";
    if (appConfigurationEntries != null) {
      for (AppConfigurationEntry e : appConfigurationEntries) {
        entries += entryName + " {\n\t" + e.getLoginModuleName() + " " + e.getControlFlag() + "\n";
        for (Map.Entry<String, ?> o : e.getOptions().entrySet()) {
          entries += "\t" + o.getKey() + "=" + o.getValue() + "\n";
        }
        entries += "}\n";
      }
    }
    if (entries.isEmpty()) {
      entries = "Not Found";
    }
    globalArguments
        .getTargetStream()
        .println(commonActionArgs.addPrefix("TraceAgent (trace_login_config): `" + method + " login config for entry \"" + entryName + "\"\n" + entries));
    return callable.call();
  }
}
