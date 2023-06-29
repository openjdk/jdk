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
 * @bug 8310308
 * @summary Basic examples for vector node type and size verification
 * @requires vm.compiler2.enabled
 * @library /test/lib /
 * @run driver TestVectorNode
 */

import compiler.lib.ir_framework.*;

public class TestVectorNode {
    public static void main(String args[]) {
        TestFramework.run();
    }

    @Test
    // By default, we search for the maximal size possible
    @IR(counts = {IRNode.LOAD_VI, "> 0"},
        applyIfCPUFeatureOr = {"sse2", "true", "asimd", "true"})
    // We can also specify that we want the maximum explicitly
    @IR(counts = {IRNode.LOAD_VI, IRNode.VECTOR_SIZE_MAX, "> 0"},
        applyIfCPUFeatureOr = {"sse2", "true", "asimd", "true"})
    // Explicitly take the maximum size for this type (here int)
    @IR(counts = {IRNode.LOAD_VI, IRNode.VECTOR_SIZE + "max_for_type", "> 0"},
        applyIfCPUFeatureOr = {"sse2", "true", "asimd", "true"})
    // Exlicitly take the maximum size for the int type
    @IR(counts = {IRNode.LOAD_VI, IRNode.VECTOR_SIZE + "max_int", "> 0"},
        applyIfCPUFeatureOr = {"sse2", "true", "asimd", "true"})
    // As a last resort, we can match with any size
    @IR(counts = {IRNode.LOAD_VI, IRNode.VECTOR_SIZE_ANY, "> 0"},
        applyIfCPUFeatureOr = {"sse2", "true", "asimd", "true"})
    // Specify comma separated list of numbers, match for any of them
    @IR(counts = {IRNode.LOAD_VI, IRNode.VECTOR_SIZE + "2,4,8,16,32,64", "> 0"},
        applyIfCPUFeatureOr = {"sse2", "true", "asimd", "true"})
    // Two or more arguments to min(...): the minimal value is applied
    @IR(counts = {IRNode.LOAD_VI, IRNode.VECTOR_SIZE + "min(max_for_type, max_int, LoopMaxUnroll, 64)", "> 0"},
        applyIfCPUFeatureOr = {"sse2", "true", "asimd", "true"})
    static int[] test0() {
        int[] a = new int[1024*8];
        for (int i = 0; i < a.length; i++) {
            a[i]++;
        }
        return a;
    }

    @Test
    // By default, we search for the maximal size possible
    @IR(counts = {IRNode.LOAD_VF, "> 0"},
        applyIfCPUFeatureOr = {"sse2", "true", "asimd", "true"})
    // In some cases, we can know the exact size, here 16
    @IR(counts = {IRNode.LOAD_VF, IRNode.VECTOR_SIZE_16, "> 0"},
        applyIf = {"MaxVectorSize", "=64"},
        applyIfCPUFeatureOr = {"sse2", "true", "asimd", "true"})
    @IR(counts = {IRNode.LOAD_VF, IRNode.VECTOR_SIZE_8, "> 0"},
        applyIf = {"MaxVectorSize", "=32"},
        applyIfCPUFeatureOr = {"sse2", "true", "asimd", "true"})
    // In some cases, we can know the exact size, here 4
    @IR(counts = {IRNode.LOAD_VF, IRNode.VECTOR_SIZE_4, "> 0"},
        applyIf = {"MaxVectorSize", "=16"},
        applyIfCPUFeatureOr = {"sse2", "true", "asimd", "true"})
    static float[] test1() {
        float[] a = new float[1024*8];
        for (int i = 0; i < a.length; i++) {
            a[i]++;
        }
        return a;
    }

    @Test
    // In some cases, we can know the exact size, here 4
    @IR(counts = {IRNode.LOAD_VF, IRNode.VECTOR_SIZE_4, "> 0"},
        applyIf = {"MaxVectorSize", ">=16"},
        applyIfCPUFeatureOr = {"sse2", "true", "asimd", "true"})
    // Hence, we know any other sizes are impossible.
    // We can also specify that explicitly for failOn
    @IR(failOn = {IRNode.LOAD_VF, IRNode.VECTOR_SIZE_2,
                  IRNode.LOAD_VF, IRNode.VECTOR_SIZE_8,
                  IRNode.LOAD_VF, IRNode.VECTOR_SIZE_16,
                  IRNode.LOAD_VF, IRNode.VECTOR_SIZE_32,
                  IRNode.LOAD_VF, IRNode.VECTOR_SIZE_64,
                  IRNode.LOAD_VF, IRNode.VECTOR_SIZE + "2,8,16,32,64"},
        applyIf = {"MaxVectorSize", ">=16"},
        applyIfCPUFeatureOr = {"sse2", "true", "asimd", "true"})
    static float[] test2() {
        float[] a = new float[1024*8];
        for (int i = 0; i < a.length/8; i++) {
            a[i*8 + 0]++; // block of 4, then gap of 4
            a[i*8 + 1]++;
            a[i*8 + 2]++;
            a[i*8 + 3]++;
        }
        return a;
    }

    @Test
    // Here, we can pack at most 8 given the 8-blocks and 8-gaps.
    // But we can also never pack more than max_float
    @IR(counts = {IRNode.LOAD_VF, IRNode.VECTOR_SIZE + "min(8, max_float)", "> 0"},
        applyIf = {"MaxVectorSize", ">=16"},
        applyIfCPUFeatureOr = {"sse2", "true", "asimd", "true"})
    static float[] test3() {
        float[] a = new float[1024*8];
        for (int i = 0; i < a.length/16; i++) {
            a[i*16 + 0]++; // block of 8, then gap of 8
            a[i*16 + 1]++;
            a[i*16 + 2]++;
            a[i*16 + 3]++;
            a[i*16 + 4]++;
            a[i*16 + 5]++;
            a[i*16 + 6]++;
            a[i*16 + 7]++;
        }
        return a;
    }
}
