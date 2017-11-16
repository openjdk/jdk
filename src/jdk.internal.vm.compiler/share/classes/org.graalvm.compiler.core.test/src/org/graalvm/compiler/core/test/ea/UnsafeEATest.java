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

import java.nio.ByteBuffer;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.calc.UnpackEndianHalfNode;
import org.graalvm.compiler.nodes.extended.RawLoadNode;
import org.graalvm.compiler.nodes.extended.RawStoreNode;
import org.graalvm.compiler.nodes.extended.UnsafeAccessNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.junit.Assert;
import org.junit.Test;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class UnsafeEATest extends EATestBase {

    public static int zero = 0;

    private static final long fieldOffset1;
    private static final long fieldOffset2;

    static {
        try {
            long localFieldOffset1 = UNSAFE.objectFieldOffset(TestClassInt.class.getField("x"));
            // Make the fields 8 byte aligned (Required for testing setLong on Architectures which
            // does not support unaligned memory access
            if (localFieldOffset1 % 8 == 0) {
                fieldOffset1 = localFieldOffset1;
                fieldOffset2 = UNSAFE.objectFieldOffset(TestClassInt.class.getField("y"));
            } else {
                fieldOffset1 = UNSAFE.objectFieldOffset(TestClassInt.class.getField("y"));
                fieldOffset2 = UNSAFE.objectFieldOffset(TestClassInt.class.getField("z"));
            }
            assert fieldOffset2 == fieldOffset1 + 4;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void testEscapeAnalysis(String snippet, JavaConstant expectedConstantResult, boolean iterativeEscapeAnalysis) {
        // Exercise both a graph containing UnsafeAccessNodes and one which has been possibly been
        // canonicalized into AccessFieldNodes.
        testingUnsafe = true;
        super.testEscapeAnalysis(snippet, expectedConstantResult, iterativeEscapeAnalysis);
        testingUnsafe = false;
        super.testEscapeAnalysis(snippet, expectedConstantResult, iterativeEscapeAnalysis);
        if (expectedConstantResult != null) {
            // Check that a compiled version of this method returns the same value if we expect a
            // constant result.
            ResolvedJavaMethod method = getResolvedJavaMethod(snippet);
            JavaKind[] javaKinds = method.getSignature().toParameterKinds(false);
            Object[] args = new Object[javaKinds.length];
            int i = 0;
            for (JavaKind k : javaKinds) {
                args[i++] = JavaConstant.defaultForKind(k).asBoxedPrimitive();
            }
            Result result = executeExpected(method, null, args);
            assertTrue(result.returnValue.equals(expectedConstantResult.asBoxedPrimitive()));
        }
    }

    @Override
    protected void canonicalizeGraph() {
        if (testingUnsafe) {
            // For testing purposes we'd like to ensure that our raw unsafe operations stay as
            // unsafe nodes, so force them to appear to have LocationIdentity.any to disable
            // transformation into field access nodes.
            for (Node node : graph.getNodes().filter(x -> x instanceof UnsafeAccessNode).snapshot()) {
                if (node instanceof RawStoreNode) {
                    RawStoreNode store = (RawStoreNode) node;
                    RawStoreNode newStore = graph.add(new RawStoreNode(store.object(), store.offset(), store.value(), store.accessKind(), NamedLocationIdentity.any(),
                                    store.needsBarrier(), store.stateAfter(), true));
                    graph.replaceFixedWithFixed(store, newStore);
                } else if (node instanceof RawLoadNode) {
                    RawLoadNode load = (RawLoadNode) node;
                    RawLoadNode newLoad = graph.add(new RawLoadNode(load.object(), load.offset(), load.accessKind(), NamedLocationIdentity.any(),
                                    true));
                    graph.replaceFixedWithFixed(load, newLoad);
                }
            }
        }
        super.canonicalizeGraph();
    }

    @Override
    protected void postEACanonicalizeGraph() {
        // Simplify any UnpackEndianHalfNode so we end up with constants.
        Graph.Mark mark = graph.getMark();
        for (UnpackEndianHalfNode node : graph.getNodes().filter(UnpackEndianHalfNode.class)) {
            node.lower(getTarget().arch.getByteOrder());
        }
        new CanonicalizerPhase().applyIncremental(graph, context, mark);
    }

    private boolean testingUnsafe;

    @Test
    public void testSimpleInt() {
        testEscapeAnalysis("testSimpleIntSnippet", JavaConstant.forInt(101), false);
    }

    public static int testSimpleIntSnippet() {
        TestClassInt x = new TestClassInt();
        UNSAFE.putInt(x, fieldOffset1, 101);
        return UNSAFE.getInt(x, fieldOffset1);
    }

    @Test
    public void testMaterializedInt() {
        test("testMaterializedIntSnippet");
    }

    public static TestClassInt testMaterializedIntSnippet() {
        TestClassInt x = new TestClassInt();
        UNSAFE.putInt(x, fieldOffset1, 101);
        return x;
    }

    @Test
    public void testSimpleDouble() {
        testEscapeAnalysis("testSimpleDoubleSnippet", JavaConstant.forDouble(10.1), false);
    }

    public static double testSimpleDoubleSnippet() {
        TestClassInt x = new TestClassInt();
        UNSAFE.putDouble(x, fieldOffset1, 10.1);
        return UNSAFE.getDouble(x, fieldOffset1);
    }

    @Test
    public void testSimpleDoubleOverwriteWithInt() {
        testEscapeAnalysis("testSimpleDoubleOverwriteWithIntSnippet", JavaConstant.forInt(10), false);
    }

    public static int testSimpleDoubleOverwriteWithIntSnippet() {
        TestClassInt x = new TestClassInt();
        UNSAFE.putDouble(x, fieldOffset1, 10.1);
        UNSAFE.putInt(x, fieldOffset1, 10);
        return UNSAFE.getInt(x, fieldOffset1);
    }

    @Test
    public void testSimpleDoubleOverwriteWithSecondInt() {
        ByteBuffer bb = ByteBuffer.allocate(8).order(getTarget().arch.getByteOrder());
        bb.putDouble(10.1);
        int value = bb.getInt(4);

        testEscapeAnalysis("testSimpleDoubleOverwriteWithSecondIntSnippet", JavaConstant.forInt(value), false);
    }

    public static int testSimpleDoubleOverwriteWithSecondIntSnippet() {
        TestClassInt x = new TestClassInt();
        UNSAFE.putDouble(x, fieldOffset1, 10.1);
        UNSAFE.putInt(x, fieldOffset1, 10);
        return UNSAFE.getInt(x, fieldOffset2);
    }

    @Test
    public void testSimpleDoubleOverwriteWithFirstInt() {
        ByteBuffer bb = ByteBuffer.allocate(8).order(getTarget().arch.getByteOrder());
        bb.putDouble(10.1);
        int value = bb.getInt(0);

        testEscapeAnalysis("testSimpleDoubleOverwriteWithFirstIntSnippet", JavaConstant.forInt(value), false);
    }

    public static int testSimpleDoubleOverwriteWithFirstIntSnippet() {
        TestClassInt x = new TestClassInt();
        UNSAFE.putDouble(x, fieldOffset1, 10.1);
        UNSAFE.putInt(x, fieldOffset2, 10);
        return UNSAFE.getInt(x, fieldOffset1);
    }

    @Test
    public void testSimpleLongOverwriteWithSecondInt() {
        ByteBuffer bb = ByteBuffer.allocate(8).order(getTarget().arch.getByteOrder());
        bb.putLong(0, 0x1122334455667788L);
        int value = bb.getInt(4);

        testEscapeAnalysis("testSimpleLongOverwriteWithSecondIntSnippet", JavaConstant.forInt(value), false);
    }

    public static int testSimpleLongOverwriteWithSecondIntSnippet() {
        TestClassInt x = new TestClassInt();
        UNSAFE.putLong(x, fieldOffset1, 0x1122334455667788L);
        UNSAFE.putInt(x, fieldOffset1, 10);
        return UNSAFE.getInt(x, fieldOffset2);
    }

    @Test
    public void testSimpleLongOverwriteWithFirstInt() {
        ByteBuffer bb = ByteBuffer.allocate(8).order(getTarget().arch.getByteOrder());
        bb.putLong(0, 0x1122334455667788L);
        int value = bb.getInt(0);

        testEscapeAnalysis("testSimpleLongOverwriteWithFirstIntSnippet", JavaConstant.forInt(value), false);
    }

    public static int testSimpleLongOverwriteWithFirstIntSnippet() {
        TestClassInt x = new TestClassInt();
        UNSAFE.putLong(x, fieldOffset1, 0x1122334455667788L);
        UNSAFE.putInt(x, fieldOffset2, 10);
        return UNSAFE.getInt(x, fieldOffset1);
    }

    @Test
    public void testMergedDouble() {
        testEscapeAnalysis("testMergedDoubleSnippet", null, false);
        Assert.assertEquals(1, returnNodes.size());
        Assert.assertTrue(returnNodes.get(0).result() instanceof ValuePhiNode);
        PhiNode phi = (PhiNode) returnNodes.get(0).result();
        Assert.assertTrue(phi.valueAt(0) instanceof LoadFieldNode);
        Assert.assertTrue(phi.valueAt(1) instanceof LoadFieldNode);
    }

    public static double testMergedDoubleSnippet(boolean a) {
        TestClassInt x;
        if (a) {
            x = new TestClassInt(0, 0);
            UNSAFE.putDouble(x, fieldOffset1, doubleField);
        } else {
            x = new TestClassInt();
            UNSAFE.putDouble(x, fieldOffset1, doubleField2);
        }
        return UNSAFE.getDouble(x, fieldOffset1);
    }

    static class ExtendedTestClassInt extends TestClassInt {
        public long l;
    }

    @Test
    public void testMergedVirtualObjects() {
        testEscapeAnalysis("testMergedVirtualObjectsSnippet", null, false);
    }

    public static TestClassInt testMergedVirtualObjectsSnippet(int value) {
        TestClassInt x;
        if (value == 1) {
            x = new TestClassInt();
            UNSAFE.putDouble(x, fieldOffset1, 10);
        } else {
            x = new TestClassInt();
            UNSAFE.putInt(x, fieldOffset1, 0);
        }
        UNSAFE.putInt(x, fieldOffset1, 0);
        if (value == 2) {
            UNSAFE.putInt(x, fieldOffset2, 0);
        }
        GraalDirectives.deoptimizeAndInvalidate();
        return x;
    }

    @Test
    public void testMaterializedDouble() {
        test("testMaterializedDoubleSnippet");
    }

    public static TestClassInt testMaterializedDoubleSnippet() {
        TestClassInt x = new TestClassInt();
        UNSAFE.putDouble(x, fieldOffset1, 10.1);
        return x;
    }

    @Test
    public void testDeoptDoubleVar() {
        test("testDeoptDoubleVarSnippet");
    }

    public static double doubleField = 10.1e99;
    public static double doubleField2;

    public static TestClassInt testDeoptDoubleVarSnippet() {
        TestClassInt x = new TestClassInt();
        UNSAFE.putDouble(x, fieldOffset1, doubleField);
        doubleField2 = 123;
        try {
            doubleField = ((int) UNSAFE.getDouble(x, fieldOffset1)) / zero;
        } catch (RuntimeException e) {
            return x;
        }
        return x;
    }

    @Test
    public void testDeoptDoubleConstant() {
        test("testDeoptDoubleConstantSnippet");
    }

    public static TestClassInt testDeoptDoubleConstantSnippet() {
        TestClassInt x = new TestClassInt();
        UNSAFE.putDouble(x, fieldOffset1, 10.123);
        doubleField2 = 123;
        try {
            doubleField = ((int) UNSAFE.getDouble(x, fieldOffset1)) / zero;
        } catch (RuntimeException e) {
            return x;
        }
        return x;
    }

    @Test
    public void testDeoptLongVar() {
        test("testDeoptLongVarSnippet");
    }

    public static long longField = 0x133443218aaaffffL;
    public static long longField2;

    public static TestClassInt testDeoptLongVarSnippet() {
        TestClassInt x = new TestClassInt();
        UNSAFE.putLong(x, fieldOffset1, longField);
        longField2 = 123;
        try {
            longField = UNSAFE.getLong(x, fieldOffset1) / zero;
        } catch (RuntimeException e) {
            return x;
        }
        return x;
    }

    @Test
    public void testDeoptLongConstant() {
        test("testDeoptLongConstantSnippet");
    }

    public static TestClassInt testDeoptLongConstantSnippet() {
        TestClassInt x = new TestClassInt();
        UNSAFE.putLong(x, fieldOffset1, 0x2222222210123L);
        longField2 = 123;
        try {
            longField = UNSAFE.getLong(x, fieldOffset1) / zero;
        } catch (RuntimeException e) {
            return x;
        }
        return x;
    }

}
