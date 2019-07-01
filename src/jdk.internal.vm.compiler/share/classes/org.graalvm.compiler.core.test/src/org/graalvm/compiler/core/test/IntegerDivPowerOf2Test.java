/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.calc.RightShiftNode;
import org.graalvm.compiler.nodes.calc.UnsignedRightShiftNode;
import org.junit.Test;

public class IntegerDivPowerOf2Test extends GraalCompilerTest {

    public static int positiveDivByPowerOf2(boolean flag) {
        int val = flag ? 1 : 10;
        GraalDirectives.blackhole(val);
        return val / 8;
    }

    @Test
    public void testPositiveDivByPowerOf2() {
        StructuredGraph graph = parseForCompile(getResolvedJavaMethod("positiveDivByPowerOf2"));
        // We expect no rounding is needed
        assertTrue(countShiftNode(graph) == 1);
    }

    private static int countShiftNode(StructuredGraph graph) {
        return graph.getNodes().filter(node -> node instanceof RightShiftNode || node instanceof UnsignedRightShiftNode).count();
    }

    public static int unknownDivByPowerOf2(boolean flag) {
        int val = flag ? 0x800000F0 : 0x20;
        GraalDirectives.blackhole(val);
        return val / 8;
    }

    @Test
    public void testUnknownDivByPowerOf2() {
        StructuredGraph graph = parseForCompile(getResolvedJavaMethod("unknownDivByPowerOf2"));
        // We expect no rounding is needed
        assertTrue(graph.getNodes().filter(RightShiftNode.class).count() <= 1);
    }

}
