/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.phases.aot;

import static org.graalvm.compiler.core.common.GraalOptions.InlineEverything;
import static org.graalvm.compiler.core.common.GraalOptions.TrivialInliningSize;

import java.util.Map;

import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.spi.Replacements;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.common.inlining.InliningUtil;
import org.graalvm.compiler.phases.common.inlining.info.InlineInfo;
import org.graalvm.compiler.phases.common.inlining.policy.GreedyInliningPolicy;
import org.graalvm.compiler.phases.common.inlining.policy.InliningPolicy;
import org.graalvm.compiler.phases.common.inlining.walker.MethodInvocation;

import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;

public class AOTInliningPolicy extends GreedyInliningPolicy {
    public static class Options {
        // @formatter:off
        @Option(help = "", type = OptionType.Expert)
        public static final OptionKey<Double> AOTInliningDepthToSizeRate = new OptionKey<>(2.5);
        @Option(help = "", type = OptionType.Expert)
        public static final OptionKey<Integer> AOTInliningSizeMaximum = new OptionKey<>(300);
        @Option(help = "", type = OptionType.Expert)
        public static final OptionKey<Integer> AOTInliningSizeMinimum = new OptionKey<>(50);
        // @formatter:on
    }

    public AOTInliningPolicy(Map<Invoke, Double> hints) {
        super(hints);
    }

    protected double maxInliningSize(int inliningDepth, OptionValues options) {
        return Math.max(Options.AOTInliningSizeMaximum.getValue(options) / (inliningDepth * Options.AOTInliningDepthToSizeRate.getValue(options)), Options.AOTInliningSizeMinimum.getValue(options));
    }

    @Override
    public Decision isWorthInlining(Replacements replacements, MethodInvocation invocation, int inliningDepth, boolean fullyProcessed) {
        final boolean isTracing = GraalOptions.TraceInlining.getValue(replacements.getOptions());
        final InlineInfo info = invocation.callee();

        for (int i = 0; i < info.numberOfMethods(); ++i) {
            HotSpotResolvedObjectType t = (HotSpotResolvedObjectType) info.methodAt(i).getDeclaringClass();
            if (t.getFingerprint() == 0) {
                return InliningPolicy.Decision.NO;
            }
        }

        final double probability = invocation.probability();
        final double relevance = invocation.relevance();

        OptionValues options = info.graph().getOptions();
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

        if (nodes < TrivialInliningSize.getValue(options) * inliningBonus) {
            InliningUtil.traceInlinedMethod(info, inliningDepth, fullyProcessed, "trivial (relevance=%f, probability=%f, bonus=%f, nodes=%d)", relevance, probability, inliningBonus, nodes);
            return InliningPolicy.Decision.YES.withReason(isTracing, "trivial (relevance=%f, probability=%f, bonus=%f, nodes=%d)", relevance, probability, inliningBonus, nodes);
        }

        double maximumNodes = computeMaximumSize(relevance, (int) (maxInliningSize(inliningDepth, options) * inliningBonus));
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
