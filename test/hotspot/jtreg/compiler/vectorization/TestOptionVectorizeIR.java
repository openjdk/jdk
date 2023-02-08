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
 * @requires vm.cpu.features ~= ".*avx.*" | vm.cpu.features ~= ".*sve.*"
 * @library /test/lib /
 * @run driver compiler.vectorization.TestOptionVectorizeIR
 */

package compiler.vectorization;
import compiler.lib.ir_framework.*;

public class TestOptionVectorizeIR {
    static final int RANGE = 512;
    static final int ITER  = 100;
    int[] gold1 = new int[RANGE];
    int[] gold2 = new int[RANGE];
    int[] gold3 = new int[RANGE];
    int[] gold4 = new int[RANGE];
    int[] gold5 = new int[RANGE];
    int[] gold6 = new int[RANGE];

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
    @IR(applyIfCPUFeatureOr = {"avx2", "true", "sve", "true"}, counts = {IRNode.POPULATE_INDEX, "> 0"})
    @IR(applyIfCPUFeatureOr = {"avx2", "true", "sve", "true"}, counts = {IRNode.STORE_VECTOR, "> 0"})
    static void test1(int[] data) {
       for (int j = 0; j < RANGE; j++) {
           // Vectorizes even if it is not forced
           data[j] = j;
       }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0"})
    @IR(counts = {IRNode.ADD_VI, "> 0"})
    @IR(counts = {IRNode.STORE_VECTOR, "> 0"})
    static void test2(int[] data) {
       for (int j = 0; j < RANGE - 1; j++) {
           // Only vectorizes if forced, because of offset by 1
           data[j] = data[j] + data[j + 1];
       }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0"})
    @IR(counts = {IRNode.REPLICATE_I, "> 0"})
    @IR(counts = {IRNode.ADD_VI, "> 0"})
    @IR(counts = {IRNode.MUL_VI, "> 0"})
    @IR(counts = {IRNode.STORE_VECTOR, "> 0"})
    static void test3(int[] data, int A, int B) {
       for (int j = 0; j < RANGE - 1; j++) {
           // Only vectorizes if forced, because of offset by 1
           data[j] = A * data[j] + B * data[j + 1];
       }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, "= 0"})
    @IR(counts = {IRNode.STORE_VECTOR, "= 0"})
    static void test4(int[] data) {
       for (int j = 0; j < RANGE - 1; j++) {
           // write forward -> cyclic dependency -> cannot vectorize
           // independent(s1, s2) for adjacent loads should detect this
           data[j + 1] = data[j];
       }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, "= 0"})
    @IR(counts = {IRNode.STORE_VECTOR, "= 0"})
    static void test5(int[] data) {
       for (int j = 0; j < RANGE - 3; j++) {
           // write forward -> cyclic dependency -> cannot vectorize
           // independent(s1, s2) for adjacent loads cannot detect this
           // Checks with memory_alignment are disabled via compile option
           data[j + 2] = data[j];
       }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, "= 0"})
    @IR(counts = {IRNode.STORE_VECTOR, "= 0"})
    static void test6(int[] data) {
       for (int j = 0; j < RANGE - 3; j++) {
           // write forward -> cyclic dependency -> cannot vectorize
           // independent(s1, s2) for adjacent loads cannot detect this
           // Checks with memory_alignment are disabled via compile option
           data[j + 3] = data[j];
       }
    }

    static void verify(String name, int[] data, int[] gold) {
        for (int i = 0; i < RANGE; i++) {
            if (data[i] != gold[i]) {
                throw new RuntimeException(" Invalid " + name + " result: data[" + i + "]: " + data[i] + " != " + gold[i]);
            }
        }
    }
}
