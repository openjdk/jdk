
/*
 * @test
 * @requires vm.debug & vm.bits == "64"
 * @summary this is a summary
 * @library /test/lib
 * @run main/othervm/timeout=300 -Xint -XX:+CountBytecodes CountBytecodesTest
 */

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.test.lib.Asserts;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class CountBytecodesTest {
    private final static long iterations = 1L << 32; // Exceed 32 bit.

    public static void main(String args[]) throws Exception {
        if (args.length == 1 && args[0].equals("test")) {
            System.out.println("### OUTPUT: " + iterations);
            for (long i = 0; i < iterations; i++) {
                // Just iterating is enough to execute and count bytecodes.
            }
        } else {
            ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder("-Xint", "-XX:+CountBytecodes", "CountBytecodesTest", "test");
            OutputAnalyzer output = new OutputAnalyzer(pb.start());
            output.shouldHaveExitValue(0);
            output.stdoutShouldContain("BytecodeCounter::counter_value");
            Pattern counterRegex = Pattern.compile("BytecodeCounter::counter_value\s*=\s*\\d+");
            Matcher counterMatcher = counterRegex.matcher(output.getStdout());
            Asserts.assertTrue(counterMatcher.find());
            String resultLine = counterMatcher.group();

            // TODO: find right group and convert to number


            // [BytecodeCounter::counter_value = 38676232802]
            // System.out.println("stdout: " + output.getStdout());
            // System.out.println("stderr: " + output.getStderr());
        }
    }
}
