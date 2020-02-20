/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
import static org.graalvm.compiler.core.common.GraalOptions.OptDeoptimizationGrouping;
import static org.graalvm.compiler.core.common.GraalOptions.OptFloatingReads;
import static org.graalvm.compiler.core.common.GraalOptions.PartialUnroll;
import static org.graalvm.compiler.core.common.GraalOptions.ReassociateInvariants;
import static org.graalvm.compiler.core.common.GraalOptions.VerifyHeapAtReturn;
import static org.graalvm.compiler.core.common.SpeculativeExecutionAttacksMitigations.GuardTargets;
import static org.graalvm.compiler.core.common.SpeculativeExecutionAttacksMitigations.NonDeoptGuardTargets;
import static org.graalvm.compiler.core.common.SpeculativeExecutionAttacksMitigations.Options.MitigateSpeculativeExecutionAttacks;

import org.graalvm.compiler.loop.DefaultLoopPolicies;
import org.graalvm.compiler.loop.LoopPolicies;
import org.graalvm.compiler.loop.phases.LoopPartialUnrollPhase;
import org.graalvm.compiler.loop.phases.LoopSafepointEliminationPhase;
import org.graalvm.compiler.loop.phases.ReassociateInvariantPhase;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.DeoptimizationGroupingPhase;
import org.graalvm.compiler.phases.common.FloatingReadPhase;
import org.graalvm.compiler.phases.common.FrameStateAssignmentPhase;
import org.graalvm.compiler.phases.common.GuardLoweringPhase;
import org.graalvm.compiler.phases.common.IncrementalCanonicalizerPhase;
import org.graalvm.compiler.phases.common.InsertGuardFencesPhase;
import org.graalvm.compiler.phases.common.IterativeConditionalEliminationPhase;
import org.graalvm.compiler.phases.common.LockEliminationPhase;
import org.graalvm.compiler.phases.common.LoopSafepointInsertionPhase;
import org.graalvm.compiler.phases.common.LoweringPhase;
import org.graalvm.compiler.phases.common.OptimizeDivPhase;
import org.graalvm.compiler.phases.common.RemoveValueProxyPhase;
import org.graalvm.compiler.phases.common.VerifyHeapAtReturnPhase;
import org.graalvm.compiler.phases.common.WriteBarrierAdditionPhase;
import org.graalvm.compiler.phases.tiers.MidTierContext;

public class MidTier extends BaseTier<MidTierContext> {

    public MidTier(OptionValues options) {
        CanonicalizerPhase canonicalizer = createCanonicalizerPhase(options);

        appendPhase(new LockEliminationPhase());

        if (OptFloatingReads.getValue(options)) {
            appendPhase(new IncrementalCanonicalizerPhase<>(canonicalizer, new FloatingReadPhase()));
        }

        if (ConditionalElimination.getValue(options)) {
            appendPhase(new IterativeConditionalEliminationPhase(canonicalizer, true));
        }

        appendPhase(new LoopSafepointEliminationPhase());

        appendPhase(new GuardLoweringPhase());

        if (MitigateSpeculativeExecutionAttacks.getValue(options) == GuardTargets || MitigateSpeculativeExecutionAttacks.getValue(options) == NonDeoptGuardTargets) {
            appendPhase(new InsertGuardFencesPhase());
        }

        if (VerifyHeapAtReturn.getValue(options)) {
            appendPhase(new VerifyHeapAtReturnPhase());
        }

        appendPhase(new IncrementalCanonicalizerPhase<>(canonicalizer, new RemoveValueProxyPhase()));

        appendPhase(new LoopSafepointInsertionPhase());

        appendPhase(new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.MID_TIER));

        appendPhase(new OptimizeDivPhase());

        appendPhase(new FrameStateAssignmentPhase());

        if (PartialUnroll.getValue(options)) {
            LoopPolicies loopPolicies = createLoopPolicies(options);
            appendPhase(new LoopPartialUnrollPhase(loopPolicies, canonicalizer));
        }

        if (ReassociateInvariants.getValue(options)) {
            appendPhase(new ReassociateInvariantPhase());
        }

        if (OptDeoptimizationGrouping.getValue(options)) {
            appendPhase(new DeoptimizationGroupingPhase());
        }

        appendPhase(canonicalizer);

        appendPhase(new WriteBarrierAdditionPhase());
    }

    @Override
    public LoopPolicies createLoopPolicies(OptionValues options) {
        return new DefaultLoopPolicies();
    }
}
