package compiler.c2.arithmeticCanonicalization;

import compiler.lib.ir_framework.*;

/*
 * @test
 * @bug 8325495
 * @summary C2 should optimize for series of Add of unique value. e.g., a + a + ... + a => a * n
 * @library /test/lib /
 * @run driver compiler.c2.arithmeticCanonicalization.SerialAdditionCanonicalization
 */
public class SerialAdditionCanonicalization {
    public static void main(String[] args) {
        TestFramework.run();
    }

    @Test
    @Arguments(values = {Argument.RANDOM_ONCE})
    @IR(counts = { IRNode.ADD_I, "1" })
    private static int addTo3(int a) {
        return a + a + a; // a<<1 + a
    }

    @Test
    @Arguments(values = {Argument.RANDOM_ONCE})
    @IR(failOn = IRNode.ADD_I)
    private static int addTo4(int a) {
        return a + a + a + a; // a<<2
    }

    @Test
    @Arguments(values = {Argument.RANDOM_ONCE})
    @IR(failOn = IRNode.ADD_I)
    private static int mulAndAddTo4(int a) {
        return a*3 + a; // a << 2
    }

    @Test
    @Arguments(values = {Argument.RANDOM_ONCE})
    @IR(counts = { IRNode.ADD_I, "1" })
    private static int addTo5(int a) {
        return a + a + a + a + a; // a<<2 + a
    }

    @Test
    @Arguments(values = {Argument.RANDOM_ONCE})
    @IR(counts = { IRNode.ADD_I, "1" })
    private static int addTo6(int a) {
        return a + a + a + a + a + a; // a<<1 + a<<2
    }

    @Test
    @Arguments(values = {Argument.RANDOM_ONCE})
    @IR(failOn = IRNode.ADD_I)
    private static int addTo7(int a) {
        return a + a + a + a + a + a + a; // a<<3 - a
    }

    @Test
    @Arguments(values = {Argument.RANDOM_ONCE})
    @IR(failOn = IRNode.ADD_I)
    private static int addTo8(int a) {
        return a + a + a + a + a + a + a + a; // a<<3
    }

    @Test
    @Arguments(values = {Argument.RANDOM_ONCE})
    @IR(failOn = IRNode.ADD_I)
    private static int addTo16(int a) {
        return a + a + a + a + a + a + a + a + a + a
             + a + a + a + a + a + a; // a<<4
    }

    @Test
    @Arguments(values = {Argument.RANDOM_ONCE})
    @IR(failOn = IRNode.ADD_I)
    @IR(counts = { IRNode.MUL_I, "1" })
    private static int addTo42(int a) {
        return  a + a + a + a + a + a + a + a + a + a +
                a + a + a + a + a + a + a + a + a + a +
                a + a + a + a + a + a + a + a + a + a +
                a + a + a + a + a + a + a + a + a + a +
                a + a;
    }

    @Test
    @Arguments(values = {Argument.RANDOM_ONCE})
    @IR(failOn = IRNode.ADD_I)
    @IR(counts = { IRNode.MUL_I, "1" })
    private static int mulAndAddTo42(int a) {
        return a * 40 + a + a; // a*42
    }

    // TODO: test long types
    // TODO: test overflows
    // TODO: test negative values
}