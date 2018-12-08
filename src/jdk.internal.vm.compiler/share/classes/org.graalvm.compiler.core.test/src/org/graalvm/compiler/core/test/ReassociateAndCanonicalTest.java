/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.core.test;

import org.junit.Test;

import org.graalvm.compiler.graph.IterableNodeType;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.tiers.PhaseContext;

public class ReassociateAndCanonicalTest extends GraalCompilerTest {

    public static int rnd = (int) (Math.random() * 100);

    @Test
    public void test1() {
        test("test1Snippet", "ref1Snippet");
    }

    public static int test1Snippet() {
        return 1 + (rnd + 2);
    }

    public static int ref1Snippet() {
        return rnd + 3;
    }

    @Test
    public void test2() {
        test("test2Snippet", "ref2Snippet");
    }

    public static int test2Snippet() {
        return rnd + 3;
    }

    public static int ref2Snippet() {
        return (rnd + 2) + 1;
    }

    @Test
    public void test3() {
        test("test3Snippet", "ref3Snippet");
    }

    public static int test3Snippet() {
        return rnd + 3;
    }

    public static int ref3Snippet() {
        return 1 + (2 + rnd);
    }

    @Test
    public void test4() {
        test("test4Snippet", "ref4Snippet");
    }

    public static int test4Snippet() {
        return rnd + 3;
    }

    public static int ref4Snippet() {
        return (2 + rnd) + 1;
    }

    @Test
    public void test5() {
        test("test5Snippet", "ref5Snippet");
    }

    public static int test5Snippet() {
        return -1 - rnd;
    }

    public static int ref5Snippet() {
        return 1 - (rnd + 2);
    }

    @Test
    public void test6() {
        test("test6Snippet", "ref6Snippet");
    }

    public static int test6Snippet() {
        return rnd + 1;
    }

    public static int ref6Snippet() {
        return (rnd + 2) - 1;
    }

    @Test
    public void test7() {
        test("test7Snippet", "ref7Snippet");
    }

    public static int test7Snippet() {
        return -1 - rnd;
    }

    public static int ref7Snippet() {
        return 1 - (2 + rnd);
    }

    @Test
    public void test8() {
        test("test8Snippet", "ref8Snippet");
    }

    public static int test8Snippet() {
        return rnd + 1;
    }

    public static int ref8Snippet() {
        return (2 + rnd) - 1;
    }

    @Test
    public void test9() {
        test("test9Snippet", "ref9Snippet");
    }

    public static int test9Snippet() {
        return rnd - 1;
    }

    public static int ref9Snippet() {
        return 1 + (rnd - 2);
    }

    @Test
    public void test10() {
        test("test10Snippet", "ref10Snippet");
    }

    public static int test10Snippet() {
        return rnd - 1;
    }

    public static int ref10Snippet() {
        return (rnd - 2) + 1;
    }

    @Test
    public void test11() {
        test("test11Snippet", "ref11Snippet");
    }

    public static int test11Snippet() {
        return -rnd + 3;
    }

    public static int ref11Snippet() {
        return 1 + (2 - rnd);
    }

    @Test
    public void test12() {
        test("test12Snippet", "ref12Snippet");
    }

    public static int test12Snippet() {
        return -rnd + 3;
    }

    public static int ref12Snippet() {
        return (2 - rnd) + 1;
    }

    @Test
    public void test13() {
        test("test13Snippet", "ref13Snippet");
    }

    public static int test13Snippet() {
        return -rnd + 3;
    }

    public static int ref13Snippet() {
        return 1 - (rnd - 2);
    }

    @Test
    public void test14() {
        test("test14Snippet", "ref14Snippet");
    }

    public static int test14Snippet() {
        return rnd - 3;
    }

    public static int ref14Snippet() {
        return (rnd - 2) - 1;
    }

    @Test
    public void test15() {
        test("test15Snippet", "ref15Snippet");
    }

    public static int test15Snippet() {
        return rnd + -1;
    }

    public static int ref15Snippet() {
        return 1 - (2 - rnd);
    }

    @Test
    public void test16() {
        test("test16Snippet", "ref16Snippet");
    }

    public static int test16Snippet() {
        return -rnd + 1;
    }

    public static int ref16Snippet() {
        return (2 - rnd) - 1;
    }

    private <T extends Node & IterableNodeType> void test(String test, String ref) {
        StructuredGraph testGraph = parseEager(test, AllowAssumptions.NO);
        new CanonicalizerPhase().apply(testGraph, new PhaseContext(getProviders()));
        StructuredGraph refGraph = parseEager(ref, AllowAssumptions.NO);
        new CanonicalizerPhase().apply(refGraph, new PhaseContext(getProviders()));
        assertEquals(testGraph, refGraph);
    }
}
