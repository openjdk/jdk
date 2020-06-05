/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Arm Limited and affiliates. All rights reserved.
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

import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.calc.NegateNode;
import org.graalvm.compiler.nodes.calc.RightShiftNode;
import org.graalvm.compiler.nodes.calc.UnsignedRightShiftNode;
import org.junit.Test;

public class NegateCanonicalizationTest extends GraalCompilerTest {

    public static int negateInt(int x) {
        return -(x >> 31);
    }

    public static long negateLong(long x) {
        return -(x >> 63);
    }

    public static int signExtractInt(int x) {
        return (x >> 31) >>> 31;
    }

    public static long signExtractLong(long x) {
        return (x >> 63) >>> 63;
    }

    private void checkNodes(String methodName) {
        StructuredGraph graph = parseForCompile(getResolvedJavaMethod(methodName));
        createCanonicalizerPhase().apply(graph, getProviders());
        assertTrue(graph.getNodes().filter(NegateNode.class).count() == 0);
        assertTrue(graph.getNodes().filter(RightShiftNode.class).count() == 0);
        assertTrue(graph.getNodes().filter(UnsignedRightShiftNode.class).count() == 1);
    }

    @Test
    public void testNegate() {
        checkNodes("negateInt");
        checkNodes("negateLong");
        checkNodes("signExtractInt");
        checkNodes("signExtractLong");
    }
}
