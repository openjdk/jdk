/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.nodes.test;

import java.util.function.Function;

import org.graalvm.compiler.api.test.Graal;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.ShortCircuitOrNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.ConditionalNode;
import org.graalvm.compiler.nodes.calc.IntegerEqualsNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.phases.tiers.Suites;
import org.graalvm.compiler.runtime.RuntimeProvider;
import org.junit.Assert;
import org.junit.Test;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class ShortCircuitOrNodeTest extends GraalCompilerTest {

    static boolean shortCircuitOr(boolean b1, boolean b2) {
        return b1 || b2;
    }

    @Override
    protected void registerInvocationPlugins(InvocationPlugins invocationPlugins) {
        Registration r = new Registration(invocationPlugins, ShortCircuitOrNodeTest.class);
        r.register2("shortCircuitOr", boolean.class, boolean.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode b1, ValueNode b2) {
                LogicNode x = b.add(new IntegerEqualsNode(b1, b.add(ConstantNode.forInt(1))));
                LogicNode y = b.add(new IntegerEqualsNode(b2, b.add(ConstantNode.forInt(1))));
                ShortCircuitOrNode compare = b.add(new ShortCircuitOrNode(x, false, y, false, 0.5));
                b.addPush(JavaKind.Boolean, new ConditionalNode(compare, b.add(ConstantNode.forBoolean(true)), b.add(ConstantNode.forBoolean(false))));
                return true;
            }
        });
        super.registerInvocationPlugins(invocationPlugins);
    }

    public static int testSharedConditionSnippet(Object o) {
        boolean b2 = o != null;
        boolean b1 = o instanceof Function;
        if (b1) {
            if (shortCircuitOr(b1, b2)) {
                return 4;
            } else {
                return 3;
            }
        }
        return 1;
    }

    @Test
    public void testSharedCondition() {
        test("testSharedConditionSnippet", "String");
    }

    private int testInputCombinations(String snippet) {
        int trueCount = 0;
        for (int i = 0; i < 4; ++i) {
            boolean aValue = (i <= 1);
            boolean bValue = ((i % 2) == 0);
            boolean returnValue = (boolean) test(snippet, new Object[]{aValue, bValue}).returnValue;

            if (returnValue) {
                trueCount++;
            }
        }

        return trueCount;
    }

    public boolean testSimpleSnippet(Boolean a, Boolean b) {
        return shortCircuitOr(a, b);
    }

    @Test
    public void testSimple() {
        testInputCombinations("testSimpleSnippet");
    }

    // We cannot trust subword inputs. Parameter declared as boolean will have a stamp of int32.
    public static boolean testCascadeSnippet1(Boolean a, Boolean b) {
        return shortCircuitOr(shortCircuitOr(a, b), a);
    }

    public static boolean testCascadeSnippet2(Boolean a, Boolean b) {
        return shortCircuitOr(shortCircuitOr(b, a), a);
    }

    public static boolean testCascadeSnippet3(Boolean a, Boolean b) {
        return shortCircuitOr(a, shortCircuitOr(a, b));
    }

    public static boolean testCascadeSnippet4(Boolean a, Boolean b) {
        return shortCircuitOr(a, shortCircuitOr(b, a));
    }

    public static boolean testCascadeSnippet5(Boolean a, Boolean b) {
        return shortCircuitOr(!shortCircuitOr(a, b), a);
    }

    public static boolean testCascadeSnippet6(Boolean a, Boolean b) {
        return shortCircuitOr(!shortCircuitOr(b, a), a);
    }

    public static boolean testCascadeSnippet7(Boolean a, Boolean b) {
        return shortCircuitOr(!a, shortCircuitOr(a, b));
    }

    public static boolean testCascadeSnippet8(Boolean a, Boolean b) {
        return shortCircuitOr(!a, shortCircuitOr(b, a));
    }

    public static boolean testCascadeSnippet9(Boolean a, Boolean b) {
        return shortCircuitOr(shortCircuitOr(!a, b), a);
    }

    public static boolean testCascadeSnippet10(Boolean a, Boolean b) {
        return shortCircuitOr(shortCircuitOr(!b, a), a);
    }

    public static boolean testCascadeSnippet11(Boolean a, Boolean b) {
        return shortCircuitOr(a, !shortCircuitOr(a, b));
    }

    public static boolean testCascadeSnippet12(Boolean a, Boolean b) {
        return shortCircuitOr(a, !shortCircuitOr(b, a));
    }

    public static boolean testCascadeSnippet13(Boolean a, Boolean b) {
        return shortCircuitOr(!shortCircuitOr(!a, b), a);
    }

    public static boolean testCascadeSnippet14(Boolean a, Boolean b) {
        return shortCircuitOr(!shortCircuitOr(!b, a), a);
    }

    public static boolean testCascadeSnippet15(Boolean a, Boolean b) {
        return shortCircuitOr(!a, !shortCircuitOr(a, b));
    }

    public static boolean testCascadeSnippet16(Boolean a, Boolean b) {
        return shortCircuitOr(!a, !shortCircuitOr(!b, a));
    }

    public static boolean testCascadeSnippet17(Boolean a, Boolean b) {
        return shortCircuitOr(shortCircuitOr(a, !b), a);
    }

    public static boolean testCascadeSnippet18(Boolean a, Boolean b) {
        return shortCircuitOr(shortCircuitOr(b, !a), a);
    }

    public static boolean testCascadeSnippet19(Boolean a, Boolean b) {
        return shortCircuitOr(a, shortCircuitOr(!a, b));
    }

    public static boolean testCascadeSnippet20(Boolean a, Boolean b) {
        return shortCircuitOr(a, shortCircuitOr(!b, a));
    }

    public static boolean testCascadeSnippet21(Boolean a, Boolean b) {
        return shortCircuitOr(!shortCircuitOr(a, !b), a);
    }

    public static boolean testCascadeSnippet22(Boolean a, Boolean b) {
        return shortCircuitOr(!shortCircuitOr(b, !a), a);
    }

    public static boolean testCascadeSnippet23(Boolean a, Boolean b) {
        return shortCircuitOr(!a, shortCircuitOr(!a, b));
    }

    public static boolean testCascadeSnippet24(Boolean a, Boolean b) {
        return shortCircuitOr(!a, shortCircuitOr(!b, a));
    }

    public static boolean testCascadeSnippet25(Boolean a, Boolean b) {
        return shortCircuitOr(shortCircuitOr(!a, !b), a);
    }

    public static boolean testCascadeSnippet26(Boolean a, Boolean b) {
        return shortCircuitOr(shortCircuitOr(!b, !a), a);
    }

    public static boolean testCascadeSnippet27(Boolean a, Boolean b) {
        return shortCircuitOr(a, !shortCircuitOr(!a, b));
    }

    public static boolean testCascadeSnippet28(Boolean a, Boolean b) {
        return shortCircuitOr(a, !shortCircuitOr(!b, a));
    }

    public static boolean testCascadeSnippet29(Boolean a, Boolean b) {
        return shortCircuitOr(!shortCircuitOr(!a, !b), a);
    }

    public static boolean testCascadeSnippet30(Boolean a, Boolean b) {
        return shortCircuitOr(!shortCircuitOr(!b, !a), a);
    }

    public static boolean testCascadeSnippet31(Boolean a, Boolean b) {
        return shortCircuitOr(!a, !shortCircuitOr(!a, b));
    }

    public static boolean testCascadeSnippet32(Boolean a, Boolean b) {
        return shortCircuitOr(!a, !shortCircuitOr(!b, a));
    }

    public static boolean testCascadeSnippet33(Boolean a, Boolean b) {
        return shortCircuitOr(shortCircuitOr(a, b), !a);
    }

    public static boolean testCascadeSnippet34(Boolean a, Boolean b) {
        return shortCircuitOr(shortCircuitOr(b, a), !a);
    }

    public static boolean testCascadeSnippet35(Boolean a, Boolean b) {
        return shortCircuitOr(a, shortCircuitOr(a, !b));
    }

    public static boolean testCascadeSnippet36(Boolean a, Boolean b) {
        return shortCircuitOr(a, shortCircuitOr(b, !a));
    }

    public static boolean testCascadeSnippet37(Boolean a, Boolean b) {
        return shortCircuitOr(!shortCircuitOr(a, b), !a);
    }

    public static boolean testCascadeSnippet38(Boolean a, Boolean b) {
        return shortCircuitOr(!shortCircuitOr(b, a), !a);
    }

    public static boolean testCascadeSnippet39(Boolean a, Boolean b) {
        return shortCircuitOr(!a, shortCircuitOr(a, !b));
    }

    public static boolean testCascadeSnippet40(Boolean a, Boolean b) {
        return shortCircuitOr(!a, shortCircuitOr(b, !a));
    }

    public static boolean testCascadeSnippet41(Boolean a, Boolean b) {
        return shortCircuitOr(shortCircuitOr(!a, b), !a);
    }

    public static boolean testCascadeSnippet42(Boolean a, Boolean b) {
        return shortCircuitOr(shortCircuitOr(!b, a), !a);
    }

    public static boolean testCascadeSnippet43(Boolean a, Boolean b) {
        return shortCircuitOr(a, !shortCircuitOr(a, !b));
    }

    public static boolean testCascadeSnippet44(Boolean a, Boolean b) {
        return shortCircuitOr(a, !shortCircuitOr(b, !a));
    }

    public static boolean testCascadeSnippet45(Boolean a, Boolean b) {
        return shortCircuitOr(!shortCircuitOr(!a, b), !a);
    }

    public static boolean testCascadeSnippet46(Boolean a, Boolean b) {
        return shortCircuitOr(!shortCircuitOr(!b, a), !a);
    }

    public static boolean testCascadeSnippet47(Boolean a, Boolean b) {
        return shortCircuitOr(!a, !shortCircuitOr(a, !b));
    }

    public static boolean testCascadeSnippet48(Boolean a, Boolean b) {
        return shortCircuitOr(!a, !shortCircuitOr(!b, !a));
    }

    public static boolean testCascadeSnippet49(Boolean a, Boolean b) {
        return shortCircuitOr(shortCircuitOr(a, !b), !a);
    }

    public static boolean testCascadeSnippet50(Boolean a, Boolean b) {
        return shortCircuitOr(shortCircuitOr(b, !a), !a);
    }

    public static boolean testCascadeSnippet51(Boolean a, Boolean b) {
        return shortCircuitOr(a, shortCircuitOr(!a, !b));
    }

    public static boolean testCascadeSnippet52(Boolean a, Boolean b) {
        return shortCircuitOr(a, shortCircuitOr(!b, !a));
    }

    public static boolean testCascadeSnippet53(Boolean a, Boolean b) {
        return shortCircuitOr(!shortCircuitOr(a, !b), !a);
    }

    public static boolean testCascadeSnippet54(Boolean a, Boolean b) {
        return shortCircuitOr(!shortCircuitOr(b, !a), !a);
    }

    public static boolean testCascadeSnippet55(Boolean a, Boolean b) {
        return shortCircuitOr(!a, shortCircuitOr(!a, !b));
    }

    public static boolean testCascadeSnippet56(Boolean a, Boolean b) {
        return shortCircuitOr(!a, shortCircuitOr(!b, !a));
    }

    public static boolean testCascadeSnippet57(Boolean a, Boolean b) {
        return shortCircuitOr(shortCircuitOr(!a, !b), !a);
    }

    public static boolean testCascadeSnippet58(Boolean a, Boolean b) {
        return shortCircuitOr(shortCircuitOr(!b, !a), !a);
    }

    public static boolean testCascadeSnippet59(Boolean a, Boolean b) {
        return shortCircuitOr(a, !shortCircuitOr(!a, !b));
    }

    public static boolean testCascadeSnippet60(Boolean a, Boolean b) {
        return shortCircuitOr(a, !shortCircuitOr(!b, !a));
    }

    public static boolean testCascadeSnippet61(Boolean a, Boolean b) {
        return shortCircuitOr(!shortCircuitOr(!a, !b), !a);
    }

    public static boolean testCascadeSnippet62(Boolean a, Boolean b) {
        return shortCircuitOr(!shortCircuitOr(!b, !a), !a);
    }

    public static boolean testCascadeSnippet63(Boolean a, Boolean b) {
        return shortCircuitOr(!a, !shortCircuitOr(!a, !b));
    }

    public static boolean testCascadeSnippet64(Boolean a, Boolean b) {
        return shortCircuitOr(!a, !shortCircuitOr(!b, !a));
    }

    @Test
    public void testCascade() {
        for (int i = 1; i <= 64; ++i) {
            String snippet = "testCascadeSnippet" + i;
            StructuredGraph graph = parseEager(snippet, AllowAssumptions.YES);
            Suites s = Graal.getRequiredCapability(RuntimeProvider.class).getHostBackend().getSuites().getDefaultSuites(getInitialOptions());
            s.getHighTier().apply(graph, getDefaultHighTierContext());
            s.getMidTier().apply(graph, getDefaultMidTierContext());
            int shortCircuitCount = graph.getNodes(ShortCircuitOrNode.TYPE).count();

            int trueCount = testInputCombinations(snippet);

            if (trueCount % 2 == 0) {
                // No ShortCircuitOrNode expected in the graph.
                Assert.assertEquals(0, shortCircuitCount);
            } else {
                // Only a single ShortCircuitOrNode expected in the graph.
                Assert.assertEquals(1, shortCircuitCount);
            }
        }
    }
}
