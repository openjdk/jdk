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


package org.graalvm.compiler.nodes.spi;

import org.graalvm.compiler.core.common.spi.ConstantFieldProvider;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.extended.AnchoringNode;
import org.graalvm.compiler.nodes.extended.GuardingNode;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.SpeculationLog.Speculation;

public interface LoweringTool {

    MetaAccessProvider getMetaAccess();

    LoweringProvider getLowerer();

    ConstantReflectionProvider getConstantReflection();

    ConstantFieldProvider getConstantFieldProvider();

    Replacements getReplacements();

    StampProvider getStampProvider();

    GuardingNode createGuard(FixedNode before, LogicNode condition, DeoptimizationReason deoptReason, DeoptimizationAction action);

    GuardingNode createGuard(FixedNode before, LogicNode condition, DeoptimizationReason deoptReason, DeoptimizationAction action, Speculation speculation, boolean negated,
                    NodeSourcePosition noDeoptSuccessorPosition);

    /**
     * Gets the closest fixed node preceding the node currently being lowered.
     */
    FixedWithNextNode lastFixedNode();

    AnchoringNode getCurrentGuardAnchor();

    /**
     * Marker interface lowering stages.
     */
    interface LoweringStage {
    }

    /**
     * The lowering stages used in a standard Graal phase plan. Lowering is called 3 times, during
     * every tier of compilation.
     */
    enum StandardLoweringStage implements LoweringStage {
        HIGH_TIER,
        MID_TIER,
        LOW_TIER
    }

    /**
     * Returns current lowering stage.
     */
    LoweringStage getLoweringStage();
}
