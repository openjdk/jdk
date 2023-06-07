public class DynamicOldChild extends DynamicOldSuper {

  public DynamicOldChild() {}

  public void bar() {
    System.out.println("Child.bar");
  }

  public static void main(String[] args) {
    DynamicOldSuper s = new DynamicOldChild();
    s.foo();
    s.bar();
  }

}
