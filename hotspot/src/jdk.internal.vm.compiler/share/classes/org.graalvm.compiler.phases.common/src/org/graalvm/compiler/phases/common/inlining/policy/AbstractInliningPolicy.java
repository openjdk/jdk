/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.phases.common.inlining.InliningPhase.Options.AlwaysInlineIntrinsics;

import java.util.Map;

import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.spi.Replacements;
import org.graalvm.compiler.phases.common.inlining.InliningUtil;
import org.graalvm.compiler.phases.common.inlining.info.InlineInfo;
import org.graalvm.compiler.phases.common.inlining.info.elem.Inlineable;

import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public abstract class AbstractInliningPolicy implements InliningPolicy {
    public static final float RelevanceCapForInlining = 1.0f;
    public static final float CapInheritedRelevance = 1.0f;
    protected final Map<Invoke, Double> hints;

    public AbstractInliningPolicy(Map<Invoke, Double> hints) {
        this.hints = hints;
    }

    protected double computeMaximumSize(double relevance, int configuredMaximum) {
        double inlineRatio = Math.min(RelevanceCapForInlining, relevance);
        return configuredMaximum * inlineRatio;
    }

    protected double getInliningBonus(InlineInfo info) {
        if (hints != null && hints.containsKey(info.invoke())) {
            return hints.get(info.invoke());
        }
        return 1;
    }

    protected boolean isIntrinsic(Replacements replacements, InlineInfo info) {
        if (AlwaysInlineIntrinsics.getValue()) {
            return onlyIntrinsics(replacements, info);
        } else {
            return onlyForcedIntrinsics(replacements, info);
        }
    }

    private static boolean onlyIntrinsics(Replacements replacements, InlineInfo info) {
        for (int i = 0; i < info.numberOfMethods(); i++) {
            if (!InliningUtil.canIntrinsify(replacements, info.methodAt(i), info.invoke().bci())) {
                return false;
            }
        }
        return true;
    }

    private static boolean onlyForcedIntrinsics(Replacements replacements, InlineInfo info) {
        for (int i = 0; i < info.numberOfMethods(); i++) {
            if (!InliningUtil.canIntrinsify(replacements, info.methodAt(i), info.invoke().bci())) {
                return false;
            }
        }
        return true;
    }

    protected static int previousLowLevelGraphSize(InlineInfo info) {
        int size = 0;
        for (int i = 0; i < info.numberOfMethods(); i++) {
            ResolvedJavaMethod m = info.methodAt(i);
            ProfilingInfo profile = info.graph().getProfilingInfo(m);
            int compiledGraphSize = profile.getCompilerIRSize(StructuredGraph.class);
            if (compiledGraphSize > 0) {
                size += compiledGraphSize;
            }
        }
        return size;
    }

    protected static double determineInvokeProbability(InlineInfo info) {
        double invokeProbability = 0;
        for (int i = 0; i < info.numberOfMethods(); i++) {
            Inlineable callee = info.inlineableElementAt(i);
            Iterable<Invoke> invokes = callee.getInvokes();
            if (invokes.iterator().hasNext()) {
                for (Invoke invoke : invokes) {
                    invokeProbability += callee.getProbability(invoke);
                }
            }
        }
        return invokeProbability;
    }
}
