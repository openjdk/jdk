package jdk.jfr.event.gc.objectcount;

import jdk.test.lib.jfr.GCHelper;

/**
 * @test
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @requires vm.gc == "Parallel" | vm.gc == null
 * @library /test/lib /test/jdk
 * @run main/othervm -XX:+UnlockExperimentalVMOptions
 *      -XX:-UseFastUnorderedTimeStamps -XX:+UseParallelGC
 *      -XX:MarkSweepDeadRatio=0 -XX:-UseCompressedOops
 *      -XX:-UseCompressedClassPointers -XX:+IgnoreUnrecognizedVMOptions
 *      jdk.jfr.event.gc.objectcount.TestObjectCountEventWithParallelOld
 */
public class TestObjectCountEventWithParallelOld {
    public static void main(String[] args) throws Exception {
        ObjectCountEvent.test(GCHelper.gcParallelOld);
    }
}
