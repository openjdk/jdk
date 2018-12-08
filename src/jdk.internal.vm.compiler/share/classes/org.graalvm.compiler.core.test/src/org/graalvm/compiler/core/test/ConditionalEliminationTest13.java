/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.tiers.PhaseContext;
import org.junit.Ignore;
import org.junit.Test;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class ConditionalEliminationTest13 extends ConditionalEliminationTestBase {

    @Override
    protected InlineInvokePlugin.InlineInfo bytecodeParserShouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
        return InlineInvokePlugin.InlineInfo.createStandardInlineInfo(method);
    }

    public static void referenceSnippet1(int a) {
        if (Integer.compareUnsigned(a, a + 1) < 0) {
            sink1 = 0;
        } else {
            sink0 = -1;
        }
    }

    public static void testSnippet1(int a) {
        if (Integer.compareUnsigned(a, a + 1) < 0 || a == 0) {
            sink1 = 0;
        } else {
            sink0 = -1;
        }
    }

    public static void referenceSnippet2(int a) {
        if (0 < a) {
            sink1 = 0;
        }
        sink0 = -1;
    }

    public static void testSnippet2(int a) {
        if (0 < a) {
            if (a == -1) {
                sink2 = -2;
            }
            sink1 = 0;
        }
        sink0 = -1;
    }

    public static void testSnippet3(int a) {
        if (0 < a) {
            if (a == 1) {
                sink2 = -2;
            }
            sink1 = 0;
        }
        sink0 = -1;
    }

    @SuppressWarnings("unused")
    public static void referenceSnippet4(int a) {
        sink1 = 0;
    }

    public static void testSnippet4(int a) {
        if (Integer.compareUnsigned(a - 1, a) < 0 || a == 0) {
            sink1 = 0;
        } else {
            sink0 = -1;
        }
    }

    public static void testSnippet5(int a) {
        if (a < 0) {
            if (a == -1) {
                sink2 = -2;
            }
            sink1 = 0;
        }
        sink0 = -1;
    }

    public static void referenceSnippet6(int a) {
        if (a < 0) {
            sink1 = 0;
        }
        sink0 = -1;
    }

    public static void testSnippet6(int a) {
        if (a < 0) {
            if (a == 0) {
                sink2 = -2;
            }
            sink1 = 0;
        }
        sink0 = -1;
    }

    public static void testSnippet7(int a) {
        if (0 < a) {
            if (a == 0) {
                sink2 = -2;
            }
            sink1 = 0;
        }
        sink0 = -1;
    }

    public static void testSnippet8(int a) {
        if (Integer.compareUnsigned(a, a + 1) < 0 || a == 0xffff_ffff) {
            sink1 = 0;
        } else {
            sink0 = -1;
        }
    }

    public static void referenceSnippet9(int a) {
        if (Integer.compareUnsigned(a - 1, a) < 0) {
            sink1 = 0;
        } else {
            sink0 = -1;
        }
    }

    public static void testSnippet9(int a) {
        if (Integer.compareUnsigned(a - 1, a) < 0 || a == 0xffff_ffff) {
            sink1 = 0;
        } else {
            sink0 = -1;
        }
    }

    private static int either(int a, int b) {
        return (sink0 + sink1 + sink2) == 0 ? a : b;
    }

    public static void testSnippet10(int a) {
        if (Integer.compareUnsigned(a, a + either(1, 2)) < 0 || a == 0xffff_ffff || a == 0xffff_fffe) {
            sink1 = 0;
        } else {
            sink0 = -1;
        }
    }

    public static void referenceSnippet11(int a) {
        if (Integer.compareUnsigned(a, Integer.MAX_VALUE + 1) > 0) {
            sink1 = 0;
        }
        sink0 = -1;
    }

    public static void testSnippet11(int a) {
        if (Integer.compareUnsigned(a, Integer.MAX_VALUE + 1) > 0) {
            if (Integer.compareUnsigned(a, 42) <= 0) {
                sink2 = -2;
            }
            sink1 = 0;
        }
        sink0 = -1;
    }

    public static void referenceSnippet12(int a) {
        if (Integer.compareUnsigned(a, 0xffff_ffff) >= 0) {
            sink1 = 0;
        } else {
            sink0 = -1;
        }
    }

    public static void testSnippet12(int a) {
        if (Integer.compareUnsigned(a, 0xffff_ffff) >= 0 && a == 0xffff_ffff) {
            sink1 = 0;
        } else {
            sink0 = -1;
        }
    }

    public static void testSnippet13(int a) {
        int x = either(0, 1);
        if (a <= a + x) {
            if (a == Integer.MAX_VALUE) {
                sink2 = -2;
            }
            sink1 = 0;
        } else {
            sink0 = -1;
        }
    }

    public static void referenceSnippet14(int a) {
        int x = either(0, 1);
        if (a < a + x) {
            sink1 = 0;
        } else {
            sink0 = -1;
        }
    }

    public static void testSnippet14(int a) {
        int x = either(0, 1);
        if (a < a + x) {
            if (a == Integer.MAX_VALUE) {
                sink2 = -2;
            }
            sink1 = 0;
        } else {
            sink0 = -1;
        }
    }

    @Test
    public void test1() {
        testConditionalElimination("testSnippet1", "referenceSnippet1");
    }

    @Test
    public void test2() {
        testConditionalElimination("testSnippet2", "referenceSnippet2");
    }

    @Test
    public void test3() {
        testConditionalElimination("testSnippet3", "testSnippet3");
    }

    @Test
    public void test4() {
        testConditionalElimination("testSnippet4", "referenceSnippet4");
    }

    @Test
    public void test5() {
        testConditionalElimination("testSnippet5", "testSnippet5");
    }

    @Test
    public void test6() {
        testConditionalElimination("testSnippet6", "referenceSnippet6");
    }

    @Test
    public void test7() {
        testConditionalElimination("testSnippet7", "referenceSnippet2");
    }

    @Test
    public void test8() {
        testConditionalElimination("testSnippet8", "referenceSnippet4");
    }

    @Test
    public void test9() {
        testConditionalElimination("testSnippet9", "referenceSnippet9");
    }

    @Ignore("Need better unsigned stamps for this conditional elimination to work.")
    @Test
    public void test10() {
        testConditionalElimination("testSnippet10", "referenceSnippet4");
    }

    @Test
    public void test11() {
        testConditionalElimination("testSnippet11", "referenceSnippet11");
    }

    @Test
    public void test12() {
        testConditionalElimination("testSnippet12", "referenceSnippet12");
    }

    @Test
    public void test13() {
        testConditionalElimination("testSnippet13", "testSnippet13");
    }

    @Test
    public void test14() {
        testConditionalElimination("testSnippet14", "referenceSnippet14");
    }

    @Override
    protected void prepareGraph(StructuredGraph graph, CanonicalizerPhase canonicalizer, PhaseContext context, boolean applyLowering) {
        super.prepareGraph(graph, canonicalizer, context, applyLowering);
        graph.clearAllStateAfter();
        graph.setGuardsStage(StructuredGraph.GuardsStage.AFTER_FSA);
        DebugContext debug = graph.getDebug();
        debug.dump(DebugContext.BASIC_LEVEL, graph, "After preparation");
        canonicalizer.apply(graph, context);
    }
}
