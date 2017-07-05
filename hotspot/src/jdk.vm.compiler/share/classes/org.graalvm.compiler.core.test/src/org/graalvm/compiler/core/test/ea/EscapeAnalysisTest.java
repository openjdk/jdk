/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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

import jdk.vm.ci.meta.JavaConstant;

import org.junit.Assert;
import org.junit.Test;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.loop.DefaultLoopPolicies;
import org.graalvm.compiler.loop.phases.LoopFullUnrollPhase;
import org.graalvm.compiler.loop.phases.LoopPeelingPhase;
import org.graalvm.compiler.nodes.extended.ValueAnchorNode;
import org.graalvm.compiler.nodes.virtual.AllocatedObjectNode;
import org.graalvm.compiler.nodes.virtual.CommitAllocationNode;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.schedule.SchedulePhase;
import org.graalvm.compiler.virtual.phases.ea.PartialEscapePhase;

/**
 * The PartialEscapeAnalysisPhase is expected to remove all allocations and return the correct
 * values.
 */
public class EscapeAnalysisTest extends EATestBase {

    @Test
    public void test1() {
        testEscapeAnalysis("test1Snippet", JavaConstant.forInt(101), false);
    }

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
            return null;
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

    @Test
    public void testCheckCast() {
        testEscapeAnalysis("testCheckCastSnippet", getSnippetReflection().forObject(TestClassObject.class), false);
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
        new PartialEscapePhase(false, new CanonicalizerPhase()).apply(graph, context);
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
        new SchedulePhase().apply(graph);
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
}
