package compiler.c2.irTests;

import jdk.test.lib.Asserts;
import compiler.lib.ir_framework.*;

/*
 * @test
 * @library /test/lib /
 * @run driver compiler.c2.irTests.URShiftINodeIdealizationTests
 */
public class URShiftINodeIdealizationTests {

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Run(test = { "test1", "test2" })
    public void runMethod() {
        int a = RunInfo.getRandom().nextInt();

        int min = Integer.MIN_VALUE;
        int max = Integer.MAX_VALUE;

        assertResult(0);
        assertResult(a);
        assertResult(min);
        assertResult(max);
    }

    @DontCompile
    public void assertResult(int a) {
        Asserts.assertEQ((a << 2022) >>> 2022, test1(a));
        Asserts.assertEQ((a >> 2022) >>> 31, test2(a));
    }

    @Test
    @IR(failOn = { IRNode.LSHIFT, IRNode.URSHIFT })
    @IR(counts = { IRNode.AND, "1" })
    // Checks (x << 2022) >>> 2022 => x & C where C = ((1 << (32 - 6)) - 1)
    public int test1(int x) {
        return (x << 2022) >>> 2022;
    }

    @Test
    @IR(failOn = { IRNode.RSHIFT })
    @IR(counts = { IRNode.URSHIFT, "1" })
    // Checks (x >> 2022) >>> 31 => x >>> 31
    public int test2(int x) {
        return (x >> 2022) >>> 31;
    }
}
