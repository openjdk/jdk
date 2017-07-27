/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.java;

import static org.graalvm.compiler.nodeinfo.InputType.State;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_20;
import static org.graalvm.compiler.nodes.java.ForeignCallDescriptors.REGISTER_FINALIZER;

import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.AbstractStateSplit;
import org.graalvm.compiler.nodes.DeoptimizingNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.nodes.spi.Virtualizable;
import org.graalvm.compiler.nodes.spi.VirtualizerTool;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;

import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.Assumptions.AssumptionResult;

/**
 * This node is used to perform the finalizer registration at the end of the java.lang.Object
 * constructor.
 */
// @formatter:off
@NodeInfo(cycles = CYCLES_UNKNOWN,
          cyclesRationale = "We cannot estimate the time of a runtime call.",
          size = SIZE_20,
          sizeRationale = "Rough estimation for register handling & calling")
// @formatter:on
public final class RegisterFinalizerNode extends AbstractStateSplit implements Canonicalizable.Unary<ValueNode>, LIRLowerable, Virtualizable, DeoptimizingNode.DeoptAfter {

    public static final NodeClass<RegisterFinalizerNode> TYPE = NodeClass.create(RegisterFinalizerNode.class);
    @OptionalInput(State) FrameState deoptState;
    @Input ValueNode value;

    public RegisterFinalizerNode(ValueNode value) {
        super(TYPE, StampFactory.forVoid());
        this.value = value;
    }

    @Override
    public ValueNode getValue() {
        return value;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        // Note that an unconditional call to the runtime routine is made without
        // checking that the object actually has a finalizer. This requires the
        // runtime routine to do the check.
        ForeignCallLinkage linkage = gen.getLIRGeneratorTool().getForeignCalls().lookupForeignCall(REGISTER_FINALIZER);
        gen.getLIRGeneratorTool().emitForeignCall(linkage, gen.state(this), gen.operand(getValue()));
    }

    /**
     * Determines if the compiler should emit code to test whether a given object has a finalizer
     * that must be registered with the runtime upon object initialization.
     */
    public static boolean mayHaveFinalizer(ValueNode object, Assumptions assumptions) {
        ObjectStamp objectStamp = (ObjectStamp) object.stamp();
        if (objectStamp.isExactType()) {
            return objectStamp.type().hasFinalizer();
        } else if (objectStamp.type() != null) {
            AssumptionResult<Boolean> result = objectStamp.type().hasFinalizableSubclass();
            if (result.canRecordTo(assumptions)) {
                result.recordTo(assumptions);
                return result.getResult();
            }
        }
        return true;
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        if (!(forValue.stamp() instanceof ObjectStamp)) {
            return this;
        }
        if (!mayHaveFinalizer(forValue, graph().getAssumptions())) {
            return null;
        }

        return this;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode alias = tool.getAlias(getValue());
        if (alias instanceof VirtualObjectNode && !((VirtualObjectNode) alias).type().hasFinalizer()) {
            tool.delete();
        }
    }

    @Override
    public boolean canDeoptimize() {
        return true;
    }

    @NodeIntrinsic
    public static native void register(Object thisObj);
}
