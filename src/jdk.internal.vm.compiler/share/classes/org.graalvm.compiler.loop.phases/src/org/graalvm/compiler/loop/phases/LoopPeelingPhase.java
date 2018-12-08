/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.compiler.loop.LoopPolicies;
import org.graalvm.compiler.loop.LoopsData;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.phases.tiers.PhaseContext;

public class LoopPeelingPhase extends LoopPhase<LoopPolicies> {

    public LoopPeelingPhase(LoopPolicies policies) {
        super(policies);
    }

    @Override
    @SuppressWarnings("try")
    protected void run(StructuredGraph graph, PhaseContext context) {
        DebugContext debug = graph.getDebug();
        if (graph.hasLoops()) {
            LoopsData data = new LoopsData(graph);
            try (DebugContext.Scope s = debug.scope("peeling", data.getCFG())) {
                for (LoopEx loop : data.outerFirst()) {
                    if (getPolicies().shouldPeel(loop, data.getCFG(), context.getMetaAccess())) {
                        debug.log("Peeling %s", loop);
                        LoopTransformations.peel(loop);
                        debug.dump(DebugContext.DETAILED_LEVEL, graph, "Peeling %s", loop);
                    }
                }
                data.deleteUnusedNodes();
            } catch (Throwable t) {
                throw debug.handle(t);
            }
        }
    }

    @Override
    public float codeSizeIncrease() {
        return 5.0f;
    }
}
