/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.loop.test;

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.junit.Test;

public class LoopPartialUnrollTest extends GraalCompilerTest {

    @Override
    protected boolean checkMidTierGraph(StructuredGraph graph) {
        NodeIterable<LoopBeginNode> loops = graph.getNodes().filter(LoopBeginNode.class);
        for (LoopBeginNode loop : loops) {
            if (loop.isMainLoop()) {
                return true;
            }
        }
        return false;
    }

    public static long testMultiplySnippet(int arg) {
        long r = 1;
        for (int i = 0; branchProbability(0.99, i < arg); i++) {
            r += r * i;
        }
        return r;
    }

    @Test
    public void testMultiply() {
        test("testMultiplySnippet", 9);
    }

    public static int testNestedSumSnippet(int d) {
        int c = 0;
        for (int i = 0; i < d; i++) {
            for (int j = 0; branchProbability(0.99, j < i); j++) {
                c += c + j & 0x3;
            }
        }
        return c;
    }

    @Test
    public void testNestedSumBy2() {
        for (int i = 0; i < 1000; i++) {
            test("testNestedSumBy2Snippet", i);
        }
    }

    public static int testNestedSumBy2Snippet(int d) {
        int c = 0;
        for (int i = 0; i < d; i++) {
            for (int j = 0; branchProbability(0.99, j < i); j += 2) {
                c += c + j & 0x3;
            }
        }
        return c;
    }

    @Test
    public void testNestedSum() {
        for (int i = 0; i < 1000; i++) {
            test("testNestedSumSnippet", i);
        }
    }

    public static int testSumDownSnippet(int d) {
        int c = 0;
        for (int j = d; branchProbability(0.99, j > -4); j--) {
            c += c + j & 0x3;
        }
        return c;
    }

    @Test
    public void testSumDown() {
        test("testSumDownSnippet", 1);
        for (int i = 0; i < 160; i++) {
            test("testSumDownSnippet", i);
        }
    }

    public static int testSumDownBy2Snippet(int d) {
        int c = 0;
        for (int j = d; branchProbability(0.99, j > -4); j -= 2) {
            c += c + j & 0x3;
        }
        return c;
    }

    @Test
    public void testSumDownBy2() {
        test("testSumDownBy2Snippet", 1);
        for (int i = 0; i < 160; i++) {
            test("testSumDownBy2Snippet", i);
        }
    }

    @Test
    public void testLoopCarried() {
        test("testLoopCarriedSnippet", 1, 2);
        test("testLoopCarriedSnippet", 0, 4);
        test("testLoopCarriedSnippet", 4, 0);
    }

    public static int testLoopCarriedSnippet(int a, int b) {
        int c = a;
        int d = b;
        for (int j = 0; branchProbability(0.99, j < a); j++) {
            d = c;
            c += 1;
        }
        return c + d;
    }

    public static long init = Runtime.getRuntime().totalMemory();
    private int x;
    private int z;

    public int[] testComplexSnippet(int d) {
        x = 3;
        int y = 5;
        z = 7;
        for (int i = 0; i < d; i++) {
            for (int j = 0; branchProbability(0.99, j < i); j++) {
                z += x;
            }
            y = x ^ z;
            if ((i & 4) == 0) {
                z--;
            } else if ((i & 8) == 0) {
                Runtime.getRuntime().totalMemory();
            }
        }
        return new int[]{x, y, z};
    }

    @Test
    public void testComplex() {
        for (int i = 0; i < 10; i++) {
            test("testComplexSnippet", i);
        }
        test("testComplexSnippet", 10);
        test("testComplexSnippet", 100);
        test("testComplexSnippet", 1000);
    }

    public static long testSignExtensionSnippet(long arg) {
        long r = 1;
        for (int i = 0; branchProbability(0.99, i < arg); i++) {
            r *= i;
        }
        return r;
    }

    @Test
    public void testSignExtension() {
        test("testSignExtensionSnippet", 9L);
    }
}
