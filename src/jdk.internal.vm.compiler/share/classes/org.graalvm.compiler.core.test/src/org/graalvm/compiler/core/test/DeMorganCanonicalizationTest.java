/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.calc.AndNode;
import org.graalvm.compiler.nodes.calc.NotNode;
import org.graalvm.compiler.nodes.calc.OrNode;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.junit.Assert;
import org.junit.Test;

public class DeMorganCanonicalizationTest extends GraalCompilerTest {

    public static int or(int a, int b) {
        return ~a | ~b;
    }

    public static int and(int a, int b) {
        return ~a & ~b;
    }

    @Test
    public void testAnd() {
        StructuredGraph g = parseEager("and", AllowAssumptions.NO, getInitialOptions());
        new CanonicalizerPhase().apply(g, getDefaultHighTierContext());
        Assert.assertEquals(1, g.getNodes().filter(OrNode.class).count());
        Assert.assertEquals(1, g.getNodes().filter(NotNode.class).count());

        testAgainstExpected(g.method(), new Result(and(-1, 17), null), (Object) null, -1, 17);
        testAgainstExpected(g.method(), new Result(and(-1, 1), null), (Object) null, -1, 1);
        testAgainstExpected(g.method(), new Result(and(-1, -1), null), (Object) null, -1, -1);
        testAgainstExpected(g.method(), new Result(and(Integer.MIN_VALUE, Integer.MIN_VALUE), null), (Object) null, Integer.MIN_VALUE, Integer.MIN_VALUE);
    }

    @Test
    public void testOr() {
        StructuredGraph g = parseEager("or", AllowAssumptions.NO, getInitialOptions());
        new CanonicalizerPhase().apply(g, getDefaultHighTierContext());
        Assert.assertEquals(1, g.getNodes().filter(AndNode.class).count());
        Assert.assertEquals(1, g.getNodes().filter(NotNode.class).count());

        testAgainstExpected(g.method(), new Result(or(-1, 17), null), (Object) null, -1, 17);
        testAgainstExpected(g.method(), new Result(or(-1, 1), null), (Object) null, -1, 1);
        testAgainstExpected(g.method(), new Result(or(-1, -1), null), (Object) null, -1, -1);
        testAgainstExpected(g.method(), new Result(or(Integer.MIN_VALUE, Integer.MIN_VALUE), null), (Object) null, Integer.MIN_VALUE, Integer.MIN_VALUE);
    }

}
