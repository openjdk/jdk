/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.loop.phases;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.loop.LoopEx;
import org.graalvm.compiler.loop.LoopsData;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.calc.BinaryArithmeticNode;
import org.graalvm.compiler.phases.Phase;

/**
 * Rearrange {@link BinaryArithmeticNode#isAssociative() associative binary operations} so that
 * invariant parts of the expression can move outside of the loop.
 */
public class ReassociateInvariantPhase extends Phase {

    @SuppressWarnings("try")
    @Override
    protected void run(StructuredGraph graph) {
        int iterations = 0;
        DebugContext debug = graph.getDebug();
        try (DebugContext.Scope s = debug.scope("ReassociateInvariants")) {
            boolean changed = true;
            while (changed) {
                changed = false;
                final LoopsData dataReassociate = new LoopsData(graph);
                for (LoopEx loop : dataReassociate.loops()) {
                    changed |= loop.reassociateInvariants();
                }
                dataReassociate.deleteUnusedNodes();
                iterations++;
                debug.dump(DebugContext.VERBOSE_LEVEL, graph, "after iteration %d", iterations);
            }
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }
}
