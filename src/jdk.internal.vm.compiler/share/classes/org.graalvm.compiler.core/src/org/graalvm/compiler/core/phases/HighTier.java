/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.phases;

import static org.graalvm.compiler.core.common.GraalOptions.ConditionalElimination;
import static org.graalvm.compiler.core.common.GraalOptions.FullUnroll;
import static org.graalvm.compiler.core.common.GraalOptions.ImmutableCode;
import static org.graalvm.compiler.core.common.GraalOptions.LoopPeeling;
import static org.graalvm.compiler.core.common.GraalOptions.LoopUnswitch;
import static org.graalvm.compiler.core.common.GraalOptions.OptConvertDeoptsToGuards;
import static org.graalvm.compiler.core.common.GraalOptions.OptLoopTransform;
import static org.graalvm.compiler.core.common.GraalOptions.OptReadElimination;
import static org.graalvm.compiler.core.common.GraalOptions.PartialEscapeAnalysis;
import static org.graalvm.compiler.phases.common.DeadCodeEliminationPhase.Optionality.Optional;

import org.graalvm.compiler.loop.DefaultLoopPolicies;
import org.graalvm.compiler.loop.LoopPolicies;
import org.graalvm.compiler.loop.phases.LoopFullUnrollPhase;
import org.graalvm.compiler.loop.phases.LoopPeelingPhase;
import org.graalvm.compiler.loop.phases.LoopUnswitchingPhase;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.ConvertDeoptimizeToGuardPhase;
import org.graalvm.compiler.phases.common.DeadCodeEliminationPhase;
import org.graalvm.compiler.phases.common.IncrementalCanonicalizerPhase;
import org.graalvm.compiler.phases.common.IterativeConditionalEliminationPhase;
import org.graalvm.compiler.phases.common.LoweringPhase;
import org.graalvm.compiler.phases.common.RemoveValueProxyPhase;
import org.graalvm.compiler.phases.common.inlining.InliningPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.virtual.phases.ea.EarlyReadEliminationPhase;
import org.graalvm.compiler.virtual.phases.ea.PartialEscapePhase;

public class HighTier extends PhaseSuite<HighTierContext> {

    public static class Options {

        // @formatter:off
        @Option(help = "Enable inlining", type = OptionType.Expert)
        public static final OptionKey<Boolean> Inline = new OptionKey<>(true);
        // @formatter:on
    }

    public HighTier(OptionValues options) {
        CanonicalizerPhase canonicalizer = new CanonicalizerPhase();
        if (ImmutableCode.getValue(options)) {
            canonicalizer.disableReadCanonicalization();
        }

        appendPhase(canonicalizer);

        if (Options.Inline.getValue(options)) {
            appendPhase(new InliningPhase(canonicalizer));
            appendPhase(new DeadCodeEliminationPhase(Optional));
        }

        if (OptConvertDeoptsToGuards.getValue(options)) {
            appendPhase(new IncrementalCanonicalizerPhase<>(canonicalizer, new ConvertDeoptimizeToGuardPhase()));
        }

        if (ConditionalElimination.getValue(options)) {
            appendPhase(new IterativeConditionalEliminationPhase(canonicalizer, false));
        }

        LoopPolicies loopPolicies = createLoopPolicies();
        if (FullUnroll.getValue(options)) {
            appendPhase(new LoopFullUnrollPhase(canonicalizer, loopPolicies));
        }

        if (OptLoopTransform.getValue(options)) {
            if (LoopPeeling.getValue(options)) {
                appendPhase(new LoopPeelingPhase(loopPolicies));
            }
            if (LoopUnswitch.getValue(options)) {
                appendPhase(new LoopUnswitchingPhase(loopPolicies));
            }
        }

        appendPhase(canonicalizer);

        if (PartialEscapeAnalysis.getValue(options)) {
            appendPhase(new PartialEscapePhase(true, canonicalizer, options));
        }

        if (OptReadElimination.getValue(options)) {
            appendPhase(new EarlyReadEliminationPhase(canonicalizer));
        }

        appendPhase(new RemoveValueProxyPhase());

        appendPhase(new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.HIGH_TIER));
    }

    public LoopPolicies createLoopPolicies() {
        return new DefaultLoopPolicies();
    }
}
