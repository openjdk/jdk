/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.replacements.profiling;

import static org.graalvm.compiler.hotspot.GraalHotSpotVMConfig.INJECTED_VMCONFIG;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.config;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.SLOW_PATH_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.probability;
import static org.graalvm.compiler.replacements.SnippetTemplate.DEFAULT_REPLACER;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.Snippet.ConstantParameter;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node.ConstantNodeParameter;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.hotspot.HotSpotBackend;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.nodes.aot.LoadMethodCountersNode;
import org.graalvm.compiler.hotspot.nodes.profiling.ProfileBranchNode;
import org.graalvm.compiler.hotspot.nodes.profiling.ProfileInvokeNode;
import org.graalvm.compiler.hotspot.nodes.profiling.ProfileNode;
import org.graalvm.compiler.hotspot.word.MethodCountersPointer;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.SnippetTemplate.AbstractTemplates;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.Snippets;

import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.code.TargetDescription;

public class ProbabilisticProfileSnippets implements Snippets {
    @Snippet
    public static boolean shouldProfile(@ConstantParameter int probLog, int random) {
        int probabilityMask = (1 << probLog) - 1;
        return (random & probabilityMask) == 0;
    }

    @Snippet
    public static int notificationMask(int freqLog, int probLog, int stepLog) {
        int frequencyMask = (1 << freqLog) - 1;
        int stepMask = (1 << (stepLog + probLog)) - 1;
        return frequencyMask & ~stepMask;
    }

    @NodeIntrinsic(ForeignCallNode.class)
    public static native void methodInvocationEvent(@ConstantNodeParameter ForeignCallDescriptor descriptor, MethodCountersPointer counters);

    @Snippet
    public static void profileMethodEntryWithProbability(MethodCountersPointer counters, int random, int step, int stepLog, @ConstantParameter int freqLog, @ConstantParameter int probLog) {
        if (probability(1.0 / (1 << probLog), shouldProfile(probLog, random))) {
            int counterValue = counters.readInt(config(INJECTED_VMCONFIG).invocationCounterOffset) + ((config(INJECTED_VMCONFIG).invocationCounterIncrement * step) << probLog);
            counters.writeInt(config(INJECTED_VMCONFIG).invocationCounterOffset, counterValue);
            if (freqLog >= 0) {
                int mask = notificationMask(freqLog, probLog, stepLog);
                if (probability(SLOW_PATH_PROBABILITY, (counterValue & (mask << config(INJECTED_VMCONFIG).invocationCounterShift)) == 0)) {
                    methodInvocationEvent(HotSpotBackend.INVOCATION_EVENT, counters);
                }
            }
        }
    }

    @NodeIntrinsic(ForeignCallNode.class)
    public static native void methodBackedgeEvent(@ConstantNodeParameter ForeignCallDescriptor descriptor, MethodCountersPointer counters, int bci, int targetBci);

    @Snippet
    public static void profileBackedgeWithProbability(MethodCountersPointer counters, int random, int step, int stepLog, @ConstantParameter int freqLog, @ConstantParameter int probLog, int bci,
                    int targetBci) {
        if (probability(1.0 / (1 << probLog), shouldProfile(probLog, random))) {
            int counterValue = counters.readInt(config(INJECTED_VMCONFIG).backedgeCounterOffset) + ((config(INJECTED_VMCONFIG).invocationCounterIncrement * step) << probLog);
            counters.writeInt(config(INJECTED_VMCONFIG).backedgeCounterOffset, counterValue);
            int mask = notificationMask(freqLog, probLog, stepLog);
            if (probability(SLOW_PATH_PROBABILITY, (counterValue & (mask << config(INJECTED_VMCONFIG).invocationCounterShift)) == 0)) {
                methodBackedgeEvent(HotSpotBackend.BACKEDGE_EVENT, counters, bci, targetBci);
            }
        }
    }

    @Snippet
    public static void profileConditionalBackedgeWithProbability(MethodCountersPointer counters, int random, int step, int stepLog, @ConstantParameter int freqLog,
                    @ConstantParameter int probLog, boolean branchCondition,
                    int bci, int targetBci) {
        if (branchCondition) {
            profileBackedgeWithProbability(counters, random, step, stepLog, freqLog, probLog, bci, targetBci);
        }
    }

    public static class Templates extends AbstractTemplates {
        private final SnippetInfo profileMethodEntryWithProbability = snippet(ProbabilisticProfileSnippets.class, "profileMethodEntryWithProbability");
        private final SnippetInfo profileBackedgeWithProbability = snippet(ProbabilisticProfileSnippets.class, "profileBackedgeWithProbability");
        private final SnippetInfo profileConditionalBackedgeWithProbability = snippet(ProbabilisticProfileSnippets.class, "profileConditionalBackedgeWithProbability");

        public Templates(OptionValues options, Iterable<DebugHandlersFactory> factories, HotSpotProviders providers, TargetDescription target) {
            super(options, factories, providers, providers.getSnippetReflection(), target);
        }

        public void lower(ProfileNode profileNode, LoweringTool tool) {
            assert profileNode.getRandom() != null;

            StructuredGraph graph = profileNode.graph();
            LoadMethodCountersNode counters = graph.unique(new LoadMethodCountersNode(profileNode.getProfiledMethod()));
            ConstantNode step = ConstantNode.forInt(profileNode.getStep(), graph);
            ConstantNode stepLog = ConstantNode.forInt(CodeUtil.log2(profileNode.getStep()), graph);

            if (profileNode instanceof ProfileBranchNode) {
                // Backedge event
                ProfileBranchNode profileBranchNode = (ProfileBranchNode) profileNode;
                SnippetInfo snippet = profileBranchNode.hasCondition() ? profileConditionalBackedgeWithProbability : profileBackedgeWithProbability;
                Arguments args = new Arguments(snippet, graph.getGuardsStage(), tool.getLoweringStage());
                ConstantNode bci = ConstantNode.forInt(profileBranchNode.bci(), graph);
                ConstantNode targetBci = ConstantNode.forInt(profileBranchNode.targetBci(), graph);

                args.add("counters", counters);
                args.add("random", profileBranchNode.getRandom());
                args.add("step", step);
                args.add("stepLog", stepLog);
                args.addConst("freqLog", profileBranchNode.getNotificationFreqLog());
                args.addConst("probLog", profileBranchNode.getProbabilityLog());
                if (profileBranchNode.hasCondition()) {
                    args.add("branchCondition", profileBranchNode.branchCondition());
                }
                args.add("bci", bci);
                args.add("targetBci", targetBci);

                SnippetTemplate template = template(profileNode, args);
                template.instantiate(providers.getMetaAccess(), profileNode, DEFAULT_REPLACER, args);
            } else if (profileNode instanceof ProfileInvokeNode) {
                ProfileInvokeNode profileInvokeNode = (ProfileInvokeNode) profileNode;
                // Method invocation event
                Arguments args = new Arguments(profileMethodEntryWithProbability, graph.getGuardsStage(), tool.getLoweringStage());

                args.add("counters", counters);
                args.add("random", profileInvokeNode.getRandom());
                args.add("step", step);
                args.add("stepLog", stepLog);
                args.addConst("freqLog", profileInvokeNode.getNotificationFreqLog());
                args.addConst("probLog", profileInvokeNode.getProbabilityLog());
                SnippetTemplate template = template(profileNode, args);
                template.instantiate(providers.getMetaAccess(), profileNode, DEFAULT_REPLACER, args);
            } else {
                throw new GraalError("Unsupported profile node type: " + profileNode);
            }

            assert profileNode.hasNoUsages();
            if (!profileNode.isDeleted()) {
                GraphUtil.killWithUnusedFloatingInputs(profileNode);
            }
        }
    }
}
