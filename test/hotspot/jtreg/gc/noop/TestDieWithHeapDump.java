package gc.noop;

/**
 * @test TestDieWithHeapDump
 * @summary Noop GC should die on heap exhaustion with error handler attached
 * @library /test/lib
 * @run driver gc.noop.TestDieWithHeapDump
 */

import java.io.*;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestDieWithHeapDump {

  public static void passWith(String... args) throws Exception {
    ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(args);
    OutputAnalyzer out = new OutputAnalyzer(pb.start());
    out.shouldNotContain("OutOfMemoryError");
    out.shouldHaveExitValue(0);
  }

  public static void failWith(String... args) throws Exception {
    ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(args);
    Process p = pb.start();
    OutputAnalyzer out = new OutputAnalyzer(p);
    out.shouldContain("OutOfMemoryError");
    if (out.getExitValue() == 0) {
      throw new IllegalStateException("Should have failed with non-zero exit code");
    }
    String heapDump = "java_pid" + p.pid() + ".hprof";
    if (!new File(heapDump).exists()) {
      throw new IllegalStateException("Should have produced the heap dump at: " + heapDump);
    }
  }

  public static void main(String[] args) throws Exception {
    passWith("-Xmx64m",
             "-XX:+UnlockExperimentalVMOptions",
             "-XX:+UseNoopGC",
             "-Dcount=1",
             "-XX:+HeapDumpOnOutOfMemoryError",
             TestDieWithHeapDump.Workload.class.getName());

    failWith("-Xmx64m",
             "-XX:+UnlockExperimentalVMOptions",
             "-XX:+UseNoopGC",
             "-XX:+HeapDumpOnOutOfMemoryError",
             TestDieWithHeapDump.Workload.class.getName());

    failWith("-Xmx64m",
             "-Xint",
             "-XX:+UnlockExperimentalVMOptions",
             "-XX:+UseNoopGC",
             "-XX:+HeapDumpOnOutOfMemoryError",
             TestDieWithHeapDump.Workload.class.getName());

    failWith("-Xmx64m",
             "-Xbatch",
             "-Xcomp",
             "-XX:TieredStopAtLevel=1",
             "-XX:+UnlockExperimentalVMOptions",
             "-XX:+UseNoopGC",
             "-XX:+HeapDumpOnOutOfMemoryError",
             TestDieWithHeapDump.Workload.class.getName());

    failWith("-Xmx64m",
             "-Xbatch",
             "-Xcomp",
             "-XX:-TieredCompilation",
             "-XX:+UnlockExperimentalVMOptions",
             "-XX:+UseNoopGC",
             "-XX:+HeapDumpOnOutOfMemoryError",
             TestDieWithHeapDump.Workload.class.getName());
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