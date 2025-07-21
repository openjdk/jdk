package compiler.c2.irTests;

import compiler.lib.generators.Generators;
import compiler.lib.ir_framework.CompilePhase;
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.Run;
import compiler.lib.ir_framework.Test;
import compiler.lib.ir_framework.TestFramework;
import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8329077
 * @summary Test that code generation for vectorized FP conversion works as intended
 * @library /test/lib /
 * @requires vm.compiler2.enabled
  * @run driver compiler.c2.irTests.TestVectorFPConversion
 */
public class TestVectorFPConversion {
    private static final int LENGTH = 1024;
    private static final Generators RANDOM = Generators.G;

    private static final double[] DOUBLES;
    private static final float[] FLOATS;

    static {
        DOUBLES = new double[LENGTH];
        FLOATS = new float[LENGTH];

        RANDOM.fill(RANDOM.doubles(), DOUBLES);
        RANDOM.fill(RANDOM.floats(), FLOATS);
    }

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Test
    @IR(phase = { CompilePhase.BEFORE_REMOVEUSELESS }, counts = {IRNode.MOV_D2L, ">=1"})
    public long[] loopDoubleToRawLongBits(double[] arr, long[] result) {
        for (int i = 0; i < arr.length; i++)
        {
            final double v = arr[i];
            final long bits = Double.doubleToRawLongBits(v);
            result[i] = bits;
        }
        return result;
    }

//    @Test
//    @IR(counts = {IRNode.MOV_D2L, ">=1"})
//    public long[] loopDoubleToLongBits(double[] arr) {
//        final long[] result = new long[arr.length];
//        for (int i = 0; i < result.length; i++)
//        {
//            final double v = arr[i];
//            final long bits = Double.doubleToLongBits(v);
//            result[i] = bits;
//        }
//        return result;
//    }
//
//    @Test
//    @IR(counts = {IRNode.MOV_L2D, ">=1"})
//    public double[] loopLongBitsToDouble(long[] arr) {
//        final double[] result = new double[arr.length];
//        for (int i = 0; i < result.length; i++)
//        {
//            final long v = arr[i];
//            final double bits = Double.longBitsToDouble(v);
//            result[i] = bits;
//        }
//        return result;
//    }

    @Run(test = {"loopDoubleToRawLongBits"
        //, "loopDoubleToLongBits", "loopLongBitsToDouble",
                 // "floatToRawIntBits", "floatToIntBits", "intBitsToFloat"
    })
    public void runTests() {
        final long[] l1 = loopDoubleToRawLongBits(DOUBLES, new long[LENGTH]);
        Asserts.assertNotNull(l1);

//        final long[] l2 = loopDoubleToLongBits(DOUBLES);
//        final double[] d1 = loopLongBitsToDouble(l1);
//        final double[] d2 = loopLongBitsToDouble(l2);
//        Asserts.assertEqualsDoubleArray(DOUBLES, d1);
//        Asserts.assertEqualsDoubleArray(DOUBLES, d2);

//        for (int i = 0; i < DOUBLES.length; i++) {
//            double d = DOUBLES[i];
//            long l1 = doubleToRawLongBits(d);
//            long l2 = doubleToLongBits(d);
//            double d1 = longBitsToDouble(l1);
//            double d2 = longBitsToDouble(l2);
//            Asserts.assertEquals(d, d1);
//            Asserts.assertEquals(d, d2);
//        }
//        for (int i = 0; i < FLOATS.length; i++) {
//            float f = FLOATS[i];
//            int i1 = floatToRawIntBits(f);
//            int i2 = floatToIntBits(f);
//            float f1 = intBitsToFloat(i1);
//            float f2 = intBitsToFloat(i2);
//            Asserts.assertEquals(f, f1);
//            Asserts.assertEquals(f, f2);
//        }
    }
}
