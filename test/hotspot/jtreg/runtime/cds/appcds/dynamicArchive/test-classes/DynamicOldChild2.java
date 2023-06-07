public class DynamicOldChild2 implements DynamicOldInterface {

    public DynamicOldChild2(){}

    public void bar() {
      System.out.println("Child.bar");
    }

    public void foo() {
        System.out.println("Child.foo");
    }

    public static void main(String[] args) {
      DynamicOldInterface oi = new DynamicOldChild2();
      oi.foo();
      oi.bar();
    }

  }
