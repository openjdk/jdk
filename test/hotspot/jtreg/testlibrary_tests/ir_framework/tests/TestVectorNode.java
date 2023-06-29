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

    // As a last resort, we can match with any size
    @IR(counts = {IRNode.LOAD_VI, IRNode.VECTOR_SIZE_ANY, "> 0"},
        applyIfCPUFeatureOr = {"sse2", "true", "asimd", "true"})
 
    // 
    @IR(counts = {IRNode.LOAD_VI, IRNode.VECTOR_SIZE + "", "> 0"},
        applyIfCPUFeatureOr = {"sse2", "true", "asimd", "true"})

    // 
    @IR(counts = {IRNode.LOAD_VI, IRNode.VECTOR_SIZE + "xxx", "> 0"},
        applyIfCPUFeatureOr = {"sse2", "true", "asimd", "true"})

    static int[] test0() {
        int[] a = new int[1024*8];
        for (int i = 0; i < a.length; i++) {
            a[i]++;
        }
        return a;
    }
}





