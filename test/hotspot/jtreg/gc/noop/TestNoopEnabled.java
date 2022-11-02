package gc.noop;

/**
 * @test TestNoopEnabled
 * @summary Basic sanity test for Noop
 * @library /test/lib
 *
 * @run main/othervm -Xmx256m
 *                   -XX:+UnlockExperimentalVMOptions -XX:+UseNoopGC
 *                   gc.noop.TestNoopEnabled
 */

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;

public class TestNoopEnabled {
  public static void main(String[] args) throws Exception {
    if (!isNoopEnabled()) {
      throw new IllegalStateException("Debug builds should have Noop enabled");
    }
  }

  public static boolean isNoopEnabled() {
    for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
      if (bean.getName().contains("Noop")) {
        return true;
      }
    }
    return false;
  }
}