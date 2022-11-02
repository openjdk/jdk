package gc.noop;

/**
 * @test TestAlwaysPretouch
 * @summary Test that pre-touch works
 *
 * @run main/othervm -Xms64m -Xmx256m
 *                   -XX:+UnlockExperimentalVMOptions -XX:+UseNoopGC
 *                   gc.noop.TestAlwaysPretouch
 *
 * @run main/othervm -Xms64m -Xmx256m -XX:-AlwaysPreTouch
 *                   -XX:+UnlockExperimentalVMOptions -XX:+UseNoopGC
 *                   gc.noop.TestAlwaysPretouch
 *
 * @run main/othervm -Xms64m -Xmx256m -XX:+AlwaysPreTouch
 *                   -XX:+UnlockExperimentalVMOptions -XX:+UseNoopGC
 *                   gc.noop.TestAlwaysPretouch
 *
 * @run main/othervm -Xmx256m
 *                   -XX:+UnlockExperimentalVMOptions -XX:+UseNoopGC
 *                   gc.noop.TestAlwaysPretouch
 *
 * @run main/othervm -Xmx256m -XX:-AlwaysPreTouch
 *                   -XX:+UnlockExperimentalVMOptions -XX:+UseNoopGC
 *                   gc.noop.TestAlwaysPretouch
 *
 * @run main/othervm -Xmx256m -XX:+AlwaysPreTouch
 *                   -XX:+UnlockExperimentalVMOptions -XX:+UseNoopGC
 *                   gc.noop.TestAlwaysPretouch
 */

public class TestAlwaysPretouch {
  public static void main(String[] args) throws Exception {}
}
