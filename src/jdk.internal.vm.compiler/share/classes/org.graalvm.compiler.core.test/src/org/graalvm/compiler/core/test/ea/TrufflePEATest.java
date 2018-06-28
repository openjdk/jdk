/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Field;

import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.extended.RawLoadNode;
import org.graalvm.compiler.nodes.extended.RawStoreNode;
import org.graalvm.compiler.nodes.virtual.CommitAllocationNode;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.inlining.InliningPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.virtual.phases.ea.PartialEscapePhase;
import org.junit.Test;

import sun.misc.Unsafe;

public class TrufflePEATest extends GraalCompilerTest {

    /**
     * This class mimics the behavior of {@code FrameWithoutBoxing}.
     */
    static class Frame {
        long[] primitiveLocals;

        Frame(int size) {
            primitiveLocals = new long[size];
        }
    }

    /**
     * This class mimics the behavior of {@code DynamicObjectL6I6}.
     */
    static class DynamicObject {
        int primitiveField0;
        int primitiveField1;
        int primitiveField2;
    }

    private static final long offsetLong1 = Unsafe.ARRAY_LONG_BASE_OFFSET + Unsafe.ARRAY_LONG_INDEX_SCALE * 1;
    private static final long offsetLong2 = Unsafe.ARRAY_LONG_BASE_OFFSET + Unsafe.ARRAY_LONG_INDEX_SCALE * 2;

    private static final long primitiveField0Offset;

    static {
        try {
            Field primitiveField0 = DynamicObject.class.getDeclaredField("primitiveField0");
            long offset = UNSAFE.objectFieldOffset(primitiveField0);
            if (offset % 8 == 0) {
                primitiveField0Offset = offset;
            } else {
                Field primitiveField1 = DynamicObject.class.getDeclaredField("primitiveField1");
                offset = UNSAFE.objectFieldOffset(primitiveField1);
                assert offset % 8 == 0;
                primitiveField0Offset = offset;
            }
        } catch (NoSuchFieldException | SecurityException e) {
            throw new AssertionError(e);
        }
    }

    public static int unsafeAccessToLongArray(int v, Frame frame) {
        long[] array = frame.primitiveLocals;
        int s = UNSAFE.getInt(array, offsetLong1);
        UNSAFE.putInt(array, offsetLong1, v);
        UNSAFE.putInt(array, offsetLong2, v);
        return s + UNSAFE.getInt(array, offsetLong1) + UNSAFE.getInt(array, offsetLong2);
    }

    @Test
    public void testUnsafeAccessToLongArray() {
        StructuredGraph graph = processMethod("unsafeAccessToLongArray");
        assertDeepEquals(1, graph.getNodes().filter(RawLoadNode.class).count());
    }

    /**
     * The following value should be less than the default value of
     * {@link GraalOptions#MaximumEscapeAnalysisArrayLength}.
     */
    private static final int FRAME_SIZE = 16;

    public static long newFrame(long v) {
        Frame frame = new Frame(FRAME_SIZE);
        // Testing unsafe accesses with other kinds requires special handling of the initialized
        // entry kind.
        UNSAFE.putLong(frame.primitiveLocals, offsetLong1, v);
        return UNSAFE.getLong(frame.primitiveLocals, offsetLong1);
    }

    @Test
    public void testNewFrame() {
        StructuredGraph graph = processMethod("newFrame");
        assertDeepEquals(0, graph.getNodes().filter(CommitAllocationNode.class).count());
        assertDeepEquals(0, graph.getNodes().filter(RawLoadNode.class).count());
        assertDeepEquals(0, graph.getNodes().filter(RawStoreNode.class).count());
    }

    protected StructuredGraph processMethod(final String snippet) {
        StructuredGraph graph = parseEager(snippet, StructuredGraph.AllowAssumptions.NO);
        HighTierContext context = getDefaultHighTierContext();
        new InliningPhase(new CanonicalizerPhase()).apply(graph, context);
        new PartialEscapePhase(true, true, new CanonicalizerPhase(), null, graph.getOptions()).apply(graph, context);
        return graph;
    }

    public static double accessDynamicObject(double v) {
        DynamicObject obj = new DynamicObject();
        UNSAFE.putDouble(obj, primitiveField0Offset, v);
        return UNSAFE.getDouble(obj, primitiveField0Offset);
    }

    @Test
    public void testAccessDynamicObject() {
        StructuredGraph graph = processMethod("accessDynamicObject");
        assertDeepEquals(0, graph.getNodes().filter(CommitAllocationNode.class).count());
        assertDeepEquals(0, graph.getNodes().filter(RawLoadNode.class).count());
        assertDeepEquals(0, graph.getNodes().filter(RawStoreNode.class).count());
    }

}
