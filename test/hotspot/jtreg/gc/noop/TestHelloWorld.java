package gc.noop;

/**
 * @test TestHelloWorld
 * @summary Basic sanity test for Noop
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:+UseNoopGC
 *                   gc.noop.TestHelloWorld
 */

public class TestHelloWorld {
  public static void main(String[] args) throws Exception {
    System.out.println("Hello World");
  }
}