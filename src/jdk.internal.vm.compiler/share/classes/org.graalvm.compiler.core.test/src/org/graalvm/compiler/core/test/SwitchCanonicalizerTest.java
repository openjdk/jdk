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

import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.extended.IntegerSwitchNode;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.junit.Test;

public class SwitchCanonicalizerTest extends GraalCompilerTest {

    public int divByPowerOf2(int n) {
        switch (n / 8) {
            case Integer.MAX_VALUE / 8 + 1:
                return hashCode();
            default:
                return 1;
        }
    }

    @Test
    public void testDivByPowerOf2() {
        shouldFoldSwitch("divByPowerOf2");
    }

    public int divByNonPowerOf2(int n) {
        switch (n / 7) {
            case Integer.MAX_VALUE / 7 + 1:
                return hashCode();
            default:
                return 1;
        }
    }

    @Test
    public void testDivByNonPowerOf2() {
        shouldFoldSwitch("divByNonPowerOf2");
    }

    public int remByPowerOf2(int n) {
        switch (n % 8) {
            case 9:
                return hashCode();
            default:
                return 1;
        }
    }

    @Test
    public void testRemByPowerOf2() {
        shouldFoldSwitch("remByPowerOf2");
    }

    public int remByPowerOf2PositiveX(int n) {
        int n0 = n > 0 ? 8 : 9;
        switch (n0 % 8) {
            case 9:
                return hashCode();
            default:
                return 1;
        }
    }

    @Test
    public void testRemByPowerOf2PositiveX() {
        shouldFoldSwitch("remByPowerOf2PositiveX");
    }

    public int remByPowerOf2NegativeX(int n) {
        int n0 = n > 0 ? -8 : -9;
        switch (n0 % 8) {
            case 9:
                return hashCode();
            default:
                return 1;
        }
    }

    @Test
    public void testRemByPowerOf2NegativeX() {
        shouldFoldSwitch("remByPowerOf2NegativeX");
    }

    public int remByNonPowerOf2(int n) {
        switch (n % 7) {
            case 9:
                return hashCode();
            default:
                return 1;
        }
    }

    @Test
    public void testRemByNonPowerOf2() {
        shouldFoldSwitch("remByNonPowerOf2");
    }

    private void shouldFoldSwitch(String methodName) {
        StructuredGraph graph = parseForCompile(getResolvedJavaMethod(methodName));
        new CanonicalizerPhase().apply(graph, getDefaultHighTierContext());
        assertFalse(graph.hasNode(IntegerSwitchNode.TYPE));
    }

}
