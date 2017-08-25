/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_4;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_4;

import org.graalvm.compiler.core.common.PermanentBailoutException;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.hotspot.HotSpotLIRGenerator;
import org.graalvm.compiler.hotspot.meta.HotSpotConstantLoadAction;
import org.graalvm.compiler.hotspot.nodes.type.MethodCountersPointerStamp;
import org.graalvm.compiler.hotspot.word.MethodCountersPointer;
import org.graalvm.compiler.hotspot.word.MethodPointer;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.nodes.util.GraphUtil;

import jdk.vm.ci.hotspot.HotSpotMetaspaceConstant;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.Value;

@NodeInfo(cycles = CYCLES_4, size = SIZE_4)
public class LoadMethodCountersIndirectlyNode extends FloatingNode implements Canonicalizable, LIRLowerable {

    public static final NodeClass<LoadMethodCountersIndirectlyNode> TYPE = NodeClass.create(LoadMethodCountersIndirectlyNode.class);

    @OptionalInput protected ValueNode value;
    protected Constant constant;

    public LoadMethodCountersIndirectlyNode(ValueNode value) {
        super(TYPE, MethodCountersPointerStamp.methodCounters());
        this.value = value;
        this.constant = null;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (value != null) {
            constant = GraphUtil.foldIfConstantAndRemove(this, value);
        }
        return this;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        assert constant != null : "Expected the value to fold: " + value;
        Value result;
        if (constant instanceof HotSpotMetaspaceConstant) {
            result = ((HotSpotLIRGenerator) gen.getLIRGeneratorTool()).emitLoadMetaspaceAddress(constant, HotSpotConstantLoadAction.LOAD_COUNTERS);
        } else {
            throw new PermanentBailoutException("Unsupported constant type: " + constant);
        }
        gen.setResult(this, result);
    }

    @NodeIntrinsic
    public static native MethodCountersPointer loadMethodCounters(MethodPointer methodPointer);
}
