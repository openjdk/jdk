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

import static org.graalvm.compiler.phases.common.DeadCodeEliminationPhase.Optionality.Required;

import jdk.internal.vm.compiler.collections.EconomicSet;
import org.graalvm.compiler.core.common.util.CompilationAlarm;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Graph.NodeEventScope;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.ScheduleResult;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.DeadCodeEliminationPhase;
import org.graalvm.compiler.phases.common.util.EconomicSetNodeEventListener;
import org.graalvm.compiler.phases.graph.ReentrantBlockIterator;
import org.graalvm.compiler.phases.schedule.SchedulePhase;
import org.graalvm.compiler.phases.schedule.SchedulePhase.SchedulingStrategy;

public abstract class EffectsPhase<CoreProvidersT extends CoreProviders> extends BasePhase<CoreProvidersT> {

    public abstract static class Closure<T> extends ReentrantBlockIterator.BlockIteratorClosure<T> {

        public abstract boolean hasChanged();

        public abstract boolean needsApplyEffects();

        public abstract void applyEffects();
    }

    private final int maxIterations;
    protected final CanonicalizerPhase canonicalizer;
    private final boolean unscheduled;
    private final SchedulePhase.SchedulingStrategy strategy;

    protected EffectsPhase(int maxIterations, CanonicalizerPhase canonicalizer) {
        this(maxIterations, canonicalizer, false, SchedulingStrategy.EARLIEST);
    }

    protected EffectsPhase(int maxIterations, CanonicalizerPhase canonicalizer, boolean unscheduled, SchedulingStrategy strategy) {
        this.strategy = strategy;
        this.maxIterations = maxIterations;
        this.canonicalizer = canonicalizer;
        this.unscheduled = unscheduled;
    }

    protected EffectsPhase(int maxIterations, CanonicalizerPhase canonicalizer, boolean unscheduled) {
        this(maxIterations, canonicalizer, unscheduled, unscheduled ? null : SchedulingStrategy.EARLIEST);
    }

    @Override
    protected void run(StructuredGraph graph, CoreProvidersT context) {
        runAnalysis(graph, context);
    }

    @SuppressWarnings("try")
    public boolean runAnalysis(StructuredGraph graph, CoreProvidersT context) {
        assert unscheduled || strategy != null;
        boolean changed = false;
        CompilationAlarm compilationAlarm = CompilationAlarm.current();
        DebugContext debug = graph.getDebug();
        for (int iteration = 0; iteration < maxIterations && !compilationAlarm.hasExpired(); iteration++) {
            try (DebugContext.Scope s = debug.scope(debug.areScopesEnabled() ? "iteration " + iteration : null)) {
                ScheduleResult schedule;
                ControlFlowGraph cfg;
                if (unscheduled) {
                    schedule = null;
                    cfg = ControlFlowGraph.compute(graph, true, true, false, false);
                } else {
                    new SchedulePhase(strategy).apply(graph, false);
                    schedule = graph.getLastSchedule();
                    cfg = schedule.getCFG();
                }
                try (DebugContext.Scope scheduleScope = debug.scope("EffectsPhaseWithSchedule", schedule)) {
                    Closure<?> closure = createEffectsClosure(context, schedule, cfg);
                    ReentrantBlockIterator.apply(closure, cfg.getStartBlock());

                    if (closure.needsApplyEffects()) {
                        // apply the effects collected during this iteration
                        EconomicSetNodeEventListener listener = new EconomicSetNodeEventListener();
                        try (NodeEventScope nes = graph.trackNodeEvents(listener)) {
                            closure.applyEffects();

                            if (debug.isDumpEnabled(DebugContext.VERBOSE_LEVEL)) {
                                debug.dump(DebugContext.VERBOSE_LEVEL, graph, "%s iteration", getName());
                            }

                            new DeadCodeEliminationPhase(Required).apply(graph);
                        }

                        postIteration(graph, context, listener.getNodes());
                    }

                    if (closure.hasChanged()) {
                        changed = true;
                    } else {
                        break;
                    }
                } catch (Throwable t) {
                    throw debug.handle(t);
                }
            }
        }
        return changed;
    }

    protected void postIteration(final StructuredGraph graph, final CoreProvidersT context, EconomicSet<Node> changedNodes) {
        if (canonicalizer != null) {
            canonicalizer.applyIncremental(graph, context, changedNodes);
        }
    }

    protected abstract Closure<?> createEffectsClosure(CoreProvidersT context, ScheduleResult schedule, ControlFlowGraph cfg);
}
