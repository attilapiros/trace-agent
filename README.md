# Trace Agent

Trace Agent is a Java agent configurable by a simple text file to trace Java methods without any rebuild.
Its config file called `actions.txt` can be provided as an external resource or can be embedded within the agent jar
this way in a distributed environment just the agent jar should be added to the classpath of the different components.


# An example

It is much easier to understand how this tool can be used if I show you it through an example.

## The project we would like to trace

Let's say we have a project what we would like analyze. Let's take a very simple Java application as an example:

```java

package net.test;

public class App {
    public static void main( String[] args )
    {
        new TestClass().test();
        TestClass2nd testClass2nd = new TestClass2nd();
        testClass2nd.anotherMethod();
        testClass2nd.methodWithArgs("secret", 42);
    }
}


class TestClass {

  public void test() {
      System.out.println("Hello World!");
      try {
        Thread.sleep(100);
      } catch(Exception e) { }
  }
}

class TestClass2nd {

  public void anotherMethod() {
      System.out.println("2nd Hello World!");
      try {
        Thread.sleep(100);
      } catch(Exception e) { }
  }

  public int methodWithArgs(String str, int i) {
      System.out.println("methodWithArgs");
      return 12;
  }
}

```

Which can be executed as:

```
$ java -jar testartifact-1.0-SNAPSHOT.jar
Hello World!
2nd Hello World!
methodWithArgs
```

## Let's trace it

If we would like to:
- measure the elapsed time of the `test` method in nanosecond
- see the call stack at the beginning of `anotherMethod`
- and measure the elapsed time in milliseconds also within the `anotherMethod`
- the trace the actual argument values used for calling the method `methodWithArgs`
- the trace the return value of the method `methodWithArgs` call

without touching the testartifact then we could set up the `actions.txt` (the config of the trace agent) like this:

```
elapsed_time_in_nano net.test.TestClass test
elapsed_time_in_ms net.test.TestClass2nd anotherMethod
stack_trace net.test.TestClass2nd anotherMethod
trace_args net.test.TestClass2nd methodWithArgs
trace_retval net.test.TestClass2nd methodWithArgs
```

This `actions.txt` is part of the trace agent jar as a resource (no recompile/rebuild is needed just edit the file within the jar).

And to start the trace one could use:

```
$ java -javaagent:target/trace-agent-1.0-SNAPSHOT.jar -jar testartifact-1.0-SNAPSHOT.jar
Hello World!
TraceAgent (timing): `public void net.test.TestClass.test()` took 101639483 nano
TraceAgent (stack trace):
        at net.test.TestClass2nd.anotherMethod(App.java)
        at net.test.App.main(App.java:8)
2nd Hello World!
TraceAgent (timing): `public void net.test.TestClass2nd.anotherMethod()` took 100 ms
TraceAgent (trace_args): `public void net.test.TestClass2nd.methodWithArgs(java.lang.String,int) called with [secret, 42]
methodWithArgs
TraceAgent (trace_retval): `public int net.test.TestClass2nd.methodWithArgs(java.lang.String,int) returns with 12
```

That's it! This was the basic idea why I started this project.

## The config format

The config format is simple lines with the following structure:

```
<action-name> <class-name> <method-name> <optionalParameters>
```

Empty lines and lines starting with `#` (comments) are skipped.

## Using regular expressions for matching to class and method names

When the class name or the method is given in the format of `REGEXP(<pattern>)` then
[java regular expression](https://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html#sum) is used for matching.

Example `actions.txt` to match for every methods of the classes within the package `net.test`:

```
elapsed_time_in_ms REGEXP(net\.test\..*) REGEXP(.*)
```

And the output will be something like this:

```
Hello World!
TraceAgent (timing): `public void net.test.TestClass.test()` took 102 ms
2nd Hello World!
TraceAgent (timing): `public void net.test.TestClass2nd.anotherMethod()` took 103 ms
methodWithArgs
TraceAgent (timing): `public int net.test.TestClass2nd.methodWithArgs(java.lang.String,int)` took 18 ms
TraceAgent (timing): `public static void net.test.App.main(java.lang.String[])` took 255 ms
```

## Parameterization

The trace agent can be parameterised with key-value pairs in the format of `<key_1>:<value_1>,<key_2>:<value_2>,...<key_N>:<value_N>`.
The parameters can be given globally or for each rule separately. Using a common format at both places makes the parsing reusable.

Disclaimer: currently parsing is done via simply splitting the strings so commas (,) and colons (:) cannot be used in the values
(if there is a need then escaping should be introduced in the future).

Not all the parameters can be used at both places. And there will be parameters which make sense only for one specific action only (or for a set of actions).

### Example for common argument (both global and action argument): `isDateLogged`

The `isDateLogged` can be used to request the current date time to be contained as prefix in the actions logs.
This is false by default but via setting it globally this default can be changed:

```
$  java -javaagent:target/trace-agent-1.0-SNAPSHOT.jar="isDateLogged:true" -jar ../testartifact/target/testartifact-1.0-SNAPSHOT.jar
Hello World!
2020-06-23T12:34:27.746 TraceAgent (timing): `public void net.test.TestClass.test()` took 101212548 nano
2020-06-23T12:34:27.805 TraceAgent (stack trace)
	at net.test.TestClass2nd.anotherMethod(App.java)
	at net.test.App.main(App.java:9)
2nd Hello World!
2020-06-23T12:34:27.907 TraceAgent (timing): `public void net.test.TestClass2nd.anotherMethod()` took 100 ms
2020-06-23T12:34:27.907 TraceAgent (trace_args): `public int net.test.TestClass2nd.methodWithArgs(java.lang.String,int) called with [secret, 42]
methodWithArgs
Tue Jun 23 12:34:27 CEST 2020
2020-06-23T12:34:27.915 TraceAgent (trace_retval): `public int net.test.TestClass2nd.methodWithArgs(java.lang.String,int) returns with 12
```

Now if you would like to save date time formatting for `elapsed_time_in_nano` you can set the `isDateLogged` to false for that rule:

```
elapsed_time_in_nano net.test.TestClass test isDateLogged:false
elapsed_time_in_ms net.test.TestClass2nd anotherMethod
stack_trace net.test.TestClass2nd anotherMethod
trace_args net.test.TestClass2nd methodWithArgs
trace_retval net.test.TestClass2nd methodWithArgs
```

And when the experiment reexecuted the date time is not logged for nanosecond measure but for other rules:

```
$  java -javaagent:target/trace-agent-1.0-SNAPSHOT.jar="isDateLogged:true" -jar ../testartifact/target/testartifact-1.0-SNAPSHOT.jar
Hello World!
TraceAgent (timing): `public void net.test.TestClass.test()` took 100895003 nano
2020-06-23T12:39:25.754 TraceAgent (stack trace)
	at net.test.TestClass2nd.anotherMethod(App.java)
	at net.test.App.main(App.java:9)
2nd Hello World!
2020-06-23T12:39:25.862 TraceAgent (timing): `public void net.test.TestClass2nd.anotherMethod()` took 101 ms
2020-06-23T12:39:25.863 TraceAgent (trace_args): `public int net.test.TestClass2nd.methodWithArgs(java.lang.String,int) called with [secret, 42]
methodWithArgs
Tue Jun 23 12:39:25 CEST 2020
2020-06-23T12:39:25.875 TraceAgent (trace_retval): `public int net.test.TestClass2nd.methodWithArgs(java.lang.String,int) returns with 12
```

### Trace agent global only parameters

There are parameters which configures the trace agent globally.

#### Specifying formatting of date times in the action logs

The `dateTimeFormat` can be used to specify the formatting for date times:

```
$  java -javaagent:target/trace-agent-1.0-SNAPSHOT.jar="isDateLogged:true,dateTimeFormat:YYYY-MM-dd'T'hh:mm" -jar ../testartifact/target/testartifact-1.0-SNAPSHOT.jar                            134 ↵
Hello World!
TraceAgent (timing): `public void net.test.TestClass.test()` took 100606015 nano
2020-06-23T12:50 TraceAgent (stack trace)
	at net.test.TestClass2nd.anotherMethod(App.java)
	at net.test.App.main(App.java:9)
2nd Hello World!
2020-06-23T12:50 TraceAgent (timing): `public void net.test.TestClass2nd.anotherMethod()` took 100 ms
2020-06-23T12:50 TraceAgent (trace_args): `public int net.test.TestClass2nd.methodWithArgs(java.lang.String,int) called with [secret, 42]
methodWithArgs
Tue Jun 23 12:50:33 CEST 2020
2020-06-23T12:50 TraceAgent (trace_retval): `public int net.test.TestClass2nd.methodWithArgs(java.lang.String,int) returns with 12
```

The default is [ISO_LOCAL_DATE_TIME](https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html#ISO_LOCAL_DATE_TIME).
For the details and valid patterns please check: [DateTimeFormatter](https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html).

#### The external actions file

It is possible to specify an external actions file. For example if the actions file in the current directory:

```
java -javaagent:target/trace-agent-1.0-SNAPSHOT.jar="actionsFile:./actions.txt" -jar ../testartifact/target/testartifact-1.0-SNAPSHOT.jar
```

In this case all the rules are used from both the internal and external action files: like the two list would be merged together.

In distributed environment when external action file is used you should take care on each node the action file is really can be accessed using the path.
Otherwise the error is logged but the application continues: "TraceAgent does not find the external action file: <file>".

#### Select the target output stream: stderr/stdout

By default TraceAgent traces to the `stdout` but `stderr` can be selected by passing `targetStream:stderr` to the agent:

```
java -javaagent:target/trace-agent-1.0-SNAPSHOT.jar="targetStream:stderr" -jar ../testartifact/target/testartifact-1.0-SNAPSHOT.jar
```


#### Enable agent logging

To troubleshoot the process of class transformation and instrumentation verbose logging on
the steps executed by the javaagent can be enabled with the `enableAgentLog` flag (if unspecified, its default value is `false`).

The structure of the logging output is dependent on the instrumentation library used (currently ByteBuddy's internal format), and is subject to change.

The agent log will be written to the stdout.

Example usage:

```
java -javaagent:target/trace-agent-1.0-SNAPSHOT.jar="actionsFile:./actions.txt,enableAgentLog:true" -jar ../testartifact/target/testartifact-1.0-SNAPSHOT.jar
```
Example output:
```
TraceAgent is initializing
TraceAgent tries to install actions: [{actionId='trace_args', classMatcher='DummyApp', methodMatcher='intMethod', actionArgs='null'}, ... ]
[Byte Buddy] BEFORE_INSTALL net.bytebuddy.agent.builder.AgentBuilder$Default$ExecutingTransformer@4de8b406 on sun.instrument.InstrumentationImpl@3c756e4d
[Byte Buddy] INSTALL net.bytebuddy.agent.builder.AgentBuilder$Default$ExecutingTransformer@4de8b406 on sun.instrument.InstrumentationImpl@3c756e4d
# ...
TraceAgent installed actions successfully
# ...
[Byte Buddy] TRANSFORM DummyApp [sun.misc.Launcher$AppClassLoader@18b4aac2, null, loaded=false]
[Byte Buddy] COMPLETE DummyApp [sun.misc.Launcher$AppClassLoader@18b4aac2, null, loaded=false]
[Byte Buddy] DISCOVERY DummyApp [sun.misc.Launcher$AppClassLoader@18b4aac2, null, loaded=false]
[Byte Buddy] IGNORE DummyApp [sun.misc.Launcher$AppClassLoader@18b4aac2, null, loaded=false]
[Byte Buddy] COMPLETE DummyApp [sun.misc.Launcher$AppClassLoader@18b4aac2, null, loaded=false]
# ...
```

# Actions

## The counter action

This action can be used to count the number of of method calls. It has one parameter `count_frequency` which specifies after how many calls there will be a printout.
Its output will be printed before the targeted method body is executed.

Example:

```
  counter net.test.TestClass2nd calledSeveralTimes count_frequency:4
```

The output is will be like:

```
TraceAgent (counter): 4
TraceAgent (counter): 8
TraceAgent (counter): 12
TraceAgent (counter): 16
TraceAgent (counter): 20
TraceAgent (counter): 24
TraceAgent (counter): 28
```

## The avg_timing action

This action creates statistics from the method runtimes based on a specified number of method calls (called `window_length` which is 100 by default).

It traces out the min, average and max ellapsed times in **milliseconds** when the number of calls are reached the `window_length` then it starts a new window.

Example:

```
avg_timing net.test.TestClass2nd calledSeveralTimes window_length:5
```

Example output:

```
TraceAgent (avg_timing): `public void net.test.TestClass2nd.calledSeveralTimes()` window_length: 5 min: 102 avg: 103 max: 105
```

**Important: if it is used with REGEXP then it traces at the method where `window_lenth` reached but it contains the elapsed times of all the matching methods!**

## The trace_login_config action

The action traces the JVM `javax.security.auth.login.Configuration` entry which is set as parameter.
If the entry is not found then the output will be `Not Found`.

Example:

```
trace_login_config A foo entry_name:KafkaClient
```

Example output:

```
TraceAgent (trace_login_config): `public void A.foo() login config for entry "KafkaClient"
KafkaClient {
	com.sun.security.auth.module.Krb5LoginModule LoginModuleControlFlag: required
	principal=user@MY.DOMAIN.COM
	storeKey=true
	keyTab=./kafka_client.keytab
	useKeyTab=true
	useTicketCache=false
	serviceName=kafka
};
```

## The trace_args_with_method_call action

The action expects 2 parameters and calls a method on an argument instance:
* `param_index` This is the index of the argument instance.
* `method_to_call` This the the method which is called on the argument instance. Please note,
only public member methods without any parameters can be used. The internal implementation uses
reflection so it can be used in situations where the number of calls is low.

Example:

```
trace_args_with_method_call org.apache.hadoop.fs.FileSystem addDelegationTokens param_index:1,method_to_call:getAllTokens
```

Example output:

```
TraceAgent (trace_args_with_method_call): public default org.apache.hadoop.security.token.Token[] org.apache.hadoop.security.token.DelegationTokenIssuer.addDelegationTokens(java.lang.String,org.apache.hadoop.security.Credentials) throws java.io.IOException parameter instance with index 1 method call "getAllTokens" returns with
[Kind: testKind, Service: testService, Ident: 74 65 73 74 49 64 65 6e 74 69 66 69 65 72]
```

## The diagnostic_command action

This action provides generic access to Diagnostic Command MBean.
Generic as via this action any parameterless diagnostic command can be executed.

Possible action args:

* `cmd`: The diagnostic command to run. For example `vmNativeMemory`, `gcClassHistogram`, `threadPrint`...
* `limit_output_lines`: The number of lines which should be printed from the command output.
                        It is very usefull in case of the class histogram where classes taking the most memory are listed at the top.
* `where`: The position where the command should be called relative to the instrumented method. It is one of `before` (default), `after` and `beforeAndAfter`.

### The diagnostic_command / gcClassHistogram subaction

This can be used to identify memory leaks and this is where `beforeAndAfter` could be very useful if the method should cleanup after itself.

Example `actions.txt`:

```
diagnostic_command net.test.TestClass2nd anotherMethod cmd:gcClassHistogram,limit_output_lines:5,where:beforeAndAfter
```

Example output:

```
TraceAgent (diagnostic_command / gcClassHistogram): at the beginning of `public void net.test.TestClass2nd.anotherMethod()`:

 num     #instances         #bytes  class name
----------------------------------------------
   1:          5935         482360  [C
   2:          2633         290624  java.lang.Class
   3:          5919         142056  java.lang.String

2nd Hello World!
TraceAgent (diagnostic_command / gcClassHistogram): at the end of `public void net.test.TestClass2nd.anotherMethod()`:

 num     #instances         #bytes  class name
----------------------------------------------
   1:          5938         482528  [C
   2:          2633         290624  java.lang.Class
   3:          5922         142128  java.lang.String

methodWithArgs
```

### The diagnostic_command / threadPrint subaction

This can be used to print out thee running thread when the execution reaches a method.

Example `actions.txt`:

```
diagnostic_command net.test.TestClass2nd anotherMethod cmd:threadPrint,limit_output_lines:15
```

Example output:

```
TraceAgent (diagnostic_command / threadPrint): at the beginning of `public void net.test.TestClass2nd.anotherMethod()`:
2021-05-21 16:50:51
Full thread dump OpenJDK 64-Bit Server VM (25.292-b10 mixed mode):

"Service Thread" #8 daemon prio=9 os_prio=0 tid=0x00007f2e904c0800 nid=0x6ee runnable [0x0000000000000000]
   java.lang.Thread.State: RUNNABLE

"C1 CompilerThread1" #7 daemon prio=9 os_prio=0 tid=0x00007f2e904b1800 nid=0x6ed waiting on condition [0x0000000000000000]
   java.lang.Thread.State: RUNNABLE

"C2 CompilerThread0" #6 daemon prio=9 os_prio=0 tid=0x00007f2e904af800 nid=0x6ec waiting on condition [0x0000000000000000]
   java.lang.Thread.State: RUNNABLE

"Signal Dispatcher" #4 daemon prio=9 os_prio=0 tid=0x00007f2e90160000 nid=0x6eb runnable [0x0000000000000000]
   java.lang.Thread.State: RUNNABLE

"Finalizer" #3 daemon prio=8 os_prio=0 tid=0x00007f2e90129000 nid=0x6ea in Object.wait() [0x00007f2e94e43000]

```

### The diagnostic_command / vmNativeMemory subaction

Prerequisite: the Native Memory Tracking (NMT) must be enabled fot the application which is traced.
To enable NMT the app must be started with one of the following JVM argument:

- `-XX:NativeMemoryTracking=summary`
- `-XX:NativeMemoryTracking=detail`

Example `actions.txt`:

```
diagnostic_command net.test.TestClass2nd anotherMethod cmd:vmNativeMemory
```

Example output:

```
TraceAgent (diagnostic_command / vmNativeMemory): at the beginning of `public void net.test.TestClass2nd.anotherMethod()`:

Native Memory Tracking:

Total: reserved=3183527KB, committed=214875KB
-                 Java Heap (reserved=1781760KB, committed=112640KB)
                            (mmap: reserved=1781760KB, committed=112640KB)

-                     Class (reserved=1062197KB, committed=15157KB)
                            (classes #2246)
                            (malloc=3381KB #1231)
                            (mmap: reserved=1058816KB, committed=11776KB)

-                    Thread (reserved=11328KB, committed=11328KB)
                            (thread #11)
                            (stack: reserved=11280KB, committed=11280KB)
                            (malloc=36KB #66)
                            (arena=12KB #21)

-                      Code (reserved=249848KB, committed=2784KB)
                            (malloc=248KB #884)
                            (mmap: reserved=249600KB, committed=2536KB)

-                        GC (reserved=68566KB, committed=63138KB)
                            (malloc=3466KB #116)
                            (mmap: reserved=65100KB, committed=59672KB)

-                  Compiler (reserved=241KB, committed=241KB)
                            (malloc=12KB #87)
                            (arena=229KB #6)

-                  Internal (reserved=3556KB, committed=3556KB)
                            (malloc=3524KB #3547)
                            (mmap: reserved=32KB, committed=32KB)

-                    Symbol (reserved=3469KB, committed=3469KB)
                            (malloc=2406KB #11126)
                            (arena=1063KB #1)

-    Native Memory Tracking (reserved=402KB, committed=402KB)
                            (malloc=109KB #1551)
                            (tracking overhead=293KB)

-               Arena Chunk (reserved=2159KB, committed=2159KB)
                            (malloc=2159KB)

```

## The heap_dump action

This action can be used to request a heap dump.

Possible action args:

* `where`: The position where the command should be called relative to the instrumented method. It is one of `before` (default), `after` and `beforeAndAfter`.
* `live_objects`: If `true` (default) dump only live objects i.e. objects that are reachable from others.

Heapdump file names follows the following patttern:

```
{requestedMethodName}_{globalNumericIndex}_[before|after]_[onlyLiveObjects|includingUnreachableObjects].hprof
```

# Summary of Parameters

* `isDateLogged` (scope: both `global` and `action`) The `isDateLogged` can be used to request the current date time to be contained as prefix in the actions logs.
* `dateTimeFormat` (scope: `global`) Can be used to specify formatting for datetimes. The default is [ISO_LOCAL_DATE_TIME](https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html#ISO_LOCAL_DATE_TIME).
  For the details and valid patterns please check: [DateTimeFormatter](https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html).
* `log_threshold_ms` (scope: multiple actions) This threshold represents the elapsed number of milliseconds after there will be a printout. The default is `0`, which means it should printout on every call. For example, if we only like to log an action when it takes more than 1 second to complete: `elapsed_time_in_ms net.test.TestClass test log_threshold_ms:1000`
* `log_threshold_nano` (scope: only for `elapsed_time_in_nano`) Similar to `log_threshold_ms` but in nanoseconds.
* `limit_count` (scope: only for `stack_trace`) Trace only the first `limit_count` number of calls. This limit is turned off by default by set it to -1.
* `window_length` scope `avg_timing`

## Actions and supported parameters

All actions have the following set of arguments

* `class-name`: **Required** name for the class to be traced

* `action-name`: **Required** name of method to be traced

* `params`: Optional list of parameters in form of `<key_1>:<value_1>,<key_2>:<value_2>,...<key_N>`<br>

Here is the full list of actions and supported `params`

| Action                      | Supported arguments                           |
| --------------------------- | --------------------------------------------- |
| elapsed_time_in_nano        | isDateLogged, log_threshold_nano              |
| elapsed_time_in_ms          | isDateLogged, log_threshold_ms                |
| stack_trace                 | isDateLogged, log_threshold_ms, limit_count   |
| trace_args                  | isDateLogged, log_threshold_ms                |
| trace_retval                | isDateLogged, log_threshold_ms                |
| trace_args_with_method_call | isDateLogged, param_index, method_to_call     |
| counter                     | isDateLogged, count_frequency                 |
| avg_timing                  | isDateLogged, window_length                   |
| trace_login_config          | isDateLogged, entry_name                      |


# Some complex examples how to specify a javaagent

Although trace agent is a general tool I would like to write up some use cases where this tool can be useful for you
(and also for myself for future reference).

## For JVM based languages other than Java (Scala, Clojure, Kotlin, ...)

If you can run an experiment you can use the regexp based matching to find out what pattern you should use exactly.
When experimenting is not possible then you can use `javap` to find out what will be the final class and method name.

### Example

For example in case of a Spark Core method (which uses Scala) this can be done as follows. Let's say you would like to match for
[createTaskScheduler](https://github.com/apache/spark/blob/master/core/src/main/scala/org/apache/spark/SparkContext.scala#L2757).
First you should find out the class file. As from a Scala object the compiler generates a class which ends with `$` in our case
this will be `org.apache.spark.SparkContext$.class` (as the object fully qualified name is `org.apache.spark.SparkContext`).

Now with `javap` the exact method name can be find out easily, like:

```
$ unzip -p jars/spark-core_2.11-2.4.5.jar org/apache/spark/SparkContext$.class > SparkContext$.class
$ javap -p SparkContext$.class | grep createTaskScheduler
  public scala.Tuple2<org.apache.spark.scheduler.SchedulerBackend, org.apache.spark.scheduler.TaskScheduler> org$apache$spark$SparkContext$$createTaskScheduler(org.apache.spark.SparkContext, java.lang.String, java.lang.String);
```

So to measure the elapsed time within this method the actions can be:

```
elapsed_time_in_ms org.apache.spark.SparkContext$ org$apache$spark$SparkContext$$createTaskScheduler
```

## Spark submit

When the submit process itself need to be traced then in the `SPARK_SUBMIT_OPTS` environment variable the trace agent have to be given as a java agent:

```
export SPARK_SUBMIT_OPTS="-javaagent:trace-agent-0.0.8.jar"
```

## YARN AM

When the YARN resource allocation is need to be traced at client mode the `spark.yarn.am.extraJavaOptions` must be used:

```
--conf spark.yarn.am.extraJavaOptions="-javaagent:trace-agent-0.0.8.jar" --jars trace-agent-0.0.8.jar
```

## Spark driver client mode

In case of client mode when the driver is at node where you call spark-submit at you can simply start Spark with the config
`spark.driver.extraJavaOptions` where you can specify `-javagent` with the trace-agent jar location:

Example (when you are in the same directory where the trace-agent jar is stored):

```
$ spark-submit --conf "spark.driver.extraJavaOptions=-javaagent:trace-agent-0.0.8.jar"  --class org.apache.spark.examples.SparkPi --deploy-mode client --master yarn spark-examples_2.11-2.4.5.jar 100 2> /dev/null | grep TraceAgent
TraceAgent (timing): `public scala.Tuple2 org.apache.spark.SparkContext$.org$apache$spark$SparkContext$$createTaskScheduler(org.apache.spark.SparkContext,java.lang.String,java.lang.String)` took 85 ms
```

## Spark driver cluster mode

In case of cluster mode please upload the trace agent jar to HDFS and combine the `spark.jars` and `spark.driver.extraJavaOptions` configuration like this:

```
$ hdfs dfs -put trace-agent-0.0.8.jar /tmp
$ spark-submit --conf "spark.jars=hdfs:/tmp/trace-agent-0.0.8.jar" --conf "spark.driver.extraJavaOptions=-javaagent:trace-agent-0.0.8.jar"  --class org.apache.spark.examples.SparkPi --deploy-mode cluster --master yarn spark-examples_2.11-2.4.5.jar 100
...
20/04/14 19:56:45 INFO yarn.Client: Submitting application application_1586873980905_0010 to ResourceManager
....
$ yarn logs --applicationId application_1586873980905_0010 | grep TraceAgent
WARNING: YARN_OPTS has been replaced by HADOOP_OPTS. Using value of YARN_OPTS.
TraceAgent (timing): `public scala.Tuple2 org.apache.spark.SparkContext$.org$apache$spark$SparkContext$$createTaskScheduler(org.apache.spark.SparkContext,java.lang.String,java.lang.String)` took 65 ms
```

## Spark executor

For example if we would like to measure the `onConnected` method of `CoarseGrainedExecutorBackend` then the actions must be:

```
elapsed_time_in_ms org.apache.spark.executor.CoarseGrainedExecutorBackend onConnected
```

The jar on the HDFS must be refreshed and the submit should be called with `spark.jars` and `spark.executor.extraJavaOptions` configs, like this:

```
$ spark-submit --conf "spark.jars=hdfs:/tmp/trace-agent-0.0.8.jar" --conf "spark.executor.extraJavaOptions=-javaagent:trace-agent-0.0.8.jar"  --class org.apache.spark.examples.SparkPi --deploy-mode cluster --master yarn spark-examples_2.11-2.4.5.jar 100
...
20/04/14 20:27:05 INFO impl.YarnClientImpl: Submitted application application_1586873980905_0012
...
$ yarn logs --applicationId application_1586873980905_0012 | grep TraceAgent
WARNING: YARN_OPTS has been replaced by HADOOP_OPTS. Using value of YARN_OPTS.
TraceAgent (timing): `public void org.apache.spark.executor.CoarseGrainedExecutorBackend.onConnected(org.apache.spark.rpc.RpcAddress)` took 0 ms
TraceAgent (timing): `public void org.apache.spark.executor.CoarseGrainedExecutorBackend.onConnected(org.apache.spark.rpc.RpcAddress)` took 0 ms
TraceAgent (timing): `public void org.apache.spark.executor.CoarseGrainedExecutorBackend.onConnected(org.apache.spark.rpc.RpcAddress)` took 0 ms
TraceAgent (timing): `public void org.apache.spark.executor.CoarseGrainedExecutorBackend.onConnected(org.apache.spark.rpc.RpcAddress)` took 1 ms
TraceAgent (timing): `public void org.apache.spark.executor.CoarseGrainedExecutorBackend.onConnected(org.apache.spark.rpc.RpcAddress)` took 0 ms
TraceAgent (timing): `public void org.apache.spark.executor.CoarseGrainedExecutorBackend.onConnected(org.apache.spark.rpc.RpcAddress)` took 0 ms
TraceAgent (timing): `public void org.apache.spark.executor.CoarseGrainedExecutorBackend.onConnected(org.apache.spark.rpc.RpcAddress)` took 0 ms
TraceAgent (timing): `public void org.apache.spark.executor.CoarseGrainedExecutorBackend.onConnected(org.apache.spark.rpc.RpcAddress)` took 0 ms
TraceAgent (timing): `public void org.apache.spark.executor.CoarseGrainedExecutorBackend.onConnected(org.apache.spark.rpc.RpcAddress)` took 0 ms
TraceAgent (timing): `public void org.apache.spark.executor.CoarseGrainedExecutorBackend.onConnected(org.apache.spark.rpc.RpcAddress)` took 0 ms
TraceAgent (timing): `public void org.apache.spark.executor.CoarseGrainedExecutorBackend.onConnected(org.apache.spark.rpc.RpcAddress)` took 0 ms
TraceAgent (timing): `public void org.apache.spark.executor.CoarseGrainedExecutorBackend.onConnected(org.apache.spark.rpc.RpcAddress)` took 0 ms
TraceAgent (timing): `public void org.apache.spark.executor.CoarseGrainedExecutorBackend.onConnected(org.apache.spark.rpc.RpcAddress)` took 0 ms
TraceAgent (timing): `public void org.apache.spark.executor.CoarseGrainedExecutorBackend.onConnected(org.apache.spark.rpc.RpcAddress)` took 0 ms
TraceAgent (timing): `public void org.apache.spark.executor.CoarseGrainedExecutorBackend.onConnected(org.apache.spark.rpc.RpcAddress)` took 0 ms
TraceAgent (timing): `public void org.apache.spark.executor.CoarseGrainedExecutorBackend.onConnected(org.apache.spark.rpc.RpcAddress)` took 0 ms
TraceAgent (timing): `public void org.apache.spark.executor.CoarseGrainedExecutorBackend.onConnected(org.apache.spark.rpc.RpcAddress)` took 0 ms
TraceAgent (timing): `public void org.apache.spark.executor.CoarseGrainedExecutorBackend.onConnected(org.apache.spark.rpc.RpcAddress)` took 0 ms
TraceAgent (timing): `public void org.apache.spark.executor.CoarseGrainedExecutorBackend.onConnected(org.apache.spark.rpc.RpcAddress)` took 0 ms
TraceAgent (timing): `public void org.apache.spark.executor.CoarseGrainedExecutorBackend.onConnected(org.apache.spark.rpc.RpcAddress)` took 0 ms
TraceAgent (timing): `public void org.apache.spark.executor.CoarseGrainedExecutorBackend.onConnected(org.apache.spark.rpc.RpcAddress)` took 0 ms
TraceAgent (timing): `public void org.apache.spark.executor.CoarseGrainedExecutorBackend.onConnected(org.apache.spark.rpc.RpcAddress)` took 0 ms
TraceAgent (timing): `public void org.apache.spark.executor.CoarseGrainedExecutorBackend.onConnected(org.apache.spark.rpc.RpcAddress)` took 0 ms
TraceAgent (timing): `public void org.apache.spark.executor.CoarseGrainedExecutorBackend.onConnected(org.apache.spark.rpc.RpcAddress)` took 0 ms
TraceAgent (timing): `public void org.apache.spark.executor.CoarseGrainedExecutorBackend.onConnected(org.apache.spark.rpc.RpcAddress)` took 0 ms
```

## Cloudera CDE

Here is how you can use Trace Agent with Cloudera CDE Spark jobs and specify an external `actions.txt` file:

```
cde spark submit spark-examples_2.11-2.4.7.7.2.10.0-120.jar 10 --class org.apache.spark.examples.SparkPi --job-name test-job-1 --tls-insecure --conf "spark.driver.extraJavaOptions=-javaagent:/app/mount/trace-agent-1.0-SNAPSHOT.jar=actionsFile:/app/mount/actions.txt" --jar trace-agent-1.0-SNAPSHOT.jar --file actions.txt
```

This method works with executors as well, in which case `spark.executor.extraJavaOptions` needs to be set instead of `spark.driver.extraJavaOptions`.

# Replacing actions directly into the jar

```bash
# Create or use already created actions.txt file
echo "elapsed_time_in_ms org.apache.spark.executor.CoarseGrainedExecutorBackend onConnected" > actions.txt

# Replace the actions file in the jar
jar uf trace-agent-1.0-SNAPSHOT.jar actions.txt

# done
```
