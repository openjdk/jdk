/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.Test;

import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.DeadCodeEliminationPhase;
import org.graalvm.compiler.phases.tiers.PhaseContext;

/**
 * This class tests some specific patterns the stamp system should be able to canonicalize away
 * using {@link IntegerStamp#upMask()}.
 */
public class StampCanonicalizerTest extends GraalCompilerTest {

    public static int andStamp(int a, int b) {
        int v = (a & 0xffff00) & (b & 0xff0000f);
        return v & 1;
    }

    @Test
    public void testAnd() {
        testZeroReturn("andStamp");
    }

    public static int shiftLeftStamp1(int a) {
        int v = a << 1;
        return v & 1;
    }

    public static int shiftLeftStamp2(int a) {
        int v = a << 1;
        if (a == 17) {
            v = a * 4;
        }
        return v & 1;
    }

    @Test
    public void testShift() {
        testZeroReturn("shiftLeftStamp1");
        testZeroReturn("shiftLeftStamp2");
    }

    public static int upperBoundShiftStamp1(int a) {
        int v = a & 0xffff;
        return (v << 4) & 0xff00000;
    }

    public static int upperBoundShiftStamp2(int a) {
        int v = a & 0xffff;
        return (v >> 4) & 0xf000;
    }

    @Test
    public void testUpperBoundShift() {
        testZeroReturn("upperBoundShiftStamp1");
        testZeroReturn("upperBoundShiftStamp2");
    }

    public static int divStamp1(int[] a) {
        int v = a.length / 4;
        return v & 0x80000000;
    }

    public static int divStamp2(int[] a) {
        int v = a.length / 5;
        return v & 0x80000000;
    }

    @Test
    public void testDiv() {
        testZeroReturn("divStamp1");
        testZeroReturn("divStamp2");
    }

    public static int distinctMask(int a, int b) {
        int x = a & 0xaaaa;
        int y = (b & 0x5555) | 0x1;
        return x == y ? 1 : 0;
    }

    @Test
    public void testDistinctMask() {
        testZeroReturn("distinctMask");
    }

    private void testZeroReturn(String methodName) {
        StructuredGraph graph = parseEager(methodName, AllowAssumptions.YES);
        new CanonicalizerPhase().apply(graph, new PhaseContext(getProviders()));
        new DeadCodeEliminationPhase().apply(graph);
        assertConstantReturn(graph, 0);
    }
}
