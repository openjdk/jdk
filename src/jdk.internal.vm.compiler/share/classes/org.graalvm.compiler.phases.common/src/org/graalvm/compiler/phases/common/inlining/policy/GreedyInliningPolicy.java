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


package org.graalvm.compiler.phases.common.inlining.policy;

import static org.graalvm.compiler.core.common.GraalOptions.InlineEverything;
import static org.graalvm.compiler.core.common.GraalOptions.LimitInlinedInvokes;
import static org.graalvm.compiler.core.common.GraalOptions.MaximumDesiredSize;
import static org.graalvm.compiler.core.common.GraalOptions.MaximumInliningSize;
import static org.graalvm.compiler.core.common.GraalOptions.SmallCompiledLowLevelGraphSize;
import static org.graalvm.compiler.core.common.GraalOptions.TraceInlining;
import static org.graalvm.compiler.core.common.GraalOptions.TrivialInliningSize;

import java.util.Map;

import org.graalvm.compiler.debug.CounterKey;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.spi.Replacements;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.common.inlining.InliningUtil;
import org.graalvm.compiler.phases.common.inlining.info.InlineInfo;
import org.graalvm.compiler.phases.common.inlining.walker.MethodInvocation;

public class GreedyInliningPolicy extends AbstractInliningPolicy {

    private static final CounterKey inliningStoppedByMaxDesiredSizeCounter = DebugContext.counter("InliningStoppedByMaxDesiredSize");

    public GreedyInliningPolicy(Map<Invoke, Double> hints) {
        super(hints);
    }

    @Override
    public boolean continueInlining(StructuredGraph currentGraph) {
        if (InliningUtil.getNodeCount(currentGraph) >= MaximumDesiredSize.getValue(currentGraph.getOptions())) {
            DebugContext debug = currentGraph.getDebug();
            InliningUtil.logInliningDecision(debug, "inlining is cut off by MaximumDesiredSize");
            inliningStoppedByMaxDesiredSizeCounter.increment(debug);
            return false;
        }
        return true;
    }

    @Override
    public Decision isWorthInlining(Replacements replacements, MethodInvocation invocation, int inliningDepth, boolean fullyProcessed) {
        final boolean isTracing = TraceInlining.getValue(replacements.getOptions());
        final InlineInfo info = invocation.callee();
        OptionValues options = info.graph().getOptions();
        final double probability = invocation.probability();
        final double relevance = invocation.relevance();

        if (InlineEverything.getValue(options)) {
            InliningUtil.traceInlinedMethod(info, inliningDepth, fullyProcessed, "inline everything");
            return InliningPolicy.Decision.YES.withReason(isTracing, "inline everything");
        }

        if (isIntrinsic(replacements, info)) {
            InliningUtil.traceInlinedMethod(info, inliningDepth, fullyProcessed, "intrinsic");
            return InliningPolicy.Decision.YES.withReason(isTracing, "intrinsic");
        }

        if (info.shouldInline()) {
            InliningUtil.traceInlinedMethod(info, inliningDepth, fullyProcessed, "forced inlining");
            return InliningPolicy.Decision.YES.withReason(isTracing, "forced inlining");
        }

        double inliningBonus = getInliningBonus(info);
        int nodes = info.determineNodeCount();
        int lowLevelGraphSize = previousLowLevelGraphSize(info);

        if (SmallCompiledLowLevelGraphSize.getValue(options) > 0 && lowLevelGraphSize > SmallCompiledLowLevelGraphSize.getValue(options) * inliningBonus) {
            InliningUtil.traceNotInlinedMethod(info, inliningDepth, "too large previous low-level graph (low-level-nodes: %d, relevance=%f, probability=%f, bonus=%f, nodes=%d)", lowLevelGraphSize,
                            relevance, probability, inliningBonus, nodes);
            return InliningPolicy.Decision.NO.withReason(isTracing, "too large previous low-level graph (low-level-nodes: %d, relevance=%f, probability=%f, bonus=%f, nodes=%d)", lowLevelGraphSize,
                            relevance, probability, inliningBonus, nodes);
        }

        if (nodes < TrivialInliningSize.getValue(options) * inliningBonus) {
            InliningUtil.traceInlinedMethod(info, inliningDepth, fullyProcessed, "trivial (relevance=%f, probability=%f, bonus=%f, nodes=%d)", relevance, probability, inliningBonus, nodes);
            return InliningPolicy.Decision.YES.withReason(isTracing, "trivial (relevance=%f, probability=%f, bonus=%f, nodes=%d)", relevance, probability, inliningBonus, nodes);
        }

        /*
         * TODO (chaeubl): invoked methods that are on important paths but not yet compiled -> will
         * be compiled anyways and it is likely that we are the only caller... might be useful to
         * inline those methods but increases bootstrap time (maybe those methods are also getting
         * queued in the compilation queue concurrently)
         */
        double invokes = determineInvokeProbability(info);
        if (LimitInlinedInvokes.getValue(options) > 0 && fullyProcessed && invokes > LimitInlinedInvokes.getValue(options) * inliningBonus) {
            InliningUtil.traceNotInlinedMethod(info, inliningDepth, "callee invoke probability is too high (invokeP=%f, relevance=%f, probability=%f, bonus=%f, nodes=%d)", invokes, relevance,
                            probability, inliningBonus, nodes);
            return InliningPolicy.Decision.NO.withReason(isTracing, "callee invoke probability is too high (invokeP=%f, relevance=%f, probability=%f, bonus=%f, nodes=%d)", invokes, relevance,
                            probability, inliningBonus, nodes);
        }

        double maximumNodes = computeMaximumSize(relevance, (int) (MaximumInliningSize.getValue(options) * inliningBonus));
        if (nodes <= maximumNodes) {
            InliningUtil.traceInlinedMethod(info, inliningDepth, fullyProcessed, "relevance-based (relevance=%f, probability=%f, bonus=%f, nodes=%d <= %f)", relevance, probability, inliningBonus,
                            nodes, maximumNodes);
            return InliningPolicy.Decision.YES.withReason(isTracing, "relevance-based (relevance=%f, probability=%f, bonus=%f, nodes=%d <= %f)", relevance, probability, inliningBonus,
                            nodes, maximumNodes);
        }

        InliningUtil.traceNotInlinedMethod(info, inliningDepth, "relevance-based (relevance=%f, probability=%f, bonus=%f, nodes=%d > %f)", relevance, probability, inliningBonus, nodes, maximumNodes);
        return InliningPolicy.Decision.NO.withReason(isTracing, "relevance-based (relevance=%f, probability=%f, bonus=%f, nodes=%d > %f)", relevance, probability, inliningBonus, nodes, maximumNodes);
    }
}
