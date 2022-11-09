package compiler.c2.irTests;

import jdk.test.lib.Asserts;
import compiler.lib.ir_framework.*;

/*
 * @test
 * @library /test/lib /
 * @run driver compiler.c2.irTests.URShiftLNodeIdealizationTests
 */
public class URShiftLNodeIdealizationTests {

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Run(test = { "test1", "test2" })
    public void runMethod() {
        long a = RunInfo.getRandom().nextInt();

        long min = Long.MIN_VALUE;
        long max = Long.MAX_VALUE;

        assertResult(0);
        assertResult(a);
        assertResult(min);
        assertResult(max);
    }

    @DontCompile
    public void assertResult(long a) {
        Asserts.assertEQ((a << 2022) >>> 2022, test1(a));
        Asserts.assertEQ((a >> 2022) >>> 63, test2(a));
    }

    @Test
    @IR(failOn = { IRNode.LSHIFT, IRNode.URSHIFT })
    @IR(counts = { IRNode.AND, "1" })
    // Checks (x << 2022) >>> 2022 => x & C where C = ((1 << (64 - 38)) - 1)
    public long test1(long x) {
        return (x << 2022) >>> 2022;
    }

    @Test
    @IR(failOn = { IRNode.RSHIFT })
    @IR(counts = { IRNode.URSHIFT, "1" })
    // Checks (x >> 2022) >>> 63 => x >>> 63
    public long test2(long x) {
        return (x >> 2022) >>> 63;
    }
}
