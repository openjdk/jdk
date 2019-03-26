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


package org.graalvm.compiler.core.test.ea;

import java.util.List;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.loop.DefaultLoopPolicies;
import org.graalvm.compiler.loop.phases.LoopFullUnrollPhase;
import org.graalvm.compiler.loop.phases.LoopPeelingPhase;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.extended.BoxNode;
import org.graalvm.compiler.nodes.extended.ValueAnchorNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.virtual.AllocatedObjectNode;
import org.graalvm.compiler.nodes.virtual.CommitAllocationNode;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.schedule.SchedulePhase;
import org.graalvm.compiler.virtual.phases.ea.PartialEscapePhase;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import jdk.vm.ci.meta.JavaConstant;

/**
 * The PartialEscapeAnalysisPhase is expected to remove all allocations and return the correct
 * values.
 */
public class EscapeAnalysisTest extends EATestBase {

    @Test
    public void test1() {
        testEscapeAnalysis("test1Snippet", JavaConstant.forInt(101), false);
    }

    @SuppressWarnings("deprecation")
    public static int test1Snippet() {
        Integer x = new Integer(101);
        return x.intValue();
    }

    @Test
    public void test2() {
        testEscapeAnalysis("test2Snippet", JavaConstant.forInt(0), false);
    }

    public static int test2Snippet() {
        Integer[] x = new Integer[0];
        return x.length;
    }

    @Test
    public void test3() {
        testEscapeAnalysis("test3Snippet", JavaConstant.NULL_POINTER, false);
    }

    public static Object test3Snippet() {
        Integer[] x = new Integer[1];
        return x[0];
    }

    @Test
    public void testMonitor() {
        testEscapeAnalysis("testMonitorSnippet", JavaConstant.forInt(0), false);
    }

    @SuppressWarnings("deprecation")
    public static int testMonitorSnippet() {
        Integer x = new Integer(0);
        Double y = new Double(0);
        Object z = new Object();
        synchronized (x) {
            synchronized (y) {
                synchronized (z) {
                    notInlineable();
                }
            }
        }
        return x.intValue();
    }

    @Test
    public void testMonitor2() {
        testEscapeAnalysis("testMonitor2Snippet", JavaConstant.forInt(0), false);
    }

    /**
     * This test case differs from the last one in that it requires inlining within a synchronized
     * region.
     */
    @SuppressWarnings("deprecation")
    public static int testMonitor2Snippet() {
        Integer x = new Integer(0);
        Double y = new Double(0);
        Object z = new Object();
        synchronized (x) {
            synchronized (y) {
                synchronized (z) {
                    notInlineable();
                    return x.intValue();
                }
            }
        }
    }

    @Test
    public void testMerge() {
        testEscapeAnalysis("testMerge1Snippet", JavaConstant.forInt(0), true);
    }

    public static int testMerge1Snippet(int a) {
        TestClassInt obj = new TestClassInt(1, 0);
        if (a < 0) {
            obj.x = obj.x + 1;
        } else {
            obj.x = obj.x + 2;
            obj.y = 0;
        }
        if (obj.x > 1000) {
            return 1;
        }
        return obj.y;
    }

    @Test
    public void testSimpleLoop() {
        testEscapeAnalysis("testSimpleLoopSnippet", JavaConstant.forInt(1), false);
    }

    public int testSimpleLoopSnippet(int a) {
        TestClassInt obj = new TestClassInt(1, 2);
        for (int i = 0; i < a; i++) {
            notInlineable();
        }
        return obj.x;
    }

    @Test
    public void testModifyingLoop() {
        testEscapeAnalysis("testModifyingLoopSnippet", JavaConstant.forInt(1), false);
    }

    public int testModifyingLoopSnippet(int a) {
        TestClassInt obj = new TestClassInt(1, 2);
        for (int i = 0; i < a; i++) {
            obj.x = 3;
            notInlineable();
        }
        return obj.x <= 3 ? 1 : 0;
    }

    @Test
    public void testMergeAllocationsInt() {
        testEscapeAnalysis("testMergeAllocationsIntSnippet", JavaConstant.forInt(1), false);
    }

    public int testMergeAllocationsIntSnippet(int a) {
        TestClassInt obj;
        if (a < 0) {
            obj = new TestClassInt(1, 2);
            notInlineable();
        } else {
            obj = new TestClassInt(1, 2);
            notInlineable();
        }
        return obj.x <= 3 ? 1 : 0;
    }

    @Test
    public void testMergeAllocationsInt2() {
        testEscapeAnalysis("testMergeAllocationsInt2Snippet", JavaConstant.forInt(1), true);
    }

    public int testMergeAllocationsInt2Snippet(int a) {
        /*
         * The initial object in obj exists until the end of the function, but it can still be
         * merged with the one allocated in the else block because noone can observe the identity.
         */
        TestClassInt obj = new TestClassInt(1, 2);
        if (a < 0) {
            notInlineable();
        } else {
            obj = new TestClassInt(1, 2);
            notInlineable();
        }
        return obj.x <= 3 ? 1 : 0;
    }

    @Test
    public void testMergeAllocationsInt3() {
        // ensure that the result is not constant:
        assertTrue(testMergeAllocationsInt3Snippet(true));
        assertFalse(testMergeAllocationsInt3Snippet(false));

        prepareGraph("testMergeAllocationsInt3Snippet", true);
        assertFalse(graph.getNodes().filter(ReturnNode.class).first().result().isConstant());
    }

    public boolean testMergeAllocationsInt3Snippet(boolean a) {
        TestClassInt phi1;
        TestClassInt phi2;
        if (a) {
            field = new TestClassObject();
            field = new TestClassObject();
            phi1 = phi2 = new TestClassInt(1, 2);
        } else {
            phi1 = new TestClassInt(2, 3);
            phi2 = new TestClassInt(3, 4);
        }
        return phi1 == phi2;
    }

    @Test
    public void testMergeAllocationsObj() {
        testEscapeAnalysis("testMergeAllocationsObjSnippet", JavaConstant.forInt(1), false);
    }

    public int testMergeAllocationsObjSnippet(int a) {
        TestClassObject obj;
        Integer one = 1;
        Integer two = 2;
        Integer three = 3;
        if (a < 0) {
            obj = new TestClassObject(one, two);
            notInlineable();
        } else {
            obj = new TestClassObject(one, three);
            notInlineable();
        }
        return ((Integer) obj.x).intValue() <= 3 ? 1 : 0;
    }

    @Test
    public void testMergeAllocationsObjCirc() {
        testEscapeAnalysis("testMergeAllocationsObjCircSnippet", JavaConstant.forInt(1), false);
    }

    public int testMergeAllocationsObjCircSnippet(int a) {
        TestClassObject obj;
        Integer one = 1;
        Integer two = 2;
        Integer three = 3;
        if (a < 0) {
            obj = new TestClassObject(one);
            obj.y = obj;
            obj.y = two;
            notInlineable();
        } else {
            obj = new TestClassObject(one);
            obj.y = obj;
            obj.y = three;
            notInlineable();
        }
        return ((Integer) obj.x).intValue() <= 3 ? 1 : 0;
    }

    static class MyException extends RuntimeException {

        private static final long serialVersionUID = 0L;

        protected Integer value;

        MyException(Integer value) {
            super((Throwable) null);
            this.value = value;
        }

        @SuppressWarnings("sync-override")
        @Override
        public final Throwable fillInStackTrace() {
            return this;
        }
    }

    @Test
    public void testMergeAllocationsException() {
        testEscapeAnalysis("testMergeAllocationsExceptionSnippet", JavaConstant.forInt(1), false);
    }

    public int testMergeAllocationsExceptionSnippet(int a) {
        MyException obj;
        Integer one = 1;
        if (a < 0) {
            obj = new MyException(one);
            notInlineable();
        } else {
            obj = new MyException(one);
            notInlineable();
        }
        return obj.value <= 3 ? 1 : 0;
    }

    /**
     * Tests that a graph with allocations that does not make progress during PEA will not be
     * changed.
     */
    @Test
    public void testChangeHandling() {
        prepareGraph("testChangeHandlingSnippet", false);
        Assert.assertEquals(2, graph.getNodes().filter(CommitAllocationNode.class).count());
        Assert.assertEquals(1, graph.getNodes().filter(BoxNode.class).count());
        List<Node> nodes = graph.getNodes().snapshot();
        // verify that an additional run doesn't add or remove nodes
        new PartialEscapePhase(false, false, new CanonicalizerPhase(), null, graph.getOptions()).apply(graph, context);
        Assert.assertEquals(nodes.size(), graph.getNodeCount());
        for (Node node : nodes) {
            Assert.assertTrue(node.isAlive());
        }
    }

    public volatile Object field;

    @SuppressWarnings("deprecation")
    public int testChangeHandlingSnippet(int a) {
        Object obj;
        Integer one = 1;
        obj = new MyException(one);
        if (a < 0) {
            notInlineable();
        } else {
            obj = new Integer(1);
            notInlineable();
        }
        field = obj;
        return 1;
    }

    /**
     * Test the case where allocations before and during a loop that have no usages other than their
     * phi need to be recognized as an important change. This needs a loop so that the allocation is
     * not trivially removed by dead code elimination.
     */
    @Test
    public void testRemovalSpecialCase() {
        prepareGraph("testRemovalSpecialCaseSnippet", false);
        Assert.assertEquals(2, graph.getNodes().filter(CommitAllocationNode.class).count());
        // create the situation by removing the if
        graph.replaceFixedWithFloating(graph.getNodes().filter(LoadFieldNode.class).first(), graph.unique(ConstantNode.forInt(0)));
        new CanonicalizerPhase().apply(graph, context);
        // verify that an additional run removes all allocations
        new PartialEscapePhase(false, false, new CanonicalizerPhase(), null, graph.getOptions()).apply(graph, context);
        Assert.assertEquals(0, graph.getNodes().filter(CommitAllocationNode.class).count());
    }

    public volatile int field2;

    public int testRemovalSpecialCaseSnippet(int a) {
        Object phi = new Object();
        for (int i = 0; i < a; i++) {
            field = null;
            if (field2 == 1) {
                phi = new Object();
            }
        }
        return phi == null ? 1 : 0;
    }

    @Test
    public void testCheckCast() {
        testEscapeAnalysis("testCheckCastSnippet", getSnippetReflection().forObject(TestClassObject.class), true);
    }

    public Object testCheckCastSnippet() {
        TestClassObject obj = new TestClassObject(TestClassObject.class);
        TestClassObject obj2 = new TestClassObject(obj);
        return ((TestClassObject) obj2.x).x;
    }

    @Test
    public void testInstanceOf() {
        testEscapeAnalysis("testInstanceOfSnippet", JavaConstant.forInt(1), false);
    }

    public boolean testInstanceOfSnippet() {
        TestClassObject obj = new TestClassObject(TestClassObject.class);
        TestClassObject obj2 = new TestClassObject(obj);
        return obj2.x instanceof TestClassObject;
    }

    @SuppressWarnings("unused")
    public static void testNewNodeSnippet() {
        new ValueAnchorNode(null);
    }

    /**
     * This test makes sure that the allocation of a {@link Node} can be removed. It therefore also
     * tests the intrinsification of {@link Object#getClass()}.
     */
    @Test
    public void testNewNode() {
        // Trackking of creation interferes with escape analysis
        Assume.assumeFalse(Node.TRACK_CREATION_POSITION);
        testEscapeAnalysis("testNewNodeSnippet", null, false);
    }

    private static final TestClassObject staticObj = new TestClassObject();

    public static Object testFullyUnrolledLoopSnippet() {
        /*
         * This tests a case that can appear if PEA is performed both before and after loop
         * unrolling/peeling: If the VirtualInstanceNode is not duplicated correctly with the loop,
         * the resulting object will reference itself, and not a second (different) object.
         */
        TestClassObject obj = staticObj;
        for (int i = 0; i < 2; i++) {
            obj = new TestClassObject(obj);
        }
        return obj.x;
    }

    @Test
    public void testFullyUnrolledLoop() {
        prepareGraph("testFullyUnrolledLoopSnippet", false);
        new LoopFullUnrollPhase(new CanonicalizerPhase(), new DefaultLoopPolicies()).apply(graph, context);
        new PartialEscapePhase(false, new CanonicalizerPhase(), graph.getOptions()).apply(graph, context);
        Assert.assertEquals(1, returnNodes.size());
        Assert.assertTrue(returnNodes.get(0).result() instanceof AllocatedObjectNode);
        CommitAllocationNode commit = ((AllocatedObjectNode) returnNodes.get(0).result()).getCommit();
        Assert.assertEquals(2, commit.getValues().size());
        Assert.assertEquals(1, commit.getVirtualObjects().size());
        Assert.assertTrue("non-cyclic data structure expected", commit.getVirtualObjects().get(0) != commit.getValues().get(0));
    }

    @SuppressWarnings("unused") private static Object staticField;

    private static TestClassObject inlinedPart(TestClassObject obj) {
        TestClassObject ret = new TestClassObject(obj);
        staticField = null;
        return ret;
    }

    public static Object testPeeledLoopSnippet() {
        TestClassObject obj = staticObj;
        int i = 0;
        do {
            obj = inlinedPart(obj);
        } while (i++ < 10);
        staticField = obj;
        return obj.x;
    }

    @Test
    public void testPeeledLoop() {
        prepareGraph("testPeeledLoopSnippet", false);
        new LoopPeelingPhase(new DefaultLoopPolicies()).apply(graph, getDefaultHighTierContext());
        new SchedulePhase(graph.getOptions()).apply(graph);
    }

    public static void testDeoptMonitorSnippetInner(Object o2, Object t, int i) {
        staticField = null;
        if (i == 0) {
            staticField = o2;
            Number n = (Number) t;
            n.toString();
        }
    }

    public static void testDeoptMonitorSnippet(Object t, int i) {
        TestClassObject o = new TestClassObject();
        TestClassObject o2 = new TestClassObject(o);

        synchronized (o) {
            testDeoptMonitorSnippetInner(o2, t, i);
        }
    }

    @Test
    public void testDeoptMonitor() {
        test("testDeoptMonitorSnippet", new Object(), 0);
    }

    @Test
    public void testInterfaceArrayAssignment() {
        prepareGraph("testInterfaceArrayAssignmentSnippet", false);
        NodeIterable<ReturnNode> returns = graph.getNodes().filter(ReturnNode.class);
        assertTrue(returns.count() == 1);
        assertFalse(returns.first().result().isConstant());
    }

    private interface TestInterface {
    }

    public static boolean testInterfaceArrayAssignmentSnippet() {
        Object[] array = new TestInterface[1];
        array[0] = new Object();
        return array[0] == null;
    }

    static final class Complex {
        private final double real;
        private final double imag;

        Complex(double real, double imag) {
            this.real = real;
            this.imag = imag;
        }

        public Complex mul(Complex other) {
            return new Complex(real * other.real - imag * other.imag, imag * other.real + real * other.imag);
        }

        public Complex add(Complex other) {
            return new Complex(real + other.real, imag + other.imag);
        }

        // equals is needed for result comparison

        @Override
        public boolean equals(Object obj) {
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            Complex other = (Complex) obj;
            return this == other || Double.doubleToLongBits(imag) == Double.doubleToLongBits(other.imag) && Double.doubleToLongBits(real) == Double.doubleToLongBits(other.real);
        }

        @Override
        public int hashCode() {
            return Double.hashCode(real) ^ Double.hashCode(imag);
        }
    }

    private static final Complex[][] inputValue = new Complex[100][100];
    static {
        for (int i = 0; i < 100; i++) {
            for (int j = 0; j < 100; j++) {
                inputValue[i][j] = new Complex(i, j);
            }
        }
    }

    public static Complex[][] testComplexMultiplySnippet1(Complex[][] input) {
        int size = input.length;
        Complex[][] result = new Complex[size][size];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                Complex s = new Complex(0, 0);
                for (int k = 0; k < size; k++) {
                    s = s.add(input[i][k].mul(input[k][j]));
                }
                result[i][j] = s;
            }
        }
        return result;
    }

    @Test
    public void testComplexMultiply1() {
        test("testComplexMultiplySnippet1", (Object) inputValue);

        // EA test: only one allocation remains (not counting the NewMultiArray), using iterative EA
        testEscapeAnalysis("testComplexMultiplySnippet1", null, true, 1);
    }

    public static Complex[][] testComplexMultiplySnippet2(Complex[][] input) {
        int size = input.length;
        Complex[][] result = new Complex[size][size];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                Complex s = input[i][0].mul(input[0][j]);
                for (int k = 1; k < size; k++) {
                    s = s.add(input[i][k].mul(input[k][j]));
                }
                result[i][j] = s;
            }
        }
        return result;
    }

    @Test
    public void testComplexMultiply2() {
        test("testComplexMultiplySnippet2", (Object) inputValue);

        // EA test: only one allocation remains (not counting the NewMultiArray), using iterative EA
        testEscapeAnalysis("testComplexMultiplySnippet2", null, true, 1);
    }

    public static Complex testComplexAddSnippet(Complex[][] input) {
        int size = input.length;
        Complex s = new Complex(0, 0);
        for (int i = 0; i < size; i++) {
            Complex s2 = new Complex(0, 0);
            for (int j = 0; j < size; j++) {
                s2 = s2.add(input[i][j]);
            }
            s.add(s2);
        }
        return s;
    }

    @Test
    public void testComplexAdd() {
        test("testComplexAddSnippet", (Object) inputValue);

        // EA test: only one allocation remains (not counting the NewMultiArray), using iterative EA
        testEscapeAnalysis("testComplexAddSnippet", null, true, 1);
    }

    public static Complex[] testComplexRowSumSnippet(Complex[][] input) {
        int size = input.length;
        Complex[] result = new Complex[size];
        for (int i = 0; i < size; i++) {
            Complex s = new Complex(0, 0);
            for (int j = 0; j < size; j++) {
                s = s.add(input[i][j]);
            }
            result[i] = s;
        }
        return result;
    }

    @Test
    public void testComplexRowSum() {
        test("testComplexRowSumSnippet", (Object) inputValue);

        // EA test: only two allocations (new array and new instance) remain
        testEscapeAnalysis("testComplexRowSumSnippet", null, true, 2);
    }
}
