package net.test.interceptor;

import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.test.ArgUtils;
import net.test.ArgumentsCollection;
import net.test.CommonActionArgs;
import net.test.DefaultArguments;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

public class TraceParamCallRetValueInterceptor {

  public static String NAME = "trace_param_call_retval";

  private static String PARAM_INDEX = "param_index";

  private static String METHOD_TO_CALL = "method_to_call";

  private static List<String> KNOWN_ARGS =
      Arrays.asList(CommonActionArgs.IS_DATE_LOGGED, PARAM_INDEX, METHOD_TO_CALL);

  private CommonActionArgs commonActionArgs;

  private final int paramIndex;

  private final String methodToCall;

  public TraceParamCallRetValueInterceptor(String actionArgs, DefaultArguments defaults) {
    ArgumentsCollection parsed = ArgUtils.parseOptionalArgs(KNOWN_ARGS, actionArgs);
    this.commonActionArgs = new CommonActionArgs(parsed, defaults);
    this.paramIndex = parsed.parseInt(PARAM_INDEX, 0);
    this.methodToCall = parsed.get(METHOD_TO_CALL);
  }

  @RuntimeType
  public Object intercept(
      @Origin Method method, @AllArguments Object[] allArguments, @SuperCall Callable<?> callable)
      throws Exception {
    String retVal = "";
    if (allArguments.length > paramIndex) {
      try {
        Method getAllTokens = allArguments[paramIndex].getClass().getMethod(methodToCall);
        retVal = getAllTokens.invoke(allArguments[paramIndex]).toString();
      } catch (NoSuchMethodException e) {
        retVal = "No such method found: " + methodToCall;
      }
    } else {
      retVal =
          "Parameter tried to be get with index "
              + paramIndex
              + " but max index is "
              + (allArguments.length - 1);
    }
    System.out.println(
        commonActionArgs.addPrefix(
            "TraceAgent (trace_param_call_retval): "
                + method
                + " parameter instance with index "
                + paramIndex
                + " method call \""
                + methodToCall
                + "\" returns with \n"
                + retVal));
    return callable.call();
  }
}
