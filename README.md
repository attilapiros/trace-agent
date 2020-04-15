# Trace Agent

A java agent for tracing which configurable by an embedded simple text file.
As the config file is resource within the agent jar no rebuild is needed for tracing methods.


# The example

It is much easier to understand how this can be used if I show you it through an example.

## The project we would like to trace

Let's say we have a project what we would like analyze. In this example its code very simple:

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


## The config format

The config format is simple lines with the following structure:

```
<action-name> <class-name> <method-name>
```

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

## Some complex examples how to specify a javaagent

Although trace agent is a general tool I would like to write up some use cases where this tool can be useful for you
(and also for myself for future reference).

### For JVM based languages other than Java (Scala, Clojure, Kotlin, ...)

If you can run an experiment you can use the regexp based matching to find out what pattern you should use exactly.
When experimenting is not possible then you can use `javap` to find out what will be the final class and method name.

#### Example

For example in case of a Spark Core method (which uses Scala) this can be done as follows. Let's say you would like to match for
[createTaskScheduler](https://github.com/apache/spark/blob/master/core/src/main/scala/org/apache/spark/SparkContext.scala#L2757).
First you should find out the class file. As from a Scala object the compiler generates a class which ends with `$` in our case
this will be `org.apache.spark.SparkContext$.class` (as the object fully qualified name is `org.apache.spark.SparkContext`).

Now with `javap` the exact method name can be find out easily, like:

```
$ unzip -p jars/spark-core_2.11-2.4.5.jar org/apache/spark/SparkContext$.class > SparkContext$.class                                                                                                  1 ↵
$ javap -p SparkContext$.class | grep createTaskScheduler
  public scala.Tuple2<org.apache.spark.scheduler.SchedulerBackend, org.apache.spark.scheduler.TaskScheduler> org$apache$spark$SparkContext$$createTaskScheduler(org.apache.spark.SparkContext, java.lang.String, java.lang.String);
```

So to measure the elapsed time within this method the actions can be:

```
elapsed_time_in_ms org.apache.spark.SparkContext$ org$apache$spark$SparkContext$$createTaskScheduler
```

### Spark driver client mode

In case of client mode when the driver is at node where you call spark-submit at you can simply start Spark with the config
`spark.driver.extraJavaOptions` where you can specify `-javagent` with the trace-agent jar location:

Example (when you are in the same directory where the trace-agent jar is stored):

```
$ spark-submit --conf "spark.driver.extraJavaOptions=-javaagent:trace-agent-0.0.2.jar"  --class org.apache.spark.examples.SparkPi --deploy-mode client --master yarn spark-examples_2.11-2.4.5.jar 100 2> /dev/null | grep TraceAgent
TraceAgent (timing): `public scala.Tuple2 org.apache.spark.SparkContext$.org$apache$spark$SparkContext$$createTaskScheduler(org.apache.spark.SparkContext,java.lang.String,java.lang.String)` took 85 ms
```

### Spark driver cluster mode

In case of cluster mode please upload the trace agent jar to HDFS and combine the `spark.jars` and `spark.driver.extraJavaOptions` configuration like this:

```
$ hdfs dfs -put trace-agent-0.0.2.jar /tmp
$ spark-submit --conf "spark.jars=hdfs:/tmp/trace-agent-0.0.2.jar" --conf "spark.driver.extraJavaOptions=-javaagent:trace-agent-0.0.2.jar"  --class org.apache.spark.examples.SparkPi --deploy-mode cluster --master yarn spark-examples_2.11-2.4.5.jar 100
...
20/04/14 19:56:45 INFO yarn.Client: Submitting application application_1586873980905_0010 to ResourceManager
....
$ yarn logs --applicationId application_1586873980905_0010 | grep TraceAgent
WARNING: YARN_OPTS has been replaced by HADOOP_OPTS. Using value of YARN_OPTS.
TraceAgent (timing): `public scala.Tuple2 org.apache.spark.SparkContext$.org$apache$spark$SparkContext$$createTaskScheduler(org.apache.spark.SparkContext,java.lang.String,java.lang.String)` took 65 ms
```

### Spark executor

For example if we would like to measure the `onConnected` method of `CoarseGrainedExecutorBackend` then the actions must be:

```
elapsed_time_in_ms org.apache.spark.executor.CoarseGrainedExecutorBackend onConnected
```

The jar on the HDFS must be refreshed and the submit should be called with `spark.jars` and `spark.executor.extraJavaOptions` configs, like this:

```
$ spark-submit --conf "spark.jars=hdfs:/tmp/trace-agent-0.0.2.jar" --conf "spark.executor.extraJavaOptions=-javaagent:trace-agent-0.0.2.jar"  --class org.apache.spark.examples.SparkPi --deploy-mode cluster --master yarn spark-examples_2.11-2.4.5.jar 100
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
