
package jdk.jfr.event.gc.objectcount;

import jdk.test.lib.jfr.GCHelper;

/**
 * @test
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @requires (vm.gc == "G1" | vm.gc == null)
 *           & vm.opt.ExplicitGCInvokesConcurrent != true
 * @library /test/lib /test/jdk
 * @run main/othervm -XX:+UnlockExperimentalVMOptions
 *      -XX:-UseFastUnorderedTimeStamps -XX:+UseG1GC -XX:MarkSweepDeadRatio=0
 *      -XX:-UseCompressedOops -XX:-UseCompressedClassPointers
 *      -XX:+IgnoreUnrecognizedVMOptions
 *      jdk.jfr.event.gc.objectcount.TestObjectCountEventWithG1FullCollection
 */
public class TestObjectCountEventWithG1FullCollection {
    public static void main(String[] args) throws Exception {
        ObjectCountEvent.test(GCHelper.gcG1Full);
    }
}
