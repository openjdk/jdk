/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.tiers.PhaseContext;
import org.junit.Test;

/**
 * In the following tests, the scalar type system of the compiler should be complete enough to see
 * the relation between the different conditions.
 */
public class ScalarTypeSystemTest extends GraalCompilerTest {

    public static int referenceSnippet1(int a) {
        if (a > 0) {
            return 1;
        } else {
            return 2;
        }
    }

    @Test(expected = AssertionError.class)
    public void test1() {
        test("test1Snippet", "referenceSnippet1");
    }

    public static int test1Snippet(int a) {
        if (a > 0) {
            if (a > -1) {
                return 1;
            } else {
                return 3;
            }
        } else {
            return 2;
        }
    }

    @Test(expected = AssertionError.class)
    public void test2() {
        test("test2Snippet", "referenceSnippet1");
    }

    public static int test2Snippet(int a) {
        if (a > 0) {
            if (a == -15) {
                return 3;
            } else {
                return 1;
            }
        } else {
            return 2;
        }
    }

    @Test(expected = AssertionError.class)
    public void test3() {
        test("test3Snippet", "referenceSnippet2");
    }

    public static int referenceSnippet2(int a, int b) {
        if (a > b) {
            return 1;
        } else {
            return 2;
        }
    }

    public static int test3Snippet(int a, int b) {
        if (a > b) {
            if (a == b) {
                return 3;
            } else {
                return 1;
            }
        } else {
            return 2;
        }
    }

    public static int referenceSnippet3(int a, int b) {
        if (a == b) {
            return 1;
        } else {
            return 2;
        }
    }

    @Test(expected = AssertionError.class)
    public void test6() {
        test("test6Snippet", "referenceSnippet3");
    }

    public static int test6Snippet(int a, int b) {
        if (a == b) {
            if (a == b + 1) {
                return 3;
            } else {
                return 1;
            }
        } else {
            return 2;
        }
    }

    private void test(final String snippet, final String referenceSnippet) {
        // No debug scope to reduce console noise for @Test(expected = ...) tests
        StructuredGraph graph = parseEager(snippet, AllowAssumptions.NO);
        graph.getDebug().dump(DebugContext.BASIC_LEVEL, graph, "Graph");
        PhaseContext context = new PhaseContext(getProviders());
        new CanonicalizerPhase().apply(graph, context);
        StructuredGraph referenceGraph = parseEager(referenceSnippet, AllowAssumptions.NO);
        assertEquals(referenceGraph, graph);
    }
}
