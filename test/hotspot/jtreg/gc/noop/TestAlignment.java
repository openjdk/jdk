package gc.noop;

/**
 * @test TestAlignment
 * @summary Check Noop runs fine with standard alignments
 *
 * @run main/othervm -Xmx64m -XX:+UseTLAB
 *                   -XX:+UnlockExperimentalVMOptions -XX:+UseNoopGC
 *                   gc.noop.TestAlignment
 *
 * @run main/othervm -Xmx64m -XX:-UseTLAB
 *                   -XX:+UnlockExperimentalVMOptions -XX:+UseNoopGC
 *                   gc.noop.TestAlignment
 */

/**
 * @test TestAlignment
 * @requires vm.bits == "64"
 * @summary Check Noop TLAB options with unusual object alignment
 * @run main/othervm -Xmx64m -XX:+UseTLAB
 *                   -XX:+UnlockExperimentalVMOptions -XX:+UseNoopGC
 *                   -XX:ObjectAlignmentInBytes=16
 *                   gc.noop.TestAlignment
 *
 * @run main/othervm -Xmx64m -XX:-UseTLAB
 *                   -XX:+UnlockExperimentalVMOptions -XX:+UseNoopGC
 *                   -XX:ObjectAlignmentInBytes=16
 *                   gc.noop.TestAlignment
 */

public class TestAlignment {
    static Object sink;

    public static void main(String[] args) throws Exception {
        for (int c = 0; c < 1000; c++) {
            sink = new byte[c];
        }
    }
}