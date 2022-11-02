package gc.noop;

/**
 * @test TestDieDefault
 * @summary Noop GC should die on heap exhaustion
 * @library /test/lib
 * @run driver gc.noop.TestDieDefault
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestDieDefault {

  public static void passWith(String... args) throws Exception {
    ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(args);
    OutputAnalyzer out = new OutputAnalyzer(pb.start());
    out.shouldNotContain("OutOfMemoryError");
    out.shouldHaveExitValue(0);
  }

  public static void failWith(String... args) throws Exception {
    ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(args);
    OutputAnalyzer out = new OutputAnalyzer(pb.start());
    out.shouldContain("OutOfMemoryError");
    if (out.getExitValue() == 0) {
      throw new IllegalStateException("Should have failed with non-zero exit code");
    }
  }

  public static void main(String[] args) throws Exception {
    passWith("-Xmx64m",
             "-XX:+UnlockExperimentalVMOptions",
             "-XX:+UseNoopGC",
             "-Dcount=1",
             TestDieDefault.Workload.class.getName());

    failWith("-Xmx64m",
             "-XX:+UnlockExperimentalVMOptions",
             "-XX:+UseNoopGC",
             TestDieDefault.Workload.class.getName());

    failWith("-Xmx64m",
             "-Xint",
             "-XX:+UnlockExperimentalVMOptions",
             "-XX:+UseNoopGC",
             TestDieDefault.Workload.class.getName());

    failWith("-Xmx64m",
             "-Xbatch",
             "-Xcomp",
             "-XX:TieredStopAtLevel=1",
             "-XX:+UnlockExperimentalVMOptions",
             "-XX:+UseNoopGC",
             TestDieDefault.Workload.class.getName());

    failWith("-Xmx64m",
             "-Xbatch",
             "-Xcomp",
             "-XX:-TieredCompilation",
             "-XX:+UnlockExperimentalVMOptions",
             "-XX:+UseNoopGC",
             TestDieDefault.Workload.class.getName());
  }

  public static class Workload {
    static int COUNT = Integer.getInteger("count", 1_000_000_000); // ~24 GB allocation

    static volatile Object sink;

    public static void main(String... args) {
      for (int c = 0; c < COUNT; c++) {
        sink = new Object();
      }
    }
  }

}