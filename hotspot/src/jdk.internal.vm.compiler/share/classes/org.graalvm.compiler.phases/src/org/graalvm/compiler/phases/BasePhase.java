/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.phases;

import java.util.regex.Pattern;

import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.Debug.Scope;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugCounter;
import org.graalvm.compiler.debug.DebugMemUseTracker;
import org.graalvm.compiler.debug.DebugTimer;
import org.graalvm.compiler.debug.Fingerprint;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.graph.Graph.Mark;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValue;
import org.graalvm.compiler.options.StableOptionValue;
import org.graalvm.compiler.phases.contract.NodeCostUtil;
import org.graalvm.compiler.phases.contract.PhaseSizeContract;
import org.graalvm.compiler.phases.tiers.PhaseContext;

/**
 * Base class for all compiler phases. Subclasses should be stateless. There will be one global
 * instance for each compiler phase that is shared for all compilations. VM-, target- and
 * compilation-specific data can be passed with a context object.
 */
public abstract class BasePhase<C> implements PhaseSizeContract {

    public static class PhaseOptions {
        // @formatter:off
        @Option(help = "Verify before - after relation of the relative, computed, code size of a graph", type = OptionType.Debug)
        public static final OptionValue<Boolean> VerifyGraalPhasesSize = new StableOptionValue<>(false);
        // @formatter:on
    }

    /**
     * Records time spent in {@link #apply(StructuredGraph, Object, boolean)}.
     */
    private final DebugTimer timer;

    /**
     * Counts calls to {@link #apply(StructuredGraph, Object, boolean)}.
     */
    private final DebugCounter executionCount;

    /**
     * Accumulates the {@linkplain Graph#getNodeCount() live node count} of all graphs sent to
     * {@link #apply(StructuredGraph, Object, boolean)}.
     */
    private final DebugCounter inputNodesCount;

    /**
     * Records memory usage within {@link #apply(StructuredGraph, Object, boolean)}.
     */
    private final DebugMemUseTracker memUseTracker;

    /** Lazy initialization to create pattern only when assertions are enabled. */
    static class NamePatternHolder {
        static final Pattern NAME_PATTERN = Pattern.compile("[A-Z][A-Za-z0-9]+");
    }

    private static class BasePhaseStatistics {
        /**
         * Records time spent in {@link #apply(StructuredGraph, Object, boolean)}.
         */
        private final DebugTimer timer;

        /**
         * Counts calls to {@link #apply(StructuredGraph, Object, boolean)}.
         */
        private final DebugCounter executionCount;

        /**
         * Accumulates the {@linkplain Graph#getNodeCount() live node count} of all graphs sent to
         * {@link #apply(StructuredGraph, Object, boolean)}.
         */
        private final DebugCounter inputNodesCount;

        /**
         * Records memory usage within {@link #apply(StructuredGraph, Object, boolean)}.
         */
        private final DebugMemUseTracker memUseTracker;

        BasePhaseStatistics(Class<?> clazz) {
            timer = Debug.timer("PhaseTime_%s", clazz);
            executionCount = Debug.counter("PhaseCount_%s", clazz);
            memUseTracker = Debug.memUseTracker("PhaseMemUse_%s", clazz);
            inputNodesCount = Debug.counter("PhaseNodes_%s", clazz);
        }
    }

    private static final ClassValue<BasePhaseStatistics> statisticsClassValue = new ClassValue<BasePhaseStatistics>() {
        @Override
        protected BasePhaseStatistics computeValue(Class<?> c) {
            return new BasePhaseStatistics(c);
        }
    };

    protected BasePhase() {
        BasePhaseStatistics statistics = statisticsClassValue.get(getClass());
        timer = statistics.timer;
        executionCount = statistics.executionCount;
        memUseTracker = statistics.memUseTracker;
        inputNodesCount = statistics.inputNodesCount;
    }

    public final void apply(final StructuredGraph graph, final C context) {
        apply(graph, context, true);
    }

    @SuppressWarnings("try")
    protected final void apply(final StructuredGraph graph, final C context, final boolean dumpGraph) {
        try (DebugCloseable a = timer.start(); Scope s = Debug.scope(getClass(), this); DebugCloseable c = memUseTracker.start()) {
            int sizeBefore = 0;
            Mark before = null;
            if (PhaseOptions.VerifyGraalPhasesSize.getValue() && checkContract()) {
                if (context instanceof PhaseContext) {
                    sizeBefore = NodeCostUtil.computeGraphSize(graph, ((PhaseContext) context).getNodeCostProvider());
                    before = graph.getMark();
                }
            }
            if (dumpGraph && Debug.isDumpEnabled(Debug.VERBOSE_LOG_LEVEL)) {
                Debug.dump(Debug.VERBOSE_LOG_LEVEL, graph, "Before phase %s", getName());
            }
            inputNodesCount.add(graph.getNodeCount());
            this.run(graph, context);
            executionCount.increment();
            if (PhaseOptions.VerifyGraalPhasesSize.getValue() && checkContract()) {
                if (context instanceof PhaseContext) {
                    if (!before.isCurrent()) {
                        int sizeAfter = NodeCostUtil.computeGraphSize(graph, ((PhaseContext) context).getNodeCostProvider());
                        NodeCostUtil.phaseFulfillsSizeContract(graph, sizeBefore, sizeAfter, this);
                    }
                }
            }
            if (dumpGraph && Debug.isDumpEnabled(Debug.BASIC_LOG_LEVEL)) {
                Debug.dump(Debug.BASIC_LOG_LEVEL, graph, "%s", getName());
            }
            if (Fingerprint.ENABLED) {
                String graphDesc = graph.method() == null ? graph.name : graph.method().format("%H.%n(%p)");
                Fingerprint.submit("After phase %s nodes in %s are %s", getName(), graphDesc, graph.getNodes().snapshot());
            }
            if (Debug.isVerifyEnabled()) {
                Debug.verify(graph, "%s", getName());
            }
            assert graph.verify();
        } catch (Throwable t) {
            throw Debug.handle(t);
        }
    }

    protected CharSequence getName() {
        String className = BasePhase.this.getClass().getName();
        String s = className.substring(className.lastIndexOf(".") + 1); // strip the package name
        int innerClassPos = s.indexOf('$');
        if (innerClassPos > 0) {
            /* Remove inner class name. */
            s = s.substring(0, innerClassPos);
        }
        if (s.endsWith("Phase")) {
            s = s.substring(0, s.length() - "Phase".length());
        }
        return s;
    }

    protected abstract void run(StructuredGraph graph, C context);

    @Override
    public String contractorName() {
        return (String) getName();
    }

    @Override
    public float codeSizeIncrease() {
        return 1.25f;
    }

}
