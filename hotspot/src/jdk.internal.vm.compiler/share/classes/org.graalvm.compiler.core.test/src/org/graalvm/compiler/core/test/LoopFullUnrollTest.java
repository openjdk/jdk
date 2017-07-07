/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugDumpScope;
import org.graalvm.compiler.loop.DefaultLoopPolicies;
import org.graalvm.compiler.loop.phases.LoopFullUnrollPhase;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.tiers.PhaseContext;
import org.junit.Test;

public class LoopFullUnrollTest extends GraalCompilerTest {

    public static int testMinToMax(int input) {
        int ret = 2;
        int current = input;
        for (long i = Long.MIN_VALUE; i < Long.MAX_VALUE; i++) {
            ret *= 2 + current;
            current /= 50;
        }
        return ret;
    }

    @Test
    public void runMinToMax() throws Throwable {
        test("testMinToMax", 1);
    }

    public static int testMinTo0(int input) {
        int ret = 2;
        int current = input;
        for (long i = Long.MIN_VALUE; i <= 0; i++) {
            ret *= 2 + current;
            current /= 50;
        }
        return ret;
    }

    @Test
    public void runMinTo0() throws Throwable {
        test("testMinTo0", 1);
    }

    public static int testNegativeTripCount(int input) {
        int ret = 2;
        int current = input;
        for (long i = 0; i <= -20; i++) {
            ret *= 2 + current;
            current /= 50;
        }
        return ret;
    }

    @Test
    public void runNegativeTripCount() throws Throwable {
        test("testNegativeTripCount", 0);
    }

    @SuppressWarnings("try")
    private void test(String snippet, int loopCount) {
        DebugContext debug = getDebugContext();
        try (DebugContext.Scope s = debug.scope(getClass().getSimpleName(), new DebugDumpScope(snippet))) {
            final StructuredGraph graph = parseEager(snippet, AllowAssumptions.NO, debug);

            PhaseContext context = new PhaseContext(getProviders());
            new LoopFullUnrollPhase(new CanonicalizerPhase(), new DefaultLoopPolicies()).apply(graph, context);

            assertTrue(graph.getNodes().filter(LoopBeginNode.class).count() == loopCount);
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }
}
