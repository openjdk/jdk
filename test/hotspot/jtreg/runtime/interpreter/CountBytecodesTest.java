
/*
 * @test
 * @requires vm.debug & vm.bits == "64"
 * @summary Test the output for CountBytecodes and validate that the counter
 *          does not overflow for more than 2^32 bytecodes counted.
 * @library /test/lib
 * @run main/othervm/timeout=300 -Xint -XX:+CountBytecodes CountBytecodesTest
 */

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.test.lib.Asserts;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class CountBytecodesTest {
    private final static long iterations = 1L << 32;

    public static void main(String args[]) throws Exception {
        if (args.length == 1 && args[0].equals("test")) {
            for (long i = 0; i < iterations; i++) {
                // Just iterating is enough to execute and count bytecodes.
            }
        } else {
            ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder("-Xint", "-XX:+CountBytecodes", "CountBytecodesTest", "test");
            OutputAnalyzer output = new OutputAnalyzer(pb.start());
            output.shouldHaveExitValue(0);

            // Output format: [BytecodeCounter::counter_value = 38676232802]
            output.stdoutShouldContain("BytecodeCounter::counter_value");
            String bytecodesStr = output.firstMatch("BytecodeCounter::counter_value\s*=\s*(\\d+)", 1);
            long bytecodes = Long.parseLong(bytecodesStr);

            Asserts.assertGTE(bytecodes, 4294967296L);
        }
    }
}
