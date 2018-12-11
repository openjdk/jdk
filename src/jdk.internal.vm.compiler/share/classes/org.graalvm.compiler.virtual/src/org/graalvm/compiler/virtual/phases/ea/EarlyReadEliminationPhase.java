/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.virtual.phases.ea;

import static org.graalvm.compiler.core.common.GraalOptions.EscapeAnalyzeOnly;

import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.ScheduleResult;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.tiers.PhaseContext;

public class EarlyReadEliminationPhase extends EffectsPhase<PhaseContext> {

    private final boolean considerGuards;

    public EarlyReadEliminationPhase(CanonicalizerPhase canonicalizer) {
        super(1, canonicalizer, true);
        this.considerGuards = true;
    }

    public EarlyReadEliminationPhase(CanonicalizerPhase canonicalizer, boolean considerGuards) {
        super(1, canonicalizer, true);
        this.considerGuards = considerGuards;
    }

    @Override
    protected void run(StructuredGraph graph, PhaseContext context) {
        if (VirtualUtil.matches(graph, EscapeAnalyzeOnly.getValue(graph.getOptions()))) {
            runAnalysis(graph, context);
        }
    }

    @Override
    protected Closure<?> createEffectsClosure(PhaseContext context, ScheduleResult schedule, ControlFlowGraph cfg) {
        assert schedule == null;
        return new ReadEliminationClosure(cfg, considerGuards);
    }

    @Override
    public float codeSizeIncrease() {
        return 2f;
    }
}
