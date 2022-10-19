package gc.noop;

/**
 * @test TestMaxTLAB
 * @summary Check NoopMaxTLAB options
 *
 * @run main/othervm -Xmx64m
 *                   -XX:+UnlockExperimentalVMOptions -XX:+UseNoopGC
 *                   -XX:NoopMaxTLABSize=1
 *                   gc.noop.TestMaxTLAB
 *
 * @run main/othervm -Xmx64m
 *                   -XX:+UnlockExperimentalVMOptions -XX:+UseNoopGC
 *                   -XX:NoopMaxTLABSize=1K
 *                   gc.noop.TestMaxTLAB
 *
 * @run main/othervm -Xmx64m
 *                   -XX:+UnlockExperimentalVMOptions -XX:+UseNoopGC
 *                   -XX:NoopMaxTLABSize=1M
 *                   gc.noop.TestMaxTLAB
 *
 * @run main/othervm -Xmx64m
 *                   -XX:+UnlockExperimentalVMOptions -XX:+UseNoopGC
 *                   -XX:NoopMaxTLABSize=12345
 *                   gc.noop.TestMaxTLAB
 */

/**
 * @test TestMaxTLAB
 * @requires vm.bits == "64"
 * @summary Check NoopMaxTLAB options with different alignment
 *
 * @run main/othervm -Xmx64m
 *                   -XX:+UnlockExperimentalVMOptions -XX:+UseNoopGC
 *                   -XX:NoopMaxTLABSize=1
 *                   -XX:ObjectAlignmentInBytes=16
 *                   gc.noop.TestMaxTLAB
 *
 * @run main/othervm -Xmx64m
 *                   -XX:+UnlockExperimentalVMOptions -XX:+UseNoopGC
 *                   -XX:NoopMaxTLABSize=1K
 *                   -XX:ObjectAlignmentInBytes=16
 *                   gc.noop.TestMaxTLAB
 *
 * @run main/othervm -Xmx64m
 *                   -XX:+UnlockExperimentalVMOptions -XX:+UseNoopGC
 *                   -XX:NoopMaxTLABSize=1M
 *                   -XX:ObjectAlignmentInBytes=16
 *                   gc.noop.TestMaxTLAB
 *
 * @run main/othervm -Xmx64m
 *                   -XX:+UnlockExperimentalVMOptions -XX:+UseNoopGC
 *                   -XX:NoopMaxTLABSize=12345
 *                   -XX:ObjectAlignmentInBytes=16
 *                   gc.noop.TestMaxTLAB
 */

public class TestMaxTLAB {
    static Object sink;

    public static void main(String[] args) throws Exception {
        for (int c = 0; c < 1000; c++) {
            sink = new byte[c];
        }
    }
}