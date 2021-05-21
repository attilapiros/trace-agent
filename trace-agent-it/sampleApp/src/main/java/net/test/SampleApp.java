package net.test;

public class SampleApp {
  public static void main(String[] args) {
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
    } catch (Exception e) {
    }
  }
}

class TestClass2nd {

  public void anotherMethod() {
    System.out.println("2nd Hello World!");
    try {
      Thread.sleep(100);
    } catch (Exception e) {
    }
  }

  public int methodWithArgs(String str, int i) {
    System.out.println("methodWithArgs");
    return 12;
  }
}
