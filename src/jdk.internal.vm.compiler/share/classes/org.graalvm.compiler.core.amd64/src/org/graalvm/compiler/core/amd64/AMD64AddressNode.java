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

package org.graalvm.compiler.core.amd64;

import org.graalvm.compiler.asm.amd64.AMD64Address.Scale;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Simplifiable;
import org.graalvm.compiler.graph.spi.SimplifierTool;
import org.graalvm.compiler.lir.amd64.AMD64AddressValue;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

/**
 * Represents an address of the form [base + index*scale + displacement]. Both base and index are
 * optional.
 */
@NodeInfo
public class AMD64AddressNode extends AddressNode implements Simplifiable, LIRLowerable {

    public static final NodeClass<AMD64AddressNode> TYPE = NodeClass.create(AMD64AddressNode.class);

    @OptionalInput private ValueNode base;

    @OptionalInput private ValueNode index;
    private Scale scale;

    private int displacement;

    public AMD64AddressNode(ValueNode base) {
        this(base, null);
    }

    public AMD64AddressNode(ValueNode base, ValueNode index) {
        super(TYPE);
        this.base = base;
        this.index = index;
        this.scale = Scale.Times1;
    }

    public void canonicalizeIndex(SimplifierTool tool) {
        if (index instanceof AddNode) {
            AddNode add = (AddNode) index;
            ValueNode valX = add.getX();
            if (valX instanceof PhiNode) {
                PhiNode phi = (PhiNode) valX;
                if (phi.merge() instanceof LoopBeginNode) {
                    LoopBeginNode loopNode = (LoopBeginNode) phi.merge();
                    if (!loopNode.isSimpleLoop()) {
                        ValueNode valY = add.getY();
                        if (valY instanceof ConstantNode) {
                            int addBy = valY.asJavaConstant().asInt();
                            displacement = displacement + scale.value * addBy;
                            replaceFirstInput(index, phi);
                            tool.addToWorkList(index);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();

        AllocatableValue baseValue = base == null ? Value.ILLEGAL : tool.asAllocatable(gen.operand(base));
        AllocatableValue indexValue = index == null ? Value.ILLEGAL : tool.asAllocatable(gen.operand(index));

        AllocatableValue baseReference = LIRKind.derivedBaseFromValue(baseValue);
        AllocatableValue indexReference;
        if (index == null) {
            indexReference = null;
        } else if (scale.equals(Scale.Times1)) {
            indexReference = LIRKind.derivedBaseFromValue(indexValue);
        } else {
            if (LIRKind.isValue(indexValue)) {
                indexReference = null;
            } else {
                indexReference = Value.ILLEGAL;
            }
        }

        LIRKind kind = LIRKind.combineDerived(tool.getLIRKind(stamp(NodeView.DEFAULT)), baseReference, indexReference);
        gen.setResult(this, new AMD64AddressValue(kind, baseValue, indexValue, scale, displacement));
    }

    @Override
    public ValueNode getBase() {
        return base;
    }

    public void setBase(ValueNode base) {
        // allow modification before inserting into the graph
        if (isAlive()) {
            updateUsages(this.base, base);
        }
        this.base = base;
    }

    @Override
    public ValueNode getIndex() {
        return index;
    }

    public void setIndex(ValueNode index) {
        // allow modification before inserting into the graph
        if (isAlive()) {
            updateUsages(this.index, index);
        }
        this.index = index;
    }

    public Scale getScale() {
        return scale;
    }

    public void setScale(Scale scale) {
        this.scale = scale;
    }

    public int getDisplacement() {
        return displacement;
    }

    public void setDisplacement(int displacement) {
        this.displacement = displacement;
    }

    @Override
    public long getMaxConstantDisplacement() {
        return displacement;
    }

    @Override
    public void simplify(SimplifierTool tool) {
        canonicalizeIndex(tool);
    }
}
