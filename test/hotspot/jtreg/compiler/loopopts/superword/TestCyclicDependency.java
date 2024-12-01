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
 *
 */

/*
 * @test
 * @bug 8298935 8334431
 * @summary Writing forward on array creates cyclic dependency
 *          which leads to wrong result, when ignored.
 * @library /test/lib /
 * @run driver TestCyclicDependency
 */

import jdk.test.lib.Asserts;
import compiler.lib.ir_framework.*;

public class TestCyclicDependency {
    static final int RANGE = 512;
    static final int ITER  = 100;
    int[] goldI0 = new int[RANGE];
    float[] goldF0 = new float[RANGE];
    int[] goldI1 = new int[RANGE];
    float[] goldF1 = new float[RANGE];
    int[] goldI2 = new int[RANGE];
    float[] goldF2 = new float[RANGE];
    int[] goldI3 = new int[RANGE];
    float[] goldF3 = new float[RANGE];
    int[] goldI4 = new int[RANGE];
    float[] goldF4 = new float[RANGE];
    int[] goldI5a = new int[RANGE];
    float[] goldF5a = new float[RANGE];
    int[] goldI5b = new int[RANGE];
    float[] goldF5b = new float[RANGE];
    int[] goldI6a = new int[RANGE];
    float[] goldF6a = new float[RANGE];
    int[] goldI6b = new int[RANGE];
    float[] goldF6b = new float[RANGE];
    int[] goldI7a = new int[RANGE];
    float[] goldF7a = new float[RANGE];
    int[] goldI7b = new int[RANGE];
    float[] goldF7b = new float[RANGE];
    float[] goldF7b_2 = new float[RANGE];
    int[] goldI7c = new int[RANGE];
    float[] goldF7c = new float[RANGE];
    int[] goldI8a = new int[RANGE];
    float[] goldF8a = new float[RANGE];
    int[] goldI8b = new int[RANGE];
    int[] goldI8b_2 = new int[RANGE];
    float[] goldF8b = new float[RANGE];
    int[] goldI8c = new int[RANGE];
    float[] goldF8c = new float[RANGE];
    int[] goldI9 = new int[RANGE];
    float[] goldF9 = new float[RANGE];

    public static void main(String args[]) {
        TestFramework.runWithFlags("-XX:CompileCommand=compileonly,TestCyclicDependency::test*",
                                   "-XX:+IgnoreUnrecognizedVMOptions", "-XX:-AlignVector", "-XX:-VerifyAlignVector");
        TestFramework.runWithFlags("-XX:CompileCommand=compileonly,TestCyclicDependency::test*",
                                   "-XX:+IgnoreUnrecognizedVMOptions", "-XX:+AlignVector", "-XX:-VerifyAlignVector");
        TestFramework.runWithFlags("-XX:CompileCommand=compileonly,TestCyclicDependency::test*",
                                   "-XX:+IgnoreUnrecognizedVMOptions", "-XX:+AlignVector", "-XX:+VerifyAlignVector");
    }

    TestCyclicDependency() {
        // compute the gold standard in interpreter mode
        // test0
        init(goldI0, goldF0);
        test0(goldI0, goldF0);
        // test1
        init(goldI1, goldF1);
        test1(goldI1, goldF1);
        // test2
        init(goldI2, goldF2);
        test2(goldI2, goldF2);
        // test3
        init(goldI3, goldF3);
        test3(goldI3, goldF3);
        // test4
        init(goldI4, goldF4);
        test4(goldI4, goldF4);
        // test5a
        init(goldI5a, goldF5a);
        test5a(goldI5a, goldF5a);
        // test5b
        init(goldI5b, goldF5b);
        test5b(goldI5b, goldF5b);
        // test6a
        init(goldI6a, goldF6a);
        test6a(goldI6a, goldF6a);
        // test6b
        init(goldI6b, goldF6b);
        test6b(goldI6b, goldF6b);
        // test7a
        init(goldI7a, goldF7a);
        test7a(goldI7a, goldF7a);
        // test7b
        init(goldI7b, goldF7b, goldF7b_2);
        test7b(goldI7b, goldF7b, goldF7b_2);
        // test7c
        init(goldI7c, goldF7c);
        test7c(goldI7c, goldF7c, goldF7c);
        // test8a
        init(goldI8a, goldF8a);
        test8a(goldI8a, goldF8a);
        // test8b
        init(goldI8b, goldI8b_2, goldF8b);
        test8b(goldI8b, goldI8b_2, goldF8b);
        // test8c
        init(goldI8c, goldF8c);
        test8c(goldI8c, goldI8c, goldF8c);
        // test9
        init(goldI9, goldF9);
        test9(goldI9, goldF9);
    }

    @Run(test = "test0")
    @Warmup(100)
    public void runTest0() {
        int[] dataI = new int[RANGE];
        float[] dataF = new float[RANGE];
        init(dataI, dataF);
        test0(dataI, dataF);
        verifyI("test0", dataI, goldI0);
        verifyF("test0", dataF, goldF0);
    }

    @Run(test = "test1")
    @Warmup(100)
    public void runTest1() {
        int[] dataI = new int[RANGE];
        float[] dataF = new float[RANGE];
        init(dataI, dataF);
        test1(dataI, dataF);
        verifyI("test1", dataI, goldI1);
        verifyF("test1", dataF, goldF1);
    }

    @Run(test = "test2")
    @Warmup(100)
    public void runTest2() {
        int[] dataI = new int[RANGE];
        float[] dataF = new float[RANGE];
        init(dataI, dataF);
        test2(dataI, dataF);
        verifyI("test2", dataI, goldI2);
        verifyF("test2", dataF, goldF2);
    }

    @Run(test = "test3")
    @Warmup(100)
    public void runTest3() {
        int[] dataI = new int[RANGE];
        float[] dataF = new float[RANGE];
        init(dataI, dataF);
        test3(dataI, dataF);
        verifyI("test3", dataI, goldI3);
        verifyF("test3", dataF, goldF3);
    }

    @Run(test = "test4")
    @Warmup(100)
    public void runTest4() {
        int[] dataI = new int[RANGE];
        float[] dataF = new float[RANGE];
        init(dataI, dataF);
        test4(dataI, dataF);
        verifyI("test4", dataI, goldI4);
        verifyF("test4", dataF, goldF4);
    }

    @Run(test = "test5a")
    @Warmup(100)
    public void runTest5a() {
        int[] dataI = new int[RANGE];
        float[] dataF = new float[RANGE];
        init(dataI, dataF);
        test5a(dataI, dataF);
        verifyI("test5a", dataI, goldI5a);
        verifyF("test5a", dataF, goldF5a);
    }

    @Run(test = "test5b")
    @Warmup(100)
    public void runTest5b() {
        int[] dataI = new int[RANGE];
        float[] dataF = new float[RANGE];
        init(dataI, dataF);
        test5b(dataI, dataF);
        verifyI("test5b", dataI, goldI5b);
        verifyF("test5b", dataF, goldF5b);
    }

    @Run(test = "test6a")
    @Warmup(100)
    public void runTest6a() {
        int[] dataI = new int[RANGE];
        float[] dataF = new float[RANGE];
        init(dataI, dataF);
        test6a(dataI, dataF);
        verifyI("test6a", dataI, goldI6a);
        verifyF("test6a", dataF, goldF6a);
    }

    @Run(test = "test6b")
    @Warmup(100)
    public void runTest6b() {
        int[] dataI = new int[RANGE];
        float[] dataF = new float[RANGE];
        init(dataI, dataF);
        test6b(dataI, dataF);
        verifyI("test6b", dataI, goldI6b);
        verifyF("test6b", dataF, goldF6b);
    }

    @Run(test = "test7a")
    @Warmup(100)
    public void runTest7a() {
        int[] dataI = new int[RANGE];
        float[] dataF = new float[RANGE];
        init(dataI, dataF);
        test7a(dataI, dataF);
        verifyI("test7a", dataI, goldI7a);
        verifyF("test7a", dataF, goldF7a);
    }

    @Run(test = "test7b")
    @Warmup(100)
    public void runTest7b() {
        int[] dataI = new int[RANGE];
        float[] dataF = new float[RANGE];
        float[] dataF_2 = new float[RANGE];
        init(dataI, dataF, dataF_2);
        test7b(dataI, dataF, dataF_2);
        verifyI("test7b", dataI, goldI7b);
        verifyF("test7b", dataF, goldF7b);
        verifyF("test7b", dataF_2, goldF7b_2);
    }

    @Run(test = "test7c")
    @Warmup(100)
    public void runTest7c() {
        int[] dataI = new int[RANGE];
        float[] dataF = new float[RANGE];
        init(dataI, dataF);
        test7c(dataI, dataF, dataF);
        verifyI("test7c", dataI, goldI7c);
        verifyF("test7c", dataF, goldF7c);
    }

    @Run(test = "test8a")
    @Warmup(100)
    public void runTest8a() {
        int[] dataI = new int[RANGE];
        float[] dataF = new float[RANGE];
        init(dataI, dataF);
        test8a(dataI, dataF);
        verifyI("test8a", dataI, goldI8a);
        verifyF("test8a", dataF, goldF8a);
    }

    @Run(test = "test8b")
    @Warmup(100)
    public void runTest8b() {
        int[] dataI = new int[RANGE];
        int[] dataI_2 = new int[RANGE];
        float[] dataF = new float[RANGE];
        init(dataI, dataI_2, dataF);
        test8b(dataI, dataI_2, dataF);
        verifyI("test8b", dataI, goldI8b);
        verifyI("test8b", dataI_2, goldI8b_2);
        verifyF("test8b", dataF, goldF8b);
    }

    @Run(test = "test8c")
    @Warmup(100)
    public void runTest8c() {
        int[] dataI = new int[RANGE];
        float[] dataF = new float[RANGE];
        init(dataI, dataF);
        test8c(dataI, dataI, dataF);
        verifyI("test8c", dataI, goldI8c);
        verifyF("test8c", dataF, goldF8c);
    }

    @Run(test = "test9")
    @Warmup(100)
    public void runTest9() {
        int[] dataI = new int[RANGE];
        float[] dataF = new float[RANGE];
        init(dataI, dataF);
        test9(dataI, dataF);
        verifyI("test9", dataI, goldI9);
        verifyF("test9", dataF, goldF9);
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, "> 0", IRNode.ADD_VI, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    static void test0(int[] dataI, float[] dataF) {
        for (int i = 0; i < RANGE; i++) {
            // All perfectly aligned, expect vectorization
            int v = dataI[i];
            dataI[i] = v + 5;
        }
    }

    @Test
    static void test1(int[] dataI, float[] dataF) {
        for (int i = 0; i < RANGE - 1; i++) {
            // dataI has cyclic dependency of distance 1
            int v = dataI[i];
            dataI[i + 1] = v;
            dataF[i] = v; // let's not get confused by another type
        }
    }

    @Test
    static void test2(int[] dataI, float[] dataF) {
        for (int i = 0; i < RANGE - 2; i++) {
            // dataI has cyclic dependency of distance 2
            int v = dataI[i];
            dataI[i + 2] = v;
            dataF[i] = v; // let's not get confused by another type
        }
    }

    @Test
    static void test3(int[] dataI, float[] dataF) {
        for (int i = 0; i < RANGE - 3; i++) {
            // dataI has cyclic dependency of distance 3
            int v = dataI[i];
            dataI[i + 3] = v;
            dataF[i] = v; // let's not get confused by another type
        }
    }

    @Test
    static void test4(int[] dataI, float[] dataF) {
        for (int i = 1; i < RANGE - 1; i++) {
            // dataI has cyclic dependency of distance 2
            int v = dataI[i - 1];
            dataI[i + 1] = v;
            dataF[i] = v; // let's not get confused by another type
        }
    }

    @Test
    static void test5a(int[] dataI, float[] dataF) {
        for (int i = 2; i < RANGE; i++) {
            // dataI has read / write distance 1, but no cyclic dependency
            int v = dataI[i];
            dataI[i - 1] = v + 5;
        }
    }

    @Test
    static void test5b(int[] dataI, float[] dataF) {
        for (int i = 1; i < RANGE; i++) {
            // dataI has read / write distance 1, but no cyclic dependency
            int v = dataI[i];
            dataI[i - 1] = v;
            dataF[i] = v; // let's not get confused by another type
        }
    }

    @Test
    static void test6a(int[] dataI, float[] dataF) {
        for (int i = 2; i < RANGE; i++) {
            // dataI has read / write distance 2, but no cyclic dependency
            int v = dataI[i];
            dataI[i - 2] = v + 5;
        }
    }

    @Test
    static void test6b(int[] dataI, float[] dataF) {
        for (int i = 2; i < RANGE; i++) {
            // dataI has read / write distance 2, but no cyclic dependency
            int v = dataI[i];
            dataI[i - 2] = v;
            dataF[i] = v; // let's not get confused by another type
        }
    }

    @Test
    @IR(counts = {IRNode.ADD_VI, "= 0",
                  IRNode.ADD_VF, "= 0"},
        applyIf = {"AlignVector", "false"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    @IR(counts = {IRNode.ADD_VI, "> 0",
                  IRNode.ADD_VF, "= 0"},
        applyIf = {"AlignVector", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // Some aarch64 machines have AlignVector == true, like ThunderX2
    static void test7a(int[] dataI, float[] dataF) {
        for (int i = 0; i < RANGE - 32; i++) {
            // write forward 32 -> more than vector size -> can vectorize
            int v = dataI[i];
            dataI[i + 32] = v + 5;
            // write forward 3:
            //   AlignVector=true -> cannot vectorize because load and store cannot be both aligned
            //   AlignVector=false -> could vectorize, but would get 2-element vectors where
            //                        store-to-load-forwarding fails, because we have store-load
            //                        dependencies that have partial overlap.
            //                        -> all vectorization cancled.
            float f = dataF[i];
            dataF[i + 3] = f + 3.5f;
        }
    }

    @Test
    @IR(counts = {IRNode.ADD_VI, "> 0",
                  IRNode.ADD_VF, IRNode.VECTOR_SIZE + "2", "> 0"},
        applyIf = {"AlignVector", "false"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    @IR(counts = {IRNode.ADD_VI, "> 0",
                  IRNode.ADD_VF, "= 0"},
        applyIf = {"AlignVector", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // Some aarch64 machines have AlignVector == true, like ThunderX2
    static void test7b(int[] dataI, float[] dataF, float[] dataF_2) {
        for (int i = 0; i < RANGE - 32; i++) {
            // write forward 32 -> more than vector size -> can vectorize
            int v = dataI[i];
            dataI[i + 32] = v + 5;
            // write forward 3 to different array reference:
            //   AlignVector=true -> cannot vectorize because load and store cannot be both aligned
            //   AlignVector=false -> vectorizes because we cannot prove store-to-load forwarding
            //                        failure. But we can only have 2-element vectors in case
            //                        the two float-arrays reference the same array.
            //                        Note: at runtime the float-arrays are always different.
            float f = dataF[i];
            dataF_2[i + 3] = f + 3.5f;
        }
    }

    @Test
    @IR(counts = {IRNode.ADD_VI, "> 0",
                  IRNode.ADD_VF, IRNode.VECTOR_SIZE + "2", "> 0"},
        applyIf = {"AlignVector", "false"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    @IR(counts = {IRNode.ADD_VI, "> 0",
                  IRNode.ADD_VF, "= 0"},
        applyIf = {"AlignVector", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // Some aarch64 machines have AlignVector == true, like ThunderX2
    static void test7c(int[] dataI, float[] dataF, float[] dataF_2) {
        for (int i = 0; i < RANGE - 32; i++) {
            // write forward 32 -> more than vector size -> can vectorize
            int v = dataI[i];
            dataI[i + 32] = v + 5;
            // write forward 3 to different array reference:
            //   AlignVector=true -> cannot vectorize because load and store cannot be both aligned
            //   AlignVector=false -> vectorizes because we cannot prove store-to-load forwarding
            //                        failure. But we can only have 2-element vectors in case
            //                        the two float-arrays reference the same array.
            //                        Note: at runtime the float-arrays are always the same.
            float f = dataF[i];
            dataF_2[i + 3] = f + 3.5f;
        }
    }

    @Test
    @IR(counts = {IRNode.ADD_VI, "= 0",
                  IRNode.ADD_VF, "= 0"},
        applyIf = {"AlignVector", "false"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    @IR(counts = {IRNode.ADD_VI, "= 0",
                  IRNode.ADD_VF, IRNode.VECTOR_SIZE + "min(max_int, max_float)", "> 0"},
        applyIf = {"AlignVector", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // Some aarch64 machines have AlignVector == true, like ThunderX2
    static void test8a(int[] dataI, float[] dataF) {
        for (int i = 0; i < RANGE - 32; i++) {
            // write forward 3:
            //   AlignVector=true -> cannot vectorize because load and store cannot be both aligned
            //   AlignVector=false -> could vectorize, but would get 2-element vectors where
            //                        store-to-load-forwarding fails, because we have store-load
            //                        dependencies that have partial overlap.
            //                        -> all vectorization cancled.
            int v = dataI[i];
            dataI[i + 3] = v + 5;
            // write forward 32 -> more than vector size -> can vectorize
            float f = dataF[i];
            dataF[i + 32] = f + 3.5f;
        }
    }

    @Test
    @IR(counts = {IRNode.ADD_VI, IRNode.VECTOR_SIZE + "2", "> 0",
                  IRNode.ADD_VF, IRNode.VECTOR_SIZE + "min(max_int, max_float)", "> 0"},
        applyIf = {"AlignVector", "false"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    @IR(counts = {IRNode.ADD_VI, "= 0",
                  IRNode.ADD_VF, IRNode.VECTOR_SIZE + "min(max_int, max_float)", "> 0"},
        applyIf = {"AlignVector", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // Some aarch64 machines have AlignVector == true, like ThunderX2
    static void test8b(int[] dataI, int[] dataI_2, float[] dataF) {
        for (int i = 0; i < RANGE - 32; i++) {
            // write forward 3 to different array reference:
            //   AlignVector=true -> cannot vectorize because load and store cannot be both aligned
            //   AlignVector=false -> vectorizes because we cannot prove store-to-load forwarding
            //                        failure. But we can only have 2-element vectors in case
            //                        the two float-arrays reference the same array.
            //                        Note: at runtime the float-arrays are always different.
            int v = dataI[i];
            dataI_2[i + 3] = v + 5;
            // write forward 32 -> more than vector size -> can vectorize
            float f = dataF[i];
            dataF[i + 32] = f + 3.5f;
        }
    }

    @Test
    @IR(counts = {IRNode.ADD_VI, IRNode.VECTOR_SIZE + "2", "> 0",
                  IRNode.ADD_VF, IRNode.VECTOR_SIZE + "min(max_int, max_float)", "> 0"},
        applyIf = {"AlignVector", "false"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    @IR(counts = {IRNode.ADD_VI, "= 0",
                  IRNode.ADD_VF, IRNode.VECTOR_SIZE + "min(max_int, max_float)", "> 0"},
        applyIf = {"AlignVector", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // Some aarch64 machines have AlignVector == true, like ThunderX2
    static void test8c(int[] dataI, int[] dataI_2, float[] dataF) {
        for (int i = 0; i < RANGE - 32; i++) {
            // write forward 3 to different array reference:
            //   AlignVector=true -> cannot vectorize because load and store cannot be both aligned
            //   AlignVector=false -> vectorizes because we cannot prove store-to-load forwarding
            //                        failure. But we can only have 2-element vectors in case
            //                        the two float-arrays reference the same array.
            //                        Note: at runtime the float-arrays are always the same.
            int v = dataI[i];
            dataI_2[i + 3] = v + 5;
            // write forward 32 -> more than vector size -> can vectorize
            float f = dataF[i];
            dataF[i + 32] = f + 3.5f;
        }
    }

    @Test
    @IR(counts = {IRNode.ADD_REDUCTION_VI, "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    static void test9(int[] dataI, float[] dataF) {
        int sI = 666;
        for (int i = 0; i < RANGE; i++) {
            // self-cycle allowed for reduction
            sI += dataI[i] * 2; // factor necessary to make it profitable
        }
        dataI[0] = sI; // write back
    }

    public static void init(int[] dataI, float[] dataF) {
        for (int j = 0; j < RANGE; j++) {
            dataI[j] = j;
            dataF[j] = j * 0.5f;
        }
    }

    public static void init(int[] dataI, float[] dataF, float[] dataF_2) {
        for (int j = 0; j < RANGE; j++) {
            dataI[j] = j;
            dataF[j] = j * 0.5f;
            dataF_2[j] = j * 0.3f;
        }
    }

    public static void init(int[] dataI, int[] dataI_2, float[] dataF) {
        for (int j = 0; j < RANGE; j++) {
            dataI[j] = j;
            dataI_2[j] = 3*j - 42;
            dataF[j] = j * 0.5f;
        }
    }

    static void verifyI(String name, int[] data, int[] gold) {
        for (int i = 0; i < RANGE; i++) {
            if (data[i] != gold[i]) {
                throw new RuntimeException(" Invalid " + name + " result: dataI[" + i + "]: " + data[i] + " != " + gold[i]);
            }
        }
    }

    static void verifyF(String name, float[] data, float[] gold) {
        for (int i = 0; i < RANGE; i++) {
            if (data[i] != gold[i]) {
                throw new RuntimeException(" Invalid " + name + " result: dataF[" + i + "]: " + data[i] + " != " + gold[i]);
            }
        }
    }
}
