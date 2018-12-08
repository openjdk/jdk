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

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.extended.RawLoadNode;
import org.graalvm.compiler.nodes.java.LoadIndexedNode;
import org.graalvm.compiler.nodes.java.StoreIndexedNode;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.virtual.phases.ea.PartialEscapePhase;
import org.junit.Test;

import sun.misc.Unsafe;

public class PEAReadEliminationTest extends GraalCompilerTest {

    public static int testIndexed1Snippet(int[] array) {
        array[1] = 1;
        array[2] = 2;
        array[3] = 3;
        array[4] = 4;
        array[5] = 5;
        array[6] = 6;
        return array[1] + array[2] + array[3] + array[4] + array[5] + array[6];
    }

    @Test
    public void testIndexed1() {
        StructuredGraph graph = processMethod("testIndexed1Snippet");
        assertDeepEquals(0, graph.getNodes().filter(LoadIndexedNode.class).count());
    }

    public static int testIndexed2Snippet(int v, int[] array) {
        array[1] = 1;
        array[2] = 2;
        array[3] = 3;
        array[v] = 0;
        array[4] = 4;
        array[5] = 5;
        array[6] = 6;
        array[4] = 4;
        array[5] = 5;
        array[6] = 6;
        return array[1] + array[2] + array[3] + array[4] + array[5] + array[6];
    }

    @Test
    public void testIndexed2() {
        StructuredGraph graph = processMethod("testIndexed2Snippet");
        assertDeepEquals(3, graph.getNodes().filter(LoadIndexedNode.class).count());
        assertDeepEquals(7, graph.getNodes().filter(StoreIndexedNode.class).count());
    }

    public static int testIndexed3Snippet(int v, int[] array, short[] array2) {
        array[1] = 1;
        array2[1] = 1;
        array[2] = 2;
        array2[2] = 2;
        array[3] = 3;
        array2[3] = 3;
        array[v] = 0;
        array[4] = 4;
        array2[4] = 4;
        array[5] = 5;
        array2[5] = 5;
        array[6] = 6;
        array2[6] = 6;
        return array[1] + array[2] + array[3] + array[4] + array[5] + array[6] + array2[1] + array2[2] + array2[3] + array2[4] + array2[5] + array2[6];
    }

    @Test
    public void testIndexed3() {
        StructuredGraph graph = processMethod("testIndexed3Snippet");
        assertDeepEquals(3, graph.getNodes().filter(LoadIndexedNode.class).count());
    }

    private static native void nonInlineable();

    public static int testIndexed4Snippet(int[] array) {
        array[1] = 1;
        array[2] = 2;
        array[3] = 3;
        nonInlineable();
        array[4] = 4;
        array[5] = 5;
        array[6] = 6;
        return array[1] + array[2] + array[3] + array[4] + array[5] + array[6];
    }

    @Test
    public void testIndexed4() {
        StructuredGraph graph = processMethod("testIndexed4Snippet");
        assertDeepEquals(3, graph.getNodes().filter(LoadIndexedNode.class).count());
    }

    private static final long offsetInt1 = Unsafe.ARRAY_INT_BASE_OFFSET + Unsafe.ARRAY_INT_INDEX_SCALE * 1;
    private static final long offsetInt2 = Unsafe.ARRAY_INT_BASE_OFFSET + Unsafe.ARRAY_INT_INDEX_SCALE * 2;

    public static int testUnsafe1Snippet(int v, int[] array) {
        int s = UNSAFE.getInt(array, offsetInt1);
        UNSAFE.putInt(array, offsetInt1, v);
        UNSAFE.putInt(array, offsetInt2, v);
        return s + UNSAFE.getInt(array, offsetInt1) + UNSAFE.getInt(array, offsetInt2);
    }

    @Test
    public void testUnsafe1() {
        StructuredGraph graph = processMethod("testUnsafe1Snippet");
        assertDeepEquals(1, graph.getNodes().filter(RawLoadNode.class).count());
    }

    public static int testUnsafe2Snippet(int v, Object array) {
        int s = UNSAFE.getInt(array, offsetInt1);
        UNSAFE.putInt(array, offsetInt1, v);
        UNSAFE.putInt(array, offsetInt2, v);
        return s + UNSAFE.getInt(array, offsetInt1) + UNSAFE.getInt(array, offsetInt2);
    }

    @Test
    public void testUnsafe2() {
        StructuredGraph graph = processMethod("testUnsafe2Snippet");
        assertDeepEquals(3, graph.getNodes().filter(RawLoadNode.class).count());
    }

    private static final long offsetObject1 = Unsafe.ARRAY_OBJECT_BASE_OFFSET + Unsafe.ARRAY_OBJECT_INDEX_SCALE * 1;
    private static final long offsetObject2 = Unsafe.ARRAY_OBJECT_BASE_OFFSET + Unsafe.ARRAY_OBJECT_INDEX_SCALE * 2;

    public static int testUnsafe3Snippet(int v, Object[] array) {
        int s = (Integer) UNSAFE.getObject(array, offsetObject1);
        UNSAFE.putObject(array, offsetObject1, v);
        UNSAFE.putObject(array, offsetObject2, v);
        return s + (Integer) UNSAFE.getObject(array, offsetObject1) + (Integer) UNSAFE.getObject(array, offsetObject2);
    }

    @Test
    public void testUnsafe3() {
        StructuredGraph graph = processMethod("testUnsafe3Snippet");
        assertDeepEquals(1, graph.getNodes().filter(RawLoadNode.class).count());
    }

    public static int testUnsafe4Snippet(int v, Object[] array) {
        int s = (Integer) UNSAFE.getObject(array, offsetObject1);
        UNSAFE.putObject(array, offsetObject1, v);
        UNSAFE.putObject(array, offsetObject2, v);
        array[v] = null;
        return s + (Integer) UNSAFE.getObject(array, offsetObject1) + (Integer) UNSAFE.getObject(array, offsetObject2);
    }

    @Test
    public void testUnsafe4() {
        StructuredGraph graph = processMethod("testUnsafe4Snippet");
        assertDeepEquals(3, graph.getNodes().filter(RawLoadNode.class).count());
    }

    protected StructuredGraph processMethod(final String snippet) {
        StructuredGraph graph = parseEager(snippet, AllowAssumptions.NO);
        HighTierContext context = getDefaultHighTierContext();
        createInliningPhase().apply(graph, context);
        new PartialEscapePhase(false, true, new CanonicalizerPhase(), null, graph.getOptions()).apply(graph, context);
        return graph;
    }
}
