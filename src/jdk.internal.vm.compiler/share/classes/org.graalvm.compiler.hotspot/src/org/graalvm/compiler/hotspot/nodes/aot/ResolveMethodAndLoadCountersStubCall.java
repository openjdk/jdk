/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.nodes.aot;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_16;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.hotspot.HotSpotLIRGenerator;
import org.graalvm.compiler.hotspot.nodes.DeoptimizingStubCall;
import org.graalvm.compiler.hotspot.nodes.type.MethodCountersPointerStamp;
import org.graalvm.compiler.hotspot.word.KlassPointer;
import org.graalvm.compiler.hotspot.word.MethodCountersPointer;
import org.graalvm.compiler.hotspot.word.MethodPointer;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.word.Word;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.Value;

/**
 * A call to the VM via a regular stub.
 */
@NodeInfo(cycles = CYCLES_UNKNOWN, size = SIZE_16)
public class ResolveMethodAndLoadCountersStubCall extends DeoptimizingStubCall implements Canonicalizable, LIRLowerable {
    public static final NodeClass<ResolveMethodAndLoadCountersStubCall> TYPE = NodeClass.create(ResolveMethodAndLoadCountersStubCall.class);

    @OptionalInput protected ValueNode method;
    @Input protected ValueNode klassHint;
    @Input protected ValueNode methodDescription;
    protected Constant methodConstant;

    public ResolveMethodAndLoadCountersStubCall(ValueNode method, ValueNode klassHint, ValueNode methodDescription) {
        super(TYPE, MethodCountersPointerStamp.methodCountersNonNull());
        this.klassHint = klassHint;
        this.method = method;
        this.methodDescription = methodDescription;
    }

    @NodeIntrinsic
    public static native MethodCountersPointer resolveMethodAndLoadCounters(MethodPointer method, KlassPointer klassHint, Word methodDescription);

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (method != null) {
            methodConstant = GraphUtil.foldIfConstantAndRemove(this, method);
        }
        return this;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        assert methodConstant != null : "Expected method to fold: " + method;

        Value methodDescriptionValue = gen.operand(methodDescription);
        Value klassHintValue = gen.operand(klassHint);
        LIRFrameState fs = gen.state(this);
        assert fs != null : "The stateAfter is null";

        Value result = ((HotSpotLIRGenerator) gen.getLIRGeneratorTool()).emitResolveMethodAndLoadCounters(methodConstant, klassHintValue, methodDescriptionValue, fs);

        gen.setResult(this, result);
    }

}
