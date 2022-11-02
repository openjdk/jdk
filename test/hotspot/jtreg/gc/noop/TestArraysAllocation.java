package gc.noop;

/**
 * @test TestArraysAllocation
 * @key randomness
 * @summary Noop is able to allocate arrays, and does not corrupt their state
 * @library /test/lib
 *
 * @run main/othervm -XX:+UseTLAB -Xmx256m
 *                   -XX:+UnlockExperimentalVMOptions -XX:+UseNoopGC
 *                   gc.noop.TestArraysAllocation
 *
 * @run main/othervm -XX:+UseTLAB -Xmx256m
 *                   -Xint
 *                   -XX:+UnlockExperimentalVMOptions -XX:+UseNoopGC
 *                   gc.noop.TestArraysAllocation
 *
 * @run main/othervm -XX:+UseTLAB -Xmx256m
 *                   -Xbatch -Xcomp -XX:TieredStopAtLevel=1
 *                   -XX:+UnlockExperimentalVMOptions -XX:+UseNoopGC
 *                   gc.noop.TestArraysAllocation
 *
 * @run main/othervm -XX:+UseTLAB -Xmx256m
 *                   -Xbatch -Xcomp -XX:-TieredCompilation
 *                   -XX:+UnlockExperimentalVMOptions -XX:+UseNoopGC
 *                   gc.noop.TestArraysAllocation
 *
 * @run main/othervm -XX:-UseTLAB -Xmx256m
 *                   -XX:+UnlockExperimentalVMOptions -XX:+UseNoopGC
 *                   gc.noop.TestArraysAllocation
 *
 * @run main/othervm -XX:-UseTLAB -Xmx256m
 *                   -Xint
 *                   -XX:+UnlockExperimentalVMOptions -XX:+UseNoopGC
 *                   gc.noop.TestArraysAllocation
 *
 * @run main/othervm -XX:-UseTLAB -Xmx256m
 *                   -Xbatch -Xcomp -XX:TieredStopAtLevel=1
 *                   -XX:+UnlockExperimentalVMOptions -XX:+UseNoopGC
 *                   gc.noop.TestArraysAllocation
 *
 * @run main/othervm -XX:-UseTLAB -Xmx256m
 *                   -Xbatch -Xcomp -XX:-TieredCompilation
 *                   -XX:+UnlockExperimentalVMOptions -XX:+UseNoopGC
 *                   gc.noop.TestArraysAllocation
 */

import java.util.Random;
import jdk.test.lib.Utils;

public class TestArraysAllocation {
  static int COUNT = Integer.getInteger("count", 500); // ~100 MB allocation

  static byte[][] arr;

  public static void main(String[] args) throws Exception {
    Random r = Utils.getRandomInstance();

    arr = new byte[COUNT * 100][];
    for (int c = 0; c < COUNT; c++) {
      arr[c] = new byte[c * 100];
      for (int v = 0; v < c; v++) {
        arr[c][v] = (byte)(r.nextInt(255) & 0xFF);
      }
    }

    r = new Random(Utils.SEED);
    for (int c = 0; c < COUNT; c++) {
      byte[] b = arr[c];
      if (b.length != (c * 100)) {
        throw new IllegalStateException("Failure: length = " + b.length + ", need = " + (c*100));
      }
      for (int v = 0; v < c; v++) {
        byte actual = b[v];
        byte expected = (byte)(r.nextInt(255) & 0xFF);
        if (actual != expected) {
          throw new IllegalStateException("Failure: expected = " + expected + ", actual = " + actual);
        }
      }
    }
  }
}