/*
 * Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.core.common.GraalOptions.EscapeAnalysisIterations;
import static org.graalvm.compiler.core.common.GraalOptions.EscapeAnalyzeOnly;

import jdk.internal.vm.compiler.collections.EconomicSet;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.ScheduleResult;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.schedule.SchedulePhase;

public class PartialEscapePhase extends EffectsPhase<CoreProviders> {

    static class Options {
        //@formatter:off
        @Option(help = "", type = OptionType.Debug)
        public static final OptionKey<Boolean> OptEarlyReadElimination = new OptionKey<>(true);
        //@formatter:on
    }

    private final boolean readElimination;
    private final BasePhase<CoreProviders> cleanupPhase;

    public PartialEscapePhase(boolean iterative, CanonicalizerPhase canonicalizer, OptionValues options) {
        this(iterative, Options.OptEarlyReadElimination.getValue(options), canonicalizer, null, options);
    }

    public PartialEscapePhase(boolean iterative, CanonicalizerPhase canonicalizer, BasePhase<CoreProviders> cleanupPhase, OptionValues options) {
        this(iterative, Options.OptEarlyReadElimination.getValue(options), canonicalizer, cleanupPhase, options);
    }

    public PartialEscapePhase(boolean iterative, boolean readElimination, CanonicalizerPhase canonicalizer, BasePhase<CoreProviders> cleanupPhase, OptionValues options) {
        super(iterative ? EscapeAnalysisIterations.getValue(options) : 1, canonicalizer);
        this.readElimination = readElimination;
        this.cleanupPhase = cleanupPhase;
    }

    public PartialEscapePhase(boolean iterative, boolean readElimination, CanonicalizerPhase canonicalizer, BasePhase<CoreProviders> cleanupPhase, OptionValues options,
                    SchedulePhase.SchedulingStrategy strategy) {
        super(iterative ? EscapeAnalysisIterations.getValue(options) : 1, canonicalizer, false, strategy);
        this.readElimination = readElimination;
        this.cleanupPhase = cleanupPhase;
    }

    @Override
    protected void postIteration(StructuredGraph graph, CoreProviders context, EconomicSet<Node> changedNodes) {
        super.postIteration(graph, context, changedNodes);
        if (cleanupPhase != null) {
            cleanupPhase.apply(graph, context);
        }
    }

    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        if (VirtualUtil.matches(graph, EscapeAnalyzeOnly.getValue(graph.getOptions()))) {
            if (readElimination || graph.hasVirtualizableAllocation()) {
                runAnalysis(graph, context);
            }
        }
    }

    @Override
    protected Closure<?> createEffectsClosure(CoreProviders context, ScheduleResult schedule, ControlFlowGraph cfg) {
        for (VirtualObjectNode virtual : cfg.graph.getNodes(VirtualObjectNode.TYPE)) {
            virtual.resetObjectId();
        }
        assert schedule != null;
        if (readElimination) {
            return new PEReadEliminationClosure(schedule, context);
        } else {
            return new PartialEscapeClosure.Final(schedule, context);
        }
    }

    @Override
    public boolean checkContract() {
        return false;
    }

}
