/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.nodes;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_1;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_1;

import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.UnaryNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

@NodeInfo(cycles = CYCLES_1, size = SIZE_1)
public final class ReverseBytesNode extends UnaryNode implements LIRLowerable {

    public static final NodeClass<ReverseBytesNode> TYPE = NodeClass.create(ReverseBytesNode.class);

    public ReverseBytesNode(ValueNode value) {
        super(TYPE, StampFactory.forKind(value.getStackKind()), value);
        assert getStackKind() == JavaKind.Int || getStackKind() == JavaKind.Long;
    }

    @Override
    public Stamp foldStamp(Stamp newStamp) {
        assert newStamp.isCompatible(getValue().stamp());
        IntegerStamp valueStamp = (IntegerStamp) newStamp;
        if (getStackKind() == JavaKind.Int) {
            long mask = CodeUtil.mask(JavaKind.Int.getBitCount());
            return IntegerStamp.stampForMask(valueStamp.getBits(), Integer.reverse((int) valueStamp.downMask()) & mask, Integer.reverse((int) valueStamp.upMask()) & mask);
        } else if (getStackKind() == JavaKind.Long) {
            return IntegerStamp.stampForMask(valueStamp.getBits(), Long.reverse(valueStamp.downMask()), Long.reverse(valueStamp.upMask()));
        } else {
            return stamp();
        }
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        if (forValue.isConstant()) {
            JavaConstant c = forValue.asJavaConstant();
            long reversed = getStackKind() == JavaKind.Int ? Integer.reverseBytes(c.asInt()) : Long.reverseBytes(c.asLong());
            return ConstantNode.forIntegerKind(getStackKind(), reversed);
        }
        return this;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        Value result = gen.getLIRGeneratorTool().emitByteSwap(gen.operand(getValue()));
        gen.setResult(this, result);
    }
}
