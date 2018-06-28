/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.graph.Graph.NodeEventScope;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.common.util.HashSetNodeEventListener;
import org.graalvm.compiler.phases.tiers.PhaseContext;

/**
 * A phase suite that applies {@linkplain CanonicalizerPhase canonicalization} to a graph after all
 * phases in the suite have been applied if any of the phases changed the graph.
 */
public class IncrementalCanonicalizerPhase<C extends PhaseContext> extends PhaseSuite<C> {

    private final CanonicalizerPhase canonicalizer;

    public IncrementalCanonicalizerPhase(CanonicalizerPhase canonicalizer) {
        this.canonicalizer = canonicalizer;
    }

    public IncrementalCanonicalizerPhase(CanonicalizerPhase canonicalizer, BasePhase<? super C> phase) {
        this.canonicalizer = canonicalizer;
        appendPhase(phase);
    }

    @Override
    @SuppressWarnings("try")
    protected void run(StructuredGraph graph, C context) {
        HashSetNodeEventListener listener = new HashSetNodeEventListener();
        try (NodeEventScope nes = graph.trackNodeEvents(listener)) {
            super.run(graph, context);
        }

        if (!listener.getNodes().isEmpty()) {
            canonicalizer.applyIncremental(graph, context, listener.getNodes(), null, false);
        }
    }
}
