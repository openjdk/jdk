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
package org.graalvm.compiler.core.test;

import java.lang.reflect.Field;

import org.junit.Assert;
import org.junit.Test;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.extended.UnsafeAccessNode;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.WriteNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.LoweringPhase;
import org.graalvm.compiler.phases.tiers.PhaseContext;
import org.graalvm.compiler.virtual.phases.ea.EarlyReadEliminationPhase;
import org.graalvm.compiler.virtual.phases.ea.PartialEscapePhase;

import sun.misc.Unsafe;

public class UnsafeReadEliminationTest extends GraalCompilerTest {

    public static final Unsafe UNSAFE;
    static {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            UNSAFE = (Unsafe) theUnsafe.get(Unsafe.class);
        } catch (Exception e) {
            throw new RuntimeException("Exception while trying to get Unsafe", e);
        }
    }

    public static long[] Memory = new long[]{1, 2};
    public static double SideEffectD;
    public static double SideEffectL;

    public static long test1Snippet(double a) {
        final Object m = Memory;
        if (a > 0) {
            UNSAFE.putDouble(m, (long) Unsafe.ARRAY_LONG_BASE_OFFSET, a);
        } else {
            SideEffectL = UNSAFE.getLong(m, (long) Unsafe.ARRAY_LONG_BASE_OFFSET);
        }
        return UNSAFE.getLong(m, (long) Unsafe.ARRAY_LONG_BASE_OFFSET);
    }

    public static class A {
        long[][] o;
        long[][] p;
    }

    public static Object test2Snippet(A a, int c) {
        Object phi = null;
        if (c != 0) {
            long[][] r = a.o;
            phi = r;
            UNSAFE.putDouble(r, (long) Unsafe.ARRAY_LONG_BASE_OFFSET, 12d);
        } else {
            long[][] r = a.p;
            phi = r;
            UNSAFE.putLong(r, (long) Unsafe.ARRAY_LONG_BASE_OFFSET, 123);
        }
        GraalDirectives.controlFlowAnchor();
        SideEffectD = UNSAFE.getDouble(phi, (long) Unsafe.ARRAY_LONG_BASE_OFFSET);
        return phi;
    }

    @Test
    public void test01() {
        StructuredGraph graph = parseEager("test1Snippet", AllowAssumptions.NO);
        testEarlyReadElimination(graph, 3, 2);
    }

    @Test
    public void test02() {
        StructuredGraph graph = parseEager("test1Snippet", AllowAssumptions.NO);
        testPartialEscapeReadElimination(graph, 3, 2);
    }

    @Test
    public void test03() {
        StructuredGraph graph = parseEager("test2Snippet", AllowAssumptions.NO);
        testEarlyReadElimination(graph, 3, 3);
    }

    @Test
    public void test04() {
        StructuredGraph graph = parseEager("test2Snippet", AllowAssumptions.NO);
        testEarlyReadElimination(graph, 3, 3);
    }

    public void testEarlyReadElimination(StructuredGraph graph, int reads, int writes) {
        PhaseContext context = getDefaultHighTierContext();
        CanonicalizerPhase canonicalizer = new CanonicalizerPhase();
        canonicalizer.apply(graph, context);
        new EarlyReadEliminationPhase(canonicalizer).apply(graph, context);
        Assert.assertEquals(3, graph.getNodes().filter(UnsafeAccessNode.class).count());
        // after lowering the same applies for reads and writes
        new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.HIGH_TIER).apply(graph, context);
        canonicalizer.apply(graph, context);
        new EarlyReadEliminationPhase(canonicalizer).apply(graph, context);
        Assert.assertEquals(reads, graph.getNodes().filter(ReadNode.class).count());
        Assert.assertEquals(writes, graph.getNodes().filter(WriteNode.class).count());
    }

    public void testPartialEscapeReadElimination(StructuredGraph graph, int reads, int writes) {
        PhaseContext context = getDefaultHighTierContext();
        CanonicalizerPhase canonicalizer = new CanonicalizerPhase();
        canonicalizer.apply(graph, context);
        new PartialEscapePhase(true, true, canonicalizer, null).apply(graph, context);
        Assert.assertEquals(3, graph.getNodes().filter(UnsafeAccessNode.class).count());
        // after lowering the same applies for reads and writes
        new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.HIGH_TIER).apply(graph, context);
        canonicalizer.apply(graph, context);
        new PartialEscapePhase(true, true, canonicalizer, null).apply(graph, context);
        Assert.assertEquals(reads, graph.getNodes().filter(ReadNode.class).count());
        Assert.assertEquals(writes, graph.getNodes().filter(WriteNode.class).count());
    }

}
