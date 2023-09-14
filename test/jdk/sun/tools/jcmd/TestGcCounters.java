import static jdk.test.lib.Asserts.*;

import jdk.test.lib.process.OutputAnalyzer;

/*
 * @test
 * @bug 8315149
 * @summary Unit test to ensure CPU GC hsperf counters are published.
 *
 * @library /test/lib
 *
 * @run main/othervm -XX:+UsePerfData TestGcCounters
 */
public class TestGcCounters {

    private static final String[] VM_ARGS = new String[] { "-XX:+UsePerfData" };

    public static void main(String[] args) throws Exception {
        testGcCpuCountersExist();
    }


    /**
     * jcmd -J-XX:+UsePerfData pid PerfCounter.print
     */
     private static void testGcCpuCountersExist() throws Exception {
        OutputAnalyzer output = JcmdBase.jcmd(VM_ARGS,
                new String[] {"PerfCounter.print"});

        output.shouldHaveExitValue(0);
        output.shouldContain("sun.threads.gc_cpu_time");
        output.shouldContain("sun.threads.gc_cpu_time.g1_conc_mark");
        output.shouldContain("sun.threads.gc_cpu_time.g1_conc_refine");
        output.shouldContain("sun.threads.gc_cpu_time.parallel_gc_workers");
        output.shouldContain("sun.threads.gc_cpu_time.vm");
    }
}

