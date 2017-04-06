/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.calc.MulNode;
import org.graalvm.compiler.nodes.java.StoreFieldNode;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.replacements.nodes.arithmetic.IntegerMulExactNode;

@RunWith(Parameterized.class)
public class IntegerMulExactFoldTest extends GraalCompilerTest {

    public static int SideEffectI;
    public static long SideEffectL;

    public static void snippetInt(int a, int b) {
        SideEffectI = Math.multiplyExact(a, b);
    }

    public static void snippetLong(long a, long b) {
        SideEffectL = Math.multiplyExact(a, b);
    }

    private StructuredGraph prepareGraph(String snippet) {
        StructuredGraph graph = parseEager(snippet, AllowAssumptions.NO);
        HighTierContext context = getDefaultHighTierContext();
        new CanonicalizerPhase().apply(graph, context);
        return graph;
    }

    @Parameter(0) public long lowerBound1;
    @Parameter(1) public long upperBound1;
    @Parameter(2) public long lowerBound2;
    @Parameter(3) public long upperBound2;
    @Parameter(4) public int bits;

    @Test
    public void tryFold() {
        assert bits == 32 || bits == 64;

        IntegerStamp a = StampFactory.forInteger(bits, lowerBound1, upperBound1);
        IntegerStamp b = StampFactory.forInteger(bits, lowerBound2, upperBound2);

        // prepare the graph once for the given stamps, if the canonicalize method thinks it does
        // not overflow it will replace the exact mul with a normal mul
        StructuredGraph g = prepareGraph(bits == 32 ? "snippetInt" : "snippetLong");
        List<ParameterNode> params = g.getNodes(ParameterNode.TYPE).snapshot();
        params.get(0).replaceAtMatchingUsages((g.addOrUnique(new PiNode(params.get(0), a))), x -> x instanceof IntegerMulExactNode);
        params.get(1).replaceAtMatchingUsages((g.addOrUnique(new PiNode(params.get(1), b))), x -> x instanceof IntegerMulExactNode);
        new CanonicalizerPhase().apply(g, getDefaultHighTierContext());
        boolean optimized = g.getNodes().filter(IntegerMulExactNode.class).count() == 0;
        ValueNode leftOverMull = optimized ? g.getNodes().filter(MulNode.class).first() : g.getNodes().filter(IntegerMulExactNode.class).first();
        new CanonicalizerPhase().apply(g, getDefaultHighTierContext());
        if (leftOverMull == null) {
            // result may be constant if there is no mul exact or mul node left
            leftOverMull = g.getNodes().filter(StoreFieldNode.class).first().inputs().filter(ConstantNode.class).first();
        }
        if (leftOverMull == null) {
            // even mul got canonicalized so we may end up with one of the original nodes
            leftOverMull = g.getNodes().filter(PiNode.class).first();
        }
        IntegerStamp resultStamp = (IntegerStamp) leftOverMull.stamp();

        // now check for all values in the stamp whether their products overflow overflow
        for (long l1 = lowerBound1; l1 <= upperBound1; l1++) {
            for (long l2 = lowerBound2; l2 <= upperBound2; l2++) {
                try {
                    long res = mulExact(l1, l2, bits);
                    Assert.assertTrue(resultStamp.contains(res));
                } catch (ArithmeticException e) {
                    Assert.assertFalse(optimized);
                }
                if (l2 == Long.MAX_VALUE) {
                    // do not want to overflow the check loop
                    break;
                }
            }
            if (l1 == Long.MAX_VALUE) {
                // do not want to overflow the check loop
                break;
            }
        }

    }

    private static long mulExact(long x, long y, int bits) {
        long r = x * y;
        if (bits == 8) {
            if ((byte) r != r) {
                throw new ArithmeticException("overflow");
            }
        } else if (bits == 16) {
            if ((short) r != r) {
                throw new ArithmeticException("overflow");
            }
        } else if (bits == 32) {
            return Math.multiplyExact((int) x, (int) y);
        } else {
            return Math.multiplyExact(x, y);
        }
        return r;
    }

    @Parameters(name = "a[{0} - {1}] b[{2} - {3}] bits=32")
    public static Collection<Object[]> data() {
        ArrayList<Object[]> tests = new ArrayList<>();

        // zero related
        addTest(tests, -2, 2, 3, 3, 32);
        addTest(tests, 0, 0, 1, 1, 32);
        addTest(tests, 1, 1, 0, 0, 32);
        addTest(tests, -1, 1, 0, 1, 32);
        addTest(tests, -1, 1, 1, 1, 32);
        addTest(tests, -1, 1, -1, 1, 32);

        addTest(tests, -2, 2, 3, 3, 64);
        addTest(tests, 0, 0, 1, 1, 64);
        addTest(tests, 1, 1, 0, 0, 64);
        addTest(tests, -1, 1, 0, 1, 64);
        addTest(tests, -1, 1, 1, 1, 64);
        addTest(tests, -1, 1, -1, 1, 64);

        addTest(tests, -2, 2, 3, 3, 32);
        addTest(tests, 0, 0, 1, 1, 32);
        addTest(tests, 1, 1, 0, 0, 32);
        addTest(tests, -1, 1, 0, 1, 32);
        addTest(tests, -1, 1, 1, 1, 32);
        addTest(tests, -1, 1, -1, 1, 32);

        addTest(tests, 0, 0, 1, 1, 64);
        addTest(tests, 1, 1, 0, 0, 64);
        addTest(tests, -1, 1, 0, 1, 64);
        addTest(tests, -1, 1, 1, 1, 64);
        addTest(tests, -1, 1, -1, 1, 64);

        // bounds
        addTest(tests, Integer.MIN_VALUE, Integer.MIN_VALUE + 0xFF, Integer.MAX_VALUE - 0xFF,
                        Integer.MAX_VALUE, 32);
        addTest(tests, Integer.MIN_VALUE, Integer.MIN_VALUE + 0xFFF, -1, -1, 32);
        addTest(tests, Integer.MIN_VALUE, Integer.MIN_VALUE + 0xFF, Integer.MAX_VALUE - 0xFF,
                        Integer.MAX_VALUE, 64);
        addTest(tests, Integer.MIN_VALUE, Integer.MIN_VALUE + 0xFFF, -1, -1, 64);
        addTest(tests, Long.MIN_VALUE, Long.MIN_VALUE + 0xFFF, -1, -1, 64);

        // constants
        addTest(tests, 2, 2, 2, 2, 32);
        addTest(tests, 1, 1, 2, 2, 32);
        addTest(tests, 2, 2, 4, 4, 32);
        addTest(tests, 3, 3, 3, 3, 32);
        addTest(tests, -4, -4, 3, 3, 32);
        addTest(tests, -4, -4, -3, -3, 32);
        addTest(tests, 4, 4, -3, -3, 32);

        addTest(tests, 2, 2, 2, 2, 64);
        addTest(tests, 1, 1, 2, 2, 64);
        addTest(tests, 3, 3, 3, 3, 64);

        addTest(tests, Long.MAX_VALUE, Long.MAX_VALUE, 1, 1, 64);
        addTest(tests, Long.MAX_VALUE, Long.MAX_VALUE, -1, -1, 64);
        addTest(tests, Long.MIN_VALUE, Long.MIN_VALUE, -1, -1, 64);
        addTest(tests, Long.MIN_VALUE, Long.MIN_VALUE, 1, 1, 64);

        return tests;
    }

    private static void addTest(ArrayList<Object[]> tests, long lowerBound1, long upperBound1, long lowerBound2, long upperBound2, int bits) {
        tests.add(new Object[]{lowerBound1, upperBound1, lowerBound2, upperBound2, bits});
    }

}
