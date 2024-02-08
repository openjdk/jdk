package compiler.c2;

import compiler.lib.ir_framework.Argument;
import compiler.lib.ir_framework.Arguments;
import compiler.lib.ir_framework.CompilePhase;
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.Test;
import compiler.lib.ir_framework.TestFramework;

/**
 * @test
 * @summary
 * @library /test/lib /
 * @run driver compiler.c2.TestBoolNodeGvn
 */
public class TestBoolNodeGvn {
    public static void main(String[] args) {
        TestFramework.run();
    }

    /**
     * Test changing ((x & m) u<= m) or ((m & x) u<= m) to always true, same with ((x & m) u< m+1) and ((m & x) u< m+1)
     * The test is not applicable to x86 (32bit) for not having <code>Integer.compareUnsigned</code> intrinsified.
     */
    @Test
    @Arguments({Argument.DEFAULT, Argument.DEFAULT})
    @IR(failOn = IRNode.CMP_U, phase = CompilePhase.AFTER_PARSING, applyIfPlatform = {"x86", "false"})
    public static boolean test(int x, int m) {
        return Integer.compareUnsigned((x & m), m) > 0
                & Integer.compareUnsigned((m & x), m) > 0
                & Integer.compareUnsigned((x & m), m + 1) < 0
                & Integer.compareUnsigned((m & x), m + 1) < 0;
    }
}
