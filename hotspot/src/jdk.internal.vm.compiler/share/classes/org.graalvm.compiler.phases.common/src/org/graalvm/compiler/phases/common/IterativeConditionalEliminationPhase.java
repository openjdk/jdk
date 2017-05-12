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
package org.graalvm.compiler.phases.common;

import static org.graalvm.compiler.graph.Graph.NodeEvent.NODE_ADDED;

import org.graalvm.compiler.core.common.RetryableBailoutException;
import org.graalvm.compiler.graph.Graph.NodeEventScope;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.spi.Simplifiable;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.common.util.HashSetNodeEventListener;
import org.graalvm.compiler.phases.tiers.PhaseContext;

public class IterativeConditionalEliminationPhase extends BasePhase<PhaseContext> {

    private static final int MAX_ITERATIONS = 256;

    private final CanonicalizerPhase canonicalizer;
    private final boolean fullSchedule;

    public IterativeConditionalEliminationPhase(CanonicalizerPhase canonicalizer, boolean fullSchedule) {
        this.canonicalizer = canonicalizer;
        this.fullSchedule = fullSchedule;
    }

    @Override
    @SuppressWarnings("try")
    protected void run(StructuredGraph graph, PhaseContext context) {
        HashSetNodeEventListener listener = new HashSetNodeEventListener().exclude(NODE_ADDED);
        int count = 0;
        while (true) {
            try (NodeEventScope nes = graph.trackNodeEvents(listener)) {
                new ConditionalEliminationPhase(fullSchedule).apply(graph, context);
            }
            if (listener.getNodes().isEmpty()) {
                break;
            }
            for (Node node : graph.getNodes()) {
                if (node instanceof Simplifiable) {
                    listener.getNodes().add(node);
                }
            }
            canonicalizer.applyIncremental(graph, context, listener.getNodes());
            listener.getNodes().clear();
            if (++count > MAX_ITERATIONS) {
                throw new RetryableBailoutException("Number of iterations in ConditionalEliminationPhase phase exceeds %d", MAX_ITERATIONS);
            }
        }
    }

    @Override
    public float codeSizeIncrease() {
        return 2.0f;
    }
}
