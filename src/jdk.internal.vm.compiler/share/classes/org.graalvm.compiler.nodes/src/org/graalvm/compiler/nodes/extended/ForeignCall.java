/*
 * Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.nodes.extended;

import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodes.DeoptBciSupplier;
import org.graalvm.compiler.nodes.DeoptimizingNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.StateSplit;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.memory.MultiMemoryKill;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.internal.vm.compiler.word.LocationIdentity;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.meta.Value;

/**
 * Interface for all nodes implementing a {@linkplain ForeignCallDescriptor foreign} call. Provides
 * implementations for several operations concerning foreign calls.
 */
public interface ForeignCall extends LIRLowerable, DeoptimizingNode.DeoptDuring, MultiMemoryKill, StateSplit, DeoptBciSupplier {

    NodeInputList<ValueNode> getArguments();

    @Override
    void setBci(int bci);

    ForeignCallDescriptor getDescriptor();

    @Override
    default LocationIdentity[] getKilledLocationIdentities() {
        return getDescriptor().getKilledLocations();
    }

    default Value[] operands(NodeLIRBuilderTool gen) {
        Value[] operands = new Value[getArguments().size()];
        for (int i = 0; i < operands.length; i++) {
            operands[i] = gen.operand(getArguments().get(i));
        }
        return operands;
    }

    @Override
    default void computeStateDuring(FrameState currentStateAfter) {
        assert stateDuring() == null;
        FrameState newStateDuring;
        if ((currentStateAfter.stackSize() > 0 && currentStateAfter.stackAt(currentStateAfter.stackSize() - 1) == this) ||
                        (currentStateAfter.stackSize() > 1 && currentStateAfter.stackAt(currentStateAfter.stackSize() - 2) == this)) {
            // The result of this call is on the top of stack, so roll back to the previous bci.
            assert bci() != BytecodeFrame.UNKNOWN_BCI : this;
            newStateDuring = currentStateAfter.duplicateModified(currentStateAfter.graph(), bci(), false, true, this.asNode().getStackKind(), null, null);
        } else {
            newStateDuring = currentStateAfter;
        }
        setStateDuring(newStateDuring);
    }

    @Override
    default boolean canDeoptimize() {
        return getDescriptor().canDeoptimize();
    }

    default boolean isGuaranteedSafepoint() {
        return getDescriptor().isGuaranteedSafepoint();
    }

    @Override
    default void generate(NodeLIRBuilderTool gen) {
        gen.emitForeignCall(this);
    }
}
