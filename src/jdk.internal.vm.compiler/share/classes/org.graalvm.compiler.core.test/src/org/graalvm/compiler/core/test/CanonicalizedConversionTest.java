/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.FloatEqualsNode;
import org.graalvm.compiler.nodes.calc.ReinterpretNode;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests that substitutions for {@link Double#doubleToLongBits(double)} and
 * {@link Float#floatToIntBits(float)} produce graphs such that multiple calls to these methods with
 * the same input are canonicalized.
 */
public class CanonicalizedConversionTest extends GraalCompilerTest {

    @Override
    protected boolean checkLowTierGraph(StructuredGraph graph) {
        int reinterpretCount = 0;
        int floatEqualsCount = 0;
        int addCount = 0;
        for (Node node : graph.getNodes()) {
            if (node instanceof ReinterpretNode) {
                reinterpretCount++;
            } else if (node instanceof FloatEqualsNode) {
                floatEqualsCount++;
            } else if (node instanceof IfNode) {
                Assert.fail("Unexpected node: " + node);
            } else if (node instanceof AddNode) {
                addCount++;
            }
        }
        Assert.assertEquals(1, reinterpretCount);
        Assert.assertEquals(1, floatEqualsCount);
        Assert.assertEquals(2, addCount);
        return true;
    }

    @Test
    public void test4() {
        test("snippet4", 567.890F);
        test("snippet4", -567.890F);
        test("snippet4", Float.NaN);
    }

    public static int snippet4(float value) {
        return Float.floatToIntBits(value) + Float.floatToIntBits(value) + Float.floatToIntBits(value);
    }

    @Test
    public void test5() {
        test("snippet5", 567.890D);
        test("snippet5", -567.890D);
        test("snippet5", Double.NaN);
    }

    public static long snippet5(double value) {
        return Double.doubleToLongBits(value) + Double.doubleToLongBits(value) + Double.doubleToLongBits(value);
    }
}
