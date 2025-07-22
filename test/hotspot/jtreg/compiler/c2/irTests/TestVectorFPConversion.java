package compiler.c2.irTests;

import compiler.lib.generators.Generators;
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

    // Does not vectorize because of NaN flow control.
    @Test
    @IR(counts = {IRNode.MOV_D2L, ">=1"})
    public void loopDoubleToLongBits(double[] arr, long[] result) {
        for (int i = 0; i < arr.length; i++)
        {
            final double v = arr[i];
            final long bits = Double.doubleToLongBits(v);
            result[i] = bits;
        }
    }

    @Test
    @IR(counts = {IRNode.VECTOR_REINTERPRET, ">=1"})
    public void loopDoubleToRawLongBits(double[] arr, long[] result) {
        for (int i = 0; i < arr.length; i++)
        {
            final double v = arr[i];
            final long bits = Double.doubleToRawLongBits(v);
            result[i] = bits;
        }
    }

    @Test
    @IR(counts = {IRNode.VECTOR_REINTERPRET, ">=1"})
    public void loopLongBitsToDouble(long[] arr, double[] result) {
        for (int i = 0; i < arr.length; i++)
        {
            final long bits = arr[i];
            final double v = Double.longBitsToDouble(bits);
            result[i] = v;
        }
    }

    // Does not vectorize because of NaN flow control.
    @Test
    @IR(counts = {IRNode.MOV_F2I, ">=1"})
    public void loopFloatToIntBits(float[] arr, int[] result) {
        for (int i = 0; i < arr.length; i++)
        {
            final float v = arr[i];
            final int bits = Float.floatToIntBits(v);
            result[i] = bits;
        }
    }

    @Test
    @IR(counts = {IRNode.VECTOR_REINTERPRET, ">=1"})
    public void loopFloatToRawIntBits(float[] arr, int[] result) {
        for (int i = 0; i < arr.length; i++)
        {
            final float v = arr[i];
            final int bits = Float.floatToRawIntBits(v);
            result[i] = bits;
        }
    }

    @Test
    @IR(counts = {IRNode.VECTOR_REINTERPRET, ">=1"})
    public void loopIntToFloatBits(int[] arr, float[] result) {
        for (int i = 0; i < arr.length; i++)
        {
            final int bits = arr[i];
            final float v = Float.intBitsToFloat(bits);
            result[i] = v;
        }
    }

    @Run(test = {
        "loopDoubleToLongBits", "loopDoubleToRawLongBits",
        "loopFloatToIntBits", "loopFloatToRawIntBits",
        "loopIntToFloatBits", "loopLongBitsToDouble"
    })
    public void runTests() {
        final long[] l1 = new long[LENGTH];
        final long[] l2 = new long[LENGTH];
        final double[] d1 = new double[LENGTH];
        final double[] d2 = new double[LENGTH];

        loopDoubleToRawLongBits(DOUBLES, l1);
        loopDoubleToLongBits(DOUBLES, l2);
        loopLongBitsToDouble(l1, d1);
        loopLongBitsToDouble(l2, d2);
        Asserts.assertEqualsDoubleArray(DOUBLES, d1);
        Asserts.assertEqualsDoubleArray(DOUBLES, d2);

        final int[] i1 = new int[LENGTH];
        final int[] i2 = new int[LENGTH];
        final float[] f1 = new float[LENGTH];
        final float[] f2 = new float[LENGTH];

        loopFloatToRawIntBits(FLOATS, i1);
        loopFloatToIntBits(FLOATS, i2);
        loopIntToFloatBits(i1, f1);
        loopIntToFloatBits(i2, f2);
        Asserts.assertEqualsFloatArray(FLOATS, f1);
        Asserts.assertEqualsFloatArray(FLOATS, f2);
    }
}
