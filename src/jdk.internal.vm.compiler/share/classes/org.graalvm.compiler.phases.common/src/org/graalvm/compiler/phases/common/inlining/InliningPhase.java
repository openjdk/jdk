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


package org.graalvm.compiler.phases.common.inlining;

import java.util.LinkedList;
import java.util.Map;

import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.phases.common.AbstractInliningPhase;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.inlining.policy.GreedyInliningPolicy;
import org.graalvm.compiler.phases.common.inlining.policy.InliningPolicy;
import org.graalvm.compiler.phases.common.inlining.walker.InliningData;
import org.graalvm.compiler.phases.tiers.HighTierContext;

public class InliningPhase extends AbstractInliningPhase {

    public static class Options {

        @Option(help = "Unconditionally inline intrinsics", type = OptionType.Debug)//
        public static final OptionKey<Boolean> AlwaysInlineIntrinsics = new OptionKey<>(false);

        /**
         * This is a defensive measure against known pathologies of the inliner where the breadth of
         * the inlining call tree exploration can be wide enough to prevent inlining from completing
         * in reasonable time.
         */
        @Option(help = "Per-compilation method inlining exploration limit before giving up (use 0 to disable)", type = OptionType.Debug)//
        public static final OptionKey<Integer> MethodInlineBailoutLimit = new OptionKey<>(5000);
    }

    private final InliningPolicy inliningPolicy;
    private final CanonicalizerPhase canonicalizer;
    private LinkedList<Invoke> rootInvokes = null;

    private int maxMethodPerInlining = Integer.MAX_VALUE;

    public InliningPhase(CanonicalizerPhase canonicalizer) {
        this(new GreedyInliningPolicy(null), canonicalizer);
    }

    public InliningPhase(Map<Invoke, Double> hints, CanonicalizerPhase canonicalizer) {
        this(new GreedyInliningPolicy(hints), canonicalizer);
    }

    public InliningPhase(InliningPolicy policy, CanonicalizerPhase canonicalizer) {
        this.inliningPolicy = policy;
        this.canonicalizer = canonicalizer;
    }

    public CanonicalizerPhase getCanonicalizer() {
        return canonicalizer;
    }

    @Override
    public float codeSizeIncrease() {
        return 10_000f;
    }

    public void setMaxMethodsPerInlining(int max) {
        maxMethodPerInlining = max;
    }

    public void setRootInvokes(LinkedList<Invoke> rootInvokes) {
        this.rootInvokes = rootInvokes;
    }

    /**
     *
     * This method sets in motion the inlining machinery.
     *
     * @see InliningData
     * @see InliningData#moveForward()
     *
     */
    @Override
    protected void run(final StructuredGraph graph, final HighTierContext context) {
        final InliningData data = new InliningData(graph, context, maxMethodPerInlining, canonicalizer, inliningPolicy, rootInvokes);

        int count = 0;
        assert data.repOK();
        int limit = Options.MethodInlineBailoutLimit.getValue(graph.getOptions());
        while (data.hasUnprocessedGraphs()) {
            boolean wasInlined = data.moveForward();
            assert data.repOK();
            count++;
            if (!wasInlined) {
                if (limit > 0 && count == limit) {
                    // Limit the amount of exploration which is done
                    break;
                }
            }
        }

        assert data.inliningDepth() == 0 || count == limit;
        assert data.graphCount() == 0 || count == limit;
    }

}
