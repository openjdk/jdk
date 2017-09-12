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
package org.graalvm.compiler.core.phases;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Graph.NodeEvent;
import org.graalvm.compiler.graph.Graph.NodeEventScope;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.LogicConstantNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.common.util.HashSetNodeEventListener;
import org.graalvm.compiler.phases.tiers.PhaseContext;
import org.graalvm.util.EconomicSet;
import org.graalvm.util.Equivalence;

/**
 * A utility phase for detecting when a phase would change the graph and reporting extra information
 * about the effects. The phase is first run on a copy of the graph and if a change in that graph is
 * detected then it's rerun on the original graph inside a new debug scope under
 * GraphChangeMonitoringPhase. The message argument can be used to distinguish between the same
 * phase run at different points.
 *
 * @param <C>
 */
public class GraphChangeMonitoringPhase<C extends PhaseContext> extends PhaseSuite<C> {

    private final String message;

    public GraphChangeMonitoringPhase(String message, BasePhase<C> phase) {
        super();
        this.message = message;
        appendPhase(phase);
    }

    public GraphChangeMonitoringPhase(String message) {
        super();
        this.message = message;
    }

    @Override
    @SuppressWarnings("try")
    protected void run(StructuredGraph graph, C context) {
        /*
         * Phase may add nodes but not end up using them so ignore additions. Nodes going dead and
         * having their inputs change are the main interesting differences.
         */
        HashSetNodeEventListener listener = new HashSetNodeEventListener().exclude(NodeEvent.NODE_ADDED);
        StructuredGraph graphCopy = (StructuredGraph) graph.copy(graph.getDebug());
        DebugContext debug = graph.getDebug();
        try (NodeEventScope s = graphCopy.trackNodeEvents(listener)) {
            try (DebugContext.Scope s2 = debug.sandbox("WithoutMonitoring", null)) {
                super.run(graphCopy, context);
            } catch (Throwable t) {
                debug.handle(t);
            }
        }

        EconomicSet<Node> filteredNodes = EconomicSet.create(Equivalence.IDENTITY);
        for (Node n : listener.getNodes()) {
            if (n instanceof LogicConstantNode) {
                // Ignore LogicConstantNode since those are sometimes created and deleted as part of
                // running a phase.
            } else {
                filteredNodes.add(n);
            }
        }
        if (!filteredNodes.isEmpty()) {
            /* rerun it on the real graph in a new Debug scope so Dump and Log can find it. */
            listener = new HashSetNodeEventListener();
            try (NodeEventScope s = graph.trackNodeEvents(listener)) {
                try (DebugContext.Scope s2 = debug.scope("WithGraphChangeMonitoring")) {
                    if (debug.isDumpEnabled(DebugContext.DETAILED_LEVEL)) {
                        debug.dump(DebugContext.DETAILED_LEVEL, graph, "*** Before phase %s", getName());
                    }
                    super.run(graph, context);
                    if (debug.isDumpEnabled(DebugContext.DETAILED_LEVEL)) {
                        debug.dump(DebugContext.DETAILED_LEVEL, graph, "*** After phase %s %s", getName(), filteredNodes);
                    }
                    debug.log("*** %s %s %s\n", message, graph, filteredNodes);
                }
            }
        } else {
            // Go ahead and run it normally even though it should have no effect
            super.run(graph, context);
        }
    }
}
