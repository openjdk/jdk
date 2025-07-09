package jdk.jfr.event.gc.objectcount;
import jdk.test.lib.jfr.GCHelper;

/**
 * @test
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @requires (vm.gc == "Shenandoah" | vm.gc == null)
 *           & vm.opt.ExplicitGCInvokesConcurrent != false
 * @library /test/lib /test/jdk
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:+ExplicitGCInvokesConcurrent -XX:MarkSweepDeadRatio=0 -XX:-UseCompressedOops -XX:-UseCompressedClassPointers -XX:+IgnoreUnrecognizedVMOptions jdk.jfr.event.gc.objectcount.TestObjectCountEventWithShenandoah
 */
public class TestObjectCountEventWithShenandoah {
    public static void main(String[] args) throws Exception {
        ObjectCountAfterGCEvent.test(GCHelper.gcShenandoah);
    }
}
