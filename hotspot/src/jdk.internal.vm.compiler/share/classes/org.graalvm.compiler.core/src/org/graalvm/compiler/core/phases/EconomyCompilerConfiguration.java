/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.lir.phases.AllocationPhase.AllocationContext;
import org.graalvm.compiler.lir.phases.EconomyAllocationStage;
import org.graalvm.compiler.lir.phases.EconomyPostAllocationOptimizationStage;
import org.graalvm.compiler.lir.phases.EconomyPreAllocationOptimizationStage;
import org.graalvm.compiler.lir.phases.LIRPhaseSuite;
import org.graalvm.compiler.lir.phases.PostAllocationOptimizationPhase.PostAllocationOptimizationContext;
import org.graalvm.compiler.lir.phases.PreAllocationOptimizationPhase.PreAllocationOptimizationContext;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.tiers.CompilerConfiguration;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.tiers.LowTierContext;
import org.graalvm.compiler.phases.tiers.MidTierContext;

public class EconomyCompilerConfiguration implements CompilerConfiguration {

    @Override
    public PhaseSuite<HighTierContext> createHighTier(OptionValues options) {
        return new EconomyHighTier(options);
    }

    @Override
    public PhaseSuite<MidTierContext> createMidTier(OptionValues options) {
        return new EconomyMidTier(options);
    }

    @Override
    public PhaseSuite<LowTierContext> createLowTier(OptionValues options) {
        return new EconomyLowTier(options);
    }

    @Override
    public LIRPhaseSuite<PreAllocationOptimizationContext> createPreAllocationOptimizationStage(OptionValues options) {
        return new EconomyPreAllocationOptimizationStage();
    }

    @Override
    public LIRPhaseSuite<AllocationContext> createAllocationStage(OptionValues options) {
        return new EconomyAllocationStage(options);
    }

    @Override
    public LIRPhaseSuite<PostAllocationOptimizationContext> createPostAllocationOptimizationStage(OptionValues options) {
        return new EconomyPostAllocationOptimizationStage();
    }

}
