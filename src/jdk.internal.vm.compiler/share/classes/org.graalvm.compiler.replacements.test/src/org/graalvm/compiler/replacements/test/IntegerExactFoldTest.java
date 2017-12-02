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
package org.graalvm.compiler.replacements.test;

import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.StructuredGraph.GuardsStage;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.LoweringPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.tiers.PhaseContext;
import org.graalvm.compiler.replacements.nodes.arithmetic.IntegerExactArithmeticNode;
import org.graalvm.compiler.replacements.nodes.arithmetic.IntegerExactArithmeticSplitNode;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IntegerExactFoldTest extends GraalCompilerTest {
    private final long lowerBoundA;
    private final long upperBoundA;
    private final long lowerBoundB;
    private final long upperBoundB;
    private final int bits;
    private final Operation operation;

    public IntegerExactFoldTest(long lowerBoundA, long upperBoundA, long lowerBoundB, long upperBoundB, int bits, Operation operation) {
        this.lowerBoundA = lowerBoundA;
        this.upperBoundA = upperBoundA;
        this.lowerBoundB = lowerBoundB;
        this.upperBoundB = upperBoundB;
        this.bits = bits;
        this.operation = operation;

        assert bits == 32 || bits == 64;
        assert lowerBoundA <= upperBoundA;
        assert lowerBoundB <= upperBoundB;
        assert bits == 64 || isInteger(lowerBoundA);
        assert bits == 64 || isInteger(upperBoundA);
        assert bits == 64 || isInteger(lowerBoundB);
        assert bits == 64 || isInteger(upperBoundB);
    }

    @Test
    public void testFolding() {
        StructuredGraph graph = prepareGraph();
        IntegerStamp a = StampFactory.forInteger(bits, lowerBoundA, upperBoundA);
        IntegerStamp b = StampFactory.forInteger(bits, lowerBoundB, upperBoundB);

        List<ParameterNode> params = graph.getNodes(ParameterNode.TYPE).snapshot();
        params.get(0).replaceAtMatchingUsages(graph.addOrUnique(new PiNode(params.get(0), a)), x -> x instanceof IntegerExactArithmeticNode);
        params.get(1).replaceAtMatchingUsages(graph.addOrUnique(new PiNode(params.get(1), b)), x -> x instanceof IntegerExactArithmeticNode);

        Node originalNode = graph.getNodes().filter(x -> x instanceof IntegerExactArithmeticNode).first();
        assertNotNull("original node must be in the graph", originalNode);

        new CanonicalizerPhase().apply(graph, getDefaultHighTierContext());
        ValueNode node = findNode(graph);
        boolean overflowExpected = node instanceof IntegerExactArithmeticNode;

        IntegerStamp resultStamp = (IntegerStamp) node.stamp(NodeView.DEFAULT);
        operation.verifyOverflow(lowerBoundA, upperBoundA, lowerBoundB, upperBoundB, bits, overflowExpected, resultStamp);
    }

    @Test
    public void testFoldingAfterLowering() {
        StructuredGraph graph = prepareGraph();

        Node originalNode = graph.getNodes().filter(x -> x instanceof IntegerExactArithmeticNode).first();
        assertNotNull("original node must be in the graph", originalNode);

        graph.setGuardsStage(GuardsStage.FIXED_DEOPTS);
        CanonicalizerPhase canonicalizer = new CanonicalizerPhase();
        PhaseContext context = new PhaseContext(getProviders());
        new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.HIGH_TIER).apply(graph, context);
        IntegerExactArithmeticSplitNode loweredNode = graph.getNodes().filter(IntegerExactArithmeticSplitNode.class).first();
        assertNotNull("the lowered node must be in the graph", loweredNode);

        loweredNode.getX().setStamp(StampFactory.forInteger(bits, lowerBoundA, upperBoundA));
        loweredNode.getY().setStamp(StampFactory.forInteger(bits, lowerBoundB, upperBoundB));
        new CanonicalizerPhase().apply(graph, context);

        ValueNode node = findNode(graph);
        boolean overflowExpected = node instanceof IntegerExactArithmeticSplitNode;

        IntegerStamp resultStamp = (IntegerStamp) node.stamp(NodeView.DEFAULT);
        operation.verifyOverflow(lowerBoundA, upperBoundA, lowerBoundB, upperBoundB, bits, overflowExpected, resultStamp);
    }

    private static boolean isInteger(long value) {
        return value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE;
    }

    private static ValueNode findNode(StructuredGraph graph) {
        ValueNode resultNode = graph.getNodes().filter(ReturnNode.class).first().result();
        assertNotNull("some node must be the returned value", resultNode);
        return resultNode;
    }

    protected StructuredGraph prepareGraph() {
        String snippet = "snippetInt" + bits;
        StructuredGraph graph = parseEager(getResolvedJavaMethod(operation.getClass(), snippet), AllowAssumptions.NO);
        HighTierContext context = getDefaultHighTierContext();
        new CanonicalizerPhase().apply(graph, context);
        return graph;
    }

    private static void addTest(ArrayList<Object[]> tests, long lowerBound1, long upperBound1, long lowerBound2, long upperBound2, int bits, Operation operation) {
        tests.add(new Object[]{lowerBound1, upperBound1, lowerBound2, upperBound2, bits, operation});
    }

    @Parameters(name = "a[{0} / {1}], b[{2} / {3}], bits={4}, operation={5}")
    public static Collection<Object[]> data() {
        ArrayList<Object[]> tests = new ArrayList<>();

        Operation[] operations = new Operation[]{new AddOperation(), new SubOperation(), new MulOperation()};
        for (Operation operation : operations) {
            for (int bits : new int[]{32, 64}) {
                // zero related
                addTest(tests, 0, 0, 1, 1, bits, operation);
                addTest(tests, 1, 1, 0, 0, bits, operation);
                addTest(tests, -1, 1, 0, 1, bits, operation);

                // bounds
                addTest(tests, -2, 2, 3, 3, bits, operation);
                addTest(tests, -1, 1, 1, 1, bits, operation);
                addTest(tests, -1, 1, -1, 1, bits, operation);

                addTest(tests, Integer.MIN_VALUE, Integer.MIN_VALUE + 0xF, Integer.MAX_VALUE - 0xF, Integer.MAX_VALUE, bits, operation);
                addTest(tests, Integer.MIN_VALUE, Integer.MIN_VALUE + 0xF, -1, -1, bits, operation);
                addTest(tests, Integer.MAX_VALUE, Integer.MAX_VALUE, -1, -1, bits, operation);
                addTest(tests, Integer.MIN_VALUE, Integer.MIN_VALUE, -1, -1, bits, operation);
                addTest(tests, Integer.MAX_VALUE, Integer.MAX_VALUE, 1, 1, bits, operation);
                addTest(tests, Integer.MIN_VALUE, Integer.MIN_VALUE, 1, 1, bits, operation);
            }

            // bit-specific test cases
            addTest(tests, Integer.MIN_VALUE, Integer.MIN_VALUE + 0xF, Integer.MAX_VALUE - 0xF, Integer.MAX_VALUE, 64, operation);
            addTest(tests, Integer.MIN_VALUE, Integer.MIN_VALUE + 0xF, -1, -1, 64, operation);
        }

        return tests;
    }

    private abstract static class Operation {
        abstract void verifyOverflow(long lowerBoundA, long upperBoundA, long lowerBoundB, long upperBoundB, int bits, boolean overflowExpected, IntegerStamp resultStamp);
    }

    private static final class AddOperation extends Operation {
        @Override
        public void verifyOverflow(long lowerBoundA, long upperBoundA, long lowerBoundB, long upperBoundB, int bits, boolean overflowExpected, IntegerStamp resultStamp) {
            try {
                long res = addExact(lowerBoundA, lowerBoundB, bits);
                resultStamp.contains(res);
                res = addExact(upperBoundA, upperBoundB, bits);
                resultStamp.contains(res);
                Assert.assertFalse(overflowExpected);
            } catch (ArithmeticException e) {
                Assert.assertTrue(overflowExpected);
            }
        }

        private static long addExact(long x, long y, int bits) {
            if (bits == 32) {
                return Math.addExact((int) x, (int) y);
            } else {
                return Math.addExact(x, y);
            }
        }

        @SuppressWarnings("unused")
        public static int snippetInt32(int a, int b) {
            return Math.addExact(a, b);
        }

        @SuppressWarnings("unused")
        public static long snippetInt64(long a, long b) {
            return Math.addExact(a, b);
        }
    }

    private static final class SubOperation extends Operation {
        @Override
        public void verifyOverflow(long lowerBoundA, long upperBoundA, long lowerBoundB, long upperBoundB, int bits, boolean overflowExpected, IntegerStamp resultStamp) {
            try {
                long res = subExact(lowerBoundA, upperBoundB, bits);
                Assert.assertTrue(resultStamp.contains(res));
                res = subExact(upperBoundA, lowerBoundB, bits);
                Assert.assertTrue(resultStamp.contains(res));
                Assert.assertFalse(overflowExpected);
            } catch (ArithmeticException e) {
                Assert.assertTrue(overflowExpected);
            }
        }

        private static long subExact(long x, long y, int bits) {
            if (bits == 32) {
                return Math.subtractExact((int) x, (int) y);
            } else {
                return Math.subtractExact(x, y);
            }
        }

        @SuppressWarnings("unused")
        public static int snippetInt32(int a, int b) {
            return Math.subtractExact(a, b);
        }

        @SuppressWarnings("unused")
        public static long snippetInt64(long a, long b) {
            return Math.subtractExact(a, b);
        }
    }

    private static final class MulOperation extends Operation {
        @Override
        public void verifyOverflow(long lowerBoundA, long upperBoundA, long lowerBoundB, long upperBoundB, int bits, boolean overflowExpected, IntegerStamp resultStamp) {
            // now check for all values in the stamp whether their products overflow overflow
            boolean overflowOccurred = false;

            for (long l1 = lowerBoundA; l1 <= upperBoundA; l1++) {
                for (long l2 = lowerBoundB; l2 <= upperBoundB; l2++) {
                    try {
                        long res = mulExact(l1, l2, bits);
                        Assert.assertTrue(resultStamp.contains(res));
                    } catch (ArithmeticException e) {
                        overflowOccurred = true;
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

            Assert.assertEquals(overflowExpected, overflowOccurred);
        }

        private static long mulExact(long x, long y, int bits) {
            if (bits == 32) {
                return Math.multiplyExact((int) x, (int) y);
            } else {
                return Math.multiplyExact(x, y);
            }
        }

        @SuppressWarnings("unused")
        public static int snippetInt32(int a, int b) {
            return Math.multiplyExact(a, b);
        }

        @SuppressWarnings("unused")
        public static long snippetInt64(long a, long b) {
            return Math.multiplyExact(a, b);
        }
    }
}
