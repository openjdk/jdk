package compiler.gcbarriers;

import compiler.lib.ir_framework.CompilePhase;
import compiler.lib.ir_framework.DontInline;
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.Test;
import compiler.lib.ir_framework.TestFramework;

/**
 * @test
 * @bug 8231569
 * @summary Test that Shenandoah barriers are expanded correctly
 * @library /test/lib /
 * @requires vm.gc.Shenandoah
 * @run main compiler.gcbarriers.TestShenandoahBarrierExpansion
 */
public class TestShenandoahBarrierExpansion {
    public static void main(String[] args) {
        TestFramework test = new TestFramework(TestShenandoahBarrierExpansion.class);
        test.addFlags("-XX:+UseShenandoahGC");
        test.start();
    }

    private static Object staticField;
    @Test
    @IR(failOn = IRNode.IF, phase = CompilePhase.MACRO_EXPANSION)
    @IR(counts = { IRNode.IF, "2" }, phase = CompilePhase.BARRIER_EXPANSION)
    public Object testLoadFieldObject() {
        return staticField;
    }

    private static A staticField2 = new A();
    @Test
    @IR(counts = { IRNode.IF, "1" }, phase = CompilePhase.MACRO_EXPANSION)
    @IR(counts = { IRNode.IF, "3" }, phase = CompilePhase.BARRIER_EXPANSION)
    private static int testLoadObjectFieldWithNullCheck() {
        return staticField2.intField;
    }

    private static A staticField3 = new A();
    @Test
    @IR(counts = { IRNode.IF, "2" }, phase = CompilePhase.MACRO_EXPANSION)
    @IR(counts = { IRNode.IF, "6" }, phase = CompilePhase.BARRIER_EXPANSION)
    private static int testLoadTwoObjectFieldsWithNullCheck() {
        return staticField2.intField + staticField3.intField;
    }

    @Test
    @IR(failOn = IRNode.IF, phase = CompilePhase.MACRO_EXPANSION)
    @IR(counts = { IRNode.IF, "3" }, phase = CompilePhase.BARRIER_EXPANSION)
    private static void testLoadTwoFieldObjectAndEscape() {
        final A field2 = staticField2;
        final A field3 = staticField3;
        notInlined(field2, field3);
    }

    @DontInline
    private static void notInlined(A field2, A field3) {
        // noop
    }

    private static class A {
        public int intField;
    }
}
