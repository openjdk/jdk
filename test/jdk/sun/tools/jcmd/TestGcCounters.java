import static jdk.test.lib.Asserts.*;

import jdk.test.lib.process.OutputAnalyzer;

/*
 * @test
 * @bug 8315149
 * @summary Unit test to ensure CPU GC hsperf counters are published.
 * @requires vm.gc.G1
 *
 * @library /test/lib
 *
 * @run main/othervm TestGcCounters
 */
public class TestGcCounters {

    private static final String[] VM_ARGS = new String[] { "-XX:+UsePerfData" };
    private static final String SUN_THREADS = "sun.threads";
    private static final String SUN_THREADS_GCCPU = "sun.threads.gc_cpu_time";

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
        output.shouldContain(SUN_THREADS + ".total_gc_cpu_time");
        output.shouldContain(SUN_THREADS_GCCPU + ".g1_conc_mark");
        output.shouldContain(SUN_THREADS_GCCPU + ".g1_conc_refine");
        output.shouldContain(SUN_THREADS_GCCPU + ".parallel_gc_workers");
        output.shouldContain(SUN_THREADS_GCCPU + ".vm");
    }
}

