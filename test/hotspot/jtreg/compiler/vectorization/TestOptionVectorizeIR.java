/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @bug 8298935
 * @summary Test forced vectorization, and check IR for vector instructions
 * @requires vm.compiler2.enabled
 * @library /test/lib /
 * @run driver compiler.vectorization.TestOptionVectorizeIR
 */

package compiler.vectorization;
import compiler.lib.ir_framework.*;

public class TestOptionVectorizeIR {
    static int RANGE = 1024*2;
    static int ITER  = 100;
    int[] gold1 = new int[RANGE];
    int[] gold2 = new int[RANGE];
    int[] gold3 = new int[RANGE];
    int[] gold4 = new int[RANGE];
    int[] gold5 = new int[RANGE];
    int[] gold6 = new int[RANGE];

    long[] gold10 = new long[RANGE];
    long[] gold11 = new long[RANGE];
    long[] gold12 = new long[RANGE];
    long[] gold13 = new long[RANGE];

    short[] gold20 = new short[RANGE];
    short[] gold21 = new short[RANGE];
    short[] gold22 = new short[RANGE];
    short[] gold23 = new short[RANGE];

    byte[] gold30 = new byte[RANGE];
    byte[] gold31 = new byte[RANGE];
    byte[] gold32 = new byte[RANGE];
    byte[] gold33 = new byte[RANGE];

    char[] gold40 = new char[RANGE];
    char[] gold41 = new char[RANGE];
    char[] gold42 = new char[RANGE];
    char[] gold43 = new char[RANGE];

    float[] gold50 = new float[RANGE];
    float[] gold51 = new float[RANGE];
    float[] gold52 = new float[RANGE];
    float[] gold53 = new float[RANGE];

    double[] gold60 = new double[RANGE];
    double[] gold61 = new double[RANGE];
    double[] gold62 = new double[RANGE];
    double[] gold63 = new double[RANGE];

    public static void main(String args[]) {
        TestFramework.runWithFlags("-XX:CompileCommand=option,compiler.vectorization.TestOptionVectorizeIR::test*,Vectorize");
    }

    TestOptionVectorizeIR() {
        // compute the gold standard in interpreter mode
        // test1
        test1(gold1);
        // test2
        test1(gold2);
        test2(gold2);
        // test3
        test1(gold3);
        test3(gold3, 2, 3);
        // test4
        test1(gold4);
        test4(gold4);
        // test5
        test1(gold5);
        test5(gold5);
        // test6
        test1(gold6);
        test6(gold6);

        // long
        init(gold10);
        test10(gold10);
        init(gold11);
        test11(gold11);
        init(gold12);
        test12(gold12);
        init(gold13);
        test13(gold13);

        // short
        init(gold20);
        test20(gold20);
        init(gold21);
        test21(gold21);
        init(gold22);
        test22(gold22);
        init(gold23);
        test23(gold23);

        // byte
        init(gold30);
        test30(gold30);
        init(gold31);
        test31(gold31);
        init(gold32);
        test32(gold32);
        init(gold33);
        test33(gold33);

        // char
        init(gold40);
        test40(gold40);
        init(gold41);
        test41(gold41);
        init(gold42);
        test42(gold42);
        init(gold43);
        test43(gold43);

        // float
        init(gold50);
        test50(gold50);
        init(gold51);
        test51(gold51);
        init(gold52);
        test52(gold52);
        init(gold53);
        test53(gold53);

        // double
        init(gold60);
        test60(gold60);
        init(gold61);
        test61(gold61);
        init(gold62);
        test62(gold62);
        init(gold63);
        test63(gold63);
    }

    @Run(test = "test1")
    @Warmup(100)
    public void runTest1() {
        int[] data = new int[RANGE];
        test1(data);
        verify("test1", data, gold1);
    }

    @Run(test = "test2")
    @Warmup(100)
    public void runTest2() {
        int[] data = new int[RANGE];
        test1(data);
        test2(data);
        verify("test2", data, gold2);
    }

    @Run(test = "test3")
    @Warmup(100)
    public void runTest3() {
        int[] data = new int[RANGE];
        test1(data);
        test3(data, 2, 3);
        verify("test3", data, gold3);
    }

    @Run(test = "test4")
    @Warmup(100)
    public void runTest4() {
        int[] data = new int[RANGE];
        test1(data);
        test4(data);
        verify("test4", data, gold4);
    }

    @Run(test = "test5")
    @Warmup(100)
    public void runTest5() {
        int[] data = new int[RANGE];
        test1(data);
        test5(data);
        verify("test5", data, gold5);
    }

    @Run(test = "test6")
    @Warmup(100)
    public void runTest6() {
        int[] data = new int[RANGE];
        test1(data);
        test6(data);
        verify("test6", data, gold6);
    }

    @Test
    static void test1(int[] data) {
       for (int j = 0; j < RANGE; j++) {
           // Vectorizes even if it is not forced
           data[j] = j;
       }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, "> 0", IRNode.ADD_VI, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"AlignVector", "false"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    static void test2(int[] data) {
       for (int j = 0; j < RANGE - 1; j++) {
           // Only vectorizes if forced, because of offset by 1
           data[j] = data[j] + data[j + 1];
       }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, "> 0", IRNode.REPLICATE_I, "> 0", IRNode.ADD_VI, "> 0", IRNode.MUL_VI, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"AlignVector", "false"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    static void test3(int[] data, int A, int B) {
       for (int j = 0; j < RANGE - 1; j++) {
           // Only vectorizes if forced, because of offset by 1
           data[j] = A * data[j] + B * data[j + 1];
       }
    }

    @Test
    static void test4(int[] data) {
       for (int j = 0; j < RANGE - 1; j++) {
           // write forward -> cyclic dependency -> cannot vectorize
           // independent(s1, s2) for adjacent loads should detect this
           data[j + 1] = data[j];
       }
    }

    @Test
    static void test5(int[] data) {
       for (int j = 0; j < RANGE - 3; j++) {
           // write forward -> cyclic dependency -> cannot vectorize
           // independent(s1, s2) for adjacent loads cannot detect this
           // Checks with memory_alignment are disabled via compile option
           data[j + 2] = data[j];
       }
    }

    @Test
    static void test6(int[] data) {
       for (int j = 0; j < RANGE - 3; j++) {
           // write forward -> cyclic dependency -> cannot vectorize
           // independent(s1, s2) for adjacent loads cannot detect this
           // Checks with memory_alignment are disabled via compile option
           data[j + 3] = data[j];
       }
    }

    // ------------------------- Long -----------------------------

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_L, "> 0", IRNode.ADD_VL, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"AlignVector", "false"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    static void test10(long[] data) {
       for (int j = 2; j < RANGE - 2; j++) {
           data[j] += data[j + 2];
       }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_L, "> 0", IRNode.ADD_VL, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"AlignVector", "false"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    static void test11(long[] data) {
       for (int j = 2; j < RANGE - 2; j++) {
           data[j] += data[j + 1];
       }
    }

    @Test
    static void test12(long[] data) {
       for (int j = 2; j < RANGE - 2; j++) {
           data[j] += data[j - 1];
       }
    }

    @Test
    static void test13(long[] data) {
       // 128-bit vectors -> can vectorize because only 2 elements
       for (int j = 2; j < RANGE - 2; j++) {
           data[j] += data[j - 2];
       }
    }

    @Run(test = "test10")
    @Warmup(100)
    public void runTest10() {
        long[] data = new long[RANGE];
        init(data);
        test10(data);
        verify("test10", data, gold10);
    }

    @Run(test = "test11")
    @Warmup(100)
    public void runTest11() {
        long[] data = new long[RANGE];
        init(data);
        test11(data);
        verify("test11", data, gold11);
    }

    @Run(test = "test12")
    @Warmup(100)
    public void runTest12() {
        long[] data = new long[RANGE];
        init(data);
        test12(data);
        verify("test12", data, gold12);
    }

    @Run(test = "test13")
    @Warmup(100)
    public void runTest13() {
        long[] data = new long[RANGE];
        init(data);
        test13(data);
        verify("test13", data, gold13);
    }


    // ------------------------- Short -----------------------------

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_S, "> 0", IRNode.ADD_VS, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"AlignVector", "false"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    static void test20(short[] data) {
       for (int j = 2; j < RANGE - 2; j++) {
           data[j] += data[j + 2];
       }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_S, "> 0", IRNode.ADD_VS, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"AlignVector", "false"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    static void test21(short[] data) {
       for (int j = 2; j < RANGE - 2; j++) {
           data[j] += data[j + 1];
       }
    }

    @Test
    static void test22(short[] data) {
       for (int j = 2; j < RANGE - 2; j++) {
           data[j] += data[j - 1];
       }
    }

    @Test
    static void test23(short[] data) {
       for (int j = 2; j < RANGE - 2; j++) {
           data[j] += data[j - 2];
       }
    }

    @Run(test = "test20")
    @Warmup(100)
    public void runTest20() {
        short[] data = new short[RANGE];
        init(data);
        test20(data);
        verify("test20", data, gold20);
    }

    @Run(test = "test21")
    @Warmup(100)
    public void runTest21() {
        short[] data = new short[RANGE];
        init(data);
        test21(data);
        verify("test21", data, gold21);
    }

    @Run(test = "test22")
    @Warmup(100)
    public void runTest22() {
        short[] data = new short[RANGE];
        init(data);
        test22(data);
        verify("test22", data, gold22);
    }

    @Run(test = "test23")
    @Warmup(100)
    public void runTest23() {
        short[] data = new short[RANGE];
        init(data);
        test23(data);
        verify("test23", data, gold23);
    }


    // ------------------------- Byte -----------------------------

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, "> 0", IRNode.ADD_VB, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"AlignVector", "false"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    static void test30(byte[] data) {
       for (int j = 2; j < RANGE - 2; j++) {
           data[j] += data[j + 2];
       }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, "> 0", IRNode.ADD_VB, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"AlignVector", "false"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    static void test31(byte[] data) {
       for (int j = 2; j < RANGE - 2; j++) {
           data[j] += data[j + 1];
       }
    }

    @Test
    static void test32(byte[] data) {
       for (int j = 2; j < RANGE - 2; j++) {
           data[j] += data[j - 1];
       }
    }

    @Test
    static void test33(byte[] data) {
       for (int j = 2; j < RANGE - 2; j++) {
           data[j] += data[j - 2];
       }
    }

    @Run(test = "test30")
    @Warmup(100)
    public void runTest30() {
        byte[] data = new byte[RANGE];
        init(data);
        test30(data);
        verify("test30", data, gold30);
    }

    @Run(test = "test31")
    @Warmup(100)
    public void runTest31() {
        byte[] data = new byte[RANGE];
        init(data);
        test31(data);
        verify("test31", data, gold31);
    }

    @Run(test = "test32")
    @Warmup(100)
    public void runTest32() {
        byte[] data = new byte[RANGE];
        init(data);
        test32(data);
        verify("test32", data, gold32);
    }

    @Run(test = "test33")
    @Warmup(100)
    public void runTest33() {
        byte[] data = new byte[RANGE];
        init(data);
        test33(data);
        verify("test33", data, gold33);
    }


    // ------------------------- Char -----------------------------

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_C, "> 0", IRNode.ADD_VS, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"AlignVector", "false"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    static void test40(char[] data) {
       for (int j = 2; j < RANGE - 2; j++) {
           data[j] += data[j + 2];
       }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_C, "> 0", IRNode.ADD_VS, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"AlignVector", "false"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    static void test41(char[] data) {
       for (int j = 2; j < RANGE - 2; j++) {
           data[j] += data[j + 1];
       }
    }

    @Test
    static void test42(char[] data) {
       for (int j = 2; j < RANGE - 2; j++) {
           data[j] += data[j - 1];
       }
    }

    @Test
    static void test43(char[] data) {
       for (int j = 2; j < RANGE - 2; j++) {
           data[j] += data[j - 2];
       }
    }

    @Run(test = "test40")
    @Warmup(100)
    public void runTest40() {
        char[] data = new char[RANGE];
        init(data);
        test40(data);
        verify("test40", data, gold40);
    }

    @Run(test = "test41")
    @Warmup(100)
    public void runTest41() {
        char[] data = new char[RANGE];
        init(data);
        test41(data);
        verify("test41", data, gold41);
    }

    @Run(test = "test42")
    @Warmup(100)
    public void runTest42() {
        char[] data = new char[RANGE];
        init(data);
        test42(data);
        verify("test42", data, gold42);
    }

    @Run(test = "test43")
    @Warmup(100)
    public void runTest43() {
        char[] data = new char[RANGE];
        init(data);
        test43(data);
        verify("test43", data, gold43);
    }

    // ------------------------- Float -----------------------------

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_F, "> 0", IRNode.ADD_VF, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"AlignVector", "false"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    static void test50(float[] data) {
       for (int j = 2; j < RANGE - 2; j++) {
           data[j] += data[j + 2];
       }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_F, "> 0", IRNode.ADD_VF, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"AlignVector", "false"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    static void test51(float[] data) {
       for (int j = 2; j < RANGE - 2; j++) {
           data[j] += data[j + 1];
       }
    }

    @Test
    static void test52(float[] data) {
       for (int j = 2; j < RANGE - 2; j++) {
           data[j] += data[j - 1];
       }
    }

    @Test
    static void test53(float[] data) {
       for (int j = 2; j < RANGE - 2; j++) {
           data[j] += data[j - 2];
       }
    }

    @Run(test = "test50")
    @Warmup(100)
    public void runTest50() {
        float[] data = new float[RANGE];
        init(data);
        test50(data);
        verify("test50", data, gold50);
    }

    @Run(test = "test51")
    @Warmup(100)
    public void runTest51() {
        float[] data = new float[RANGE];
        init(data);
        test51(data);
        verify("test51", data, gold51);
    }

    @Run(test = "test52")
    @Warmup(100)
    public void runTest52() {
        float[] data = new float[RANGE];
        init(data);
        test52(data);
        verify("test52", data, gold52);
    }

    @Run(test = "test53")
    @Warmup(100)
    public void runTest53() {
        float[] data = new float[RANGE];
        init(data);
        test53(data);
        verify("test53", data, gold53);
    }

    // ------------------------- Double -----------------------------

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_D, "> 0", IRNode.ADD_VD, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"AlignVector", "false"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    static void test60(double[] data) {
       for (int j = 2; j < RANGE - 2; j++) {
           data[j] += data[j + 2];
       }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_D, "> 0", IRNode.ADD_VD, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"AlignVector", "false"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    static void test61(double[] data) {
       for (int j = 2; j < RANGE - 2; j++) {
           data[j] += data[j + 1];
       }
    }

    @Test
    static void test62(double[] data) {
       for (int j = 2; j < RANGE - 2; j++) {
           data[j] += data[j - 1];
       }
    }

    @Test
    static void test63(double[] data) {
       // 128-bit vectors -> can vectorize because only 2 elements
       for (int j = 2; j < RANGE - 2; j++) {
           data[j] += data[j - 2];
       }
    }

    @Run(test = "test60")
    @Warmup(100)
    public void runTest60() {
        double[] data = new double[RANGE];
        init(data);
        test60(data);
        verify("test60", data, gold60);
    }

    @Run(test = "test61")
    @Warmup(100)
    public void runTest61() {
        double[] data = new double[RANGE];
        init(data);
        test61(data);
        verify("test61", data, gold61);
    }

    @Run(test = "test62")
    @Warmup(100)
    public void runTest62() {
        double[] data = new double[RANGE];
        init(data);
        test62(data);
        verify("test62", data, gold62);
    }

    @Run(test = "test63")
    @Warmup(100)
    public void runTest63() {
        double[] data = new double[RANGE];
        init(data);
        test63(data);
        verify("test63", data, gold63);
    }

    static void init(long[] data) {
       for (int j = 0; j < RANGE; j++) {
           data[j] = j;
       }
    }

    static void init(short[] data) {
       for (int j = 0; j < RANGE; j++) {
           data[j] = (short)j;
       }
    }

    static void init(byte[] data) {
       for (int j = 0; j < RANGE; j++) {
           data[j] = (byte)j;
       }
    }

    static void init(char[] data) {
       for (int j = 0; j < RANGE; j++) {
           data[j] = (char)j;
       }
    }


    static void init(float[] data) {
       for (int j = 0; j < RANGE; j++) {
           data[j] = j;
       }
    }


    static void init(double[] data) {
       for (int j = 0; j < RANGE; j++) {
           data[j] = j;
       }
    }

    static void verify(String name, int[] data, int[] gold) {
        for (int i = 0; i < RANGE; i++) {
            if (data[i] != gold[i]) {
                throw new RuntimeException(" Invalid " + name + " result: data[" + i + "]: " + data[i] + " != " + gold[i]);
            }
        }
    }

    static void verify(String name, long[] data, long[] gold) {
        for (int i = 0; i < RANGE; i++) {
            if (data[i] != gold[i]) {
                throw new RuntimeException(" Invalid " + name + " result: data[" + i + "]: " + data[i] + " != " + gold[i]);
            }
        }
    }

    static void verify(String name, short[] data, short[] gold) {
        for (int i = 0; i < RANGE; i++) {
            if (data[i] != gold[i]) {
                throw new RuntimeException(" Invalid " + name + " result: data[" + i + "]: " + data[i] + " != " + gold[i]);
            }
        }
    }

    static void verify(String name, byte[] data, byte[] gold) {
        for (int i = 0; i < RANGE; i++) {
            if (data[i] != gold[i]) {
                throw new RuntimeException(" Invalid " + name + " result: data[" + i + "]: " + data[i] + " != " + gold[i]);
            }
        }
    }

    static void verify(String name, char[] data, char[] gold) {
        for (int i = 0; i < RANGE; i++) {
            if (data[i] != gold[i]) {
                throw new RuntimeException(" Invalid " + name + " result: data[" + i + "]: " + data[i] + " != " + gold[i]);
            }
        }
    }

    static void verify(String name, float[] data, float[] gold) {
        for (int i = 0; i < RANGE; i++) {
            if (data[i] != gold[i]) {
                throw new RuntimeException(" Invalid " + name + " result: data[" + i + "]: " + data[i] + " != " + gold[i]);
            }
        }
    }

    static void verify(String name, double[] data, double[] gold) {
        for (int i = 0; i < RANGE; i++) {
            if (data[i] != gold[i]) {
                throw new RuntimeException(" Invalid " + name + " result: data[" + i + "]: " + data[i] + " != " + gold[i]);
            }
        }
    }
}
