/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_1;

import org.graalvm.compiler.core.common.type.FloatStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.TernaryNode;
import org.graalvm.compiler.nodes.spi.ArithmeticLIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import org.graalvm.compiler.serviceprovider.GraalServices;

@NodeInfo(cycles = CYCLES_2, size = SIZE_1)
public final class FusedMultiplyAddNode extends TernaryNode implements ArithmeticLIRLowerable {

    public static final NodeClass<FusedMultiplyAddNode> TYPE = NodeClass.create(FusedMultiplyAddNode.class);

    public FusedMultiplyAddNode(ValueNode a, ValueNode b, ValueNode c) {
        super(TYPE, computeStamp(a.stamp(NodeView.DEFAULT), b.stamp(NodeView.DEFAULT), c.stamp(NodeView.DEFAULT)), a, b, c);
        assert a.getStackKind().isNumericFloat();
        assert b.getStackKind().isNumericFloat();
        assert c.getStackKind().isNumericFloat();
    }

    @Override
    public Stamp foldStamp(Stamp stampX, Stamp stampY, Stamp stampZ) {
        return computeStamp(stampX, stampY, stampZ);
    }

    private static Stamp computeStamp(Stamp stampX, Stamp stampY, Stamp stampZ) {
        Stamp m = FloatStamp.OPS.getMul().foldStamp(stampX, stampY);
        return FloatStamp.OPS.getAdd().foldStamp(m, stampZ);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode a, ValueNode b, ValueNode c) {
        if (a.isConstant() && b.isConstant() && c.isConstant()) {
            JavaConstant ca = a.asJavaConstant();
            JavaConstant cb = b.asJavaConstant();
            JavaConstant cc = c.asJavaConstant();

            ValueNode res;
            if (a.getStackKind() == JavaKind.Float) {
                res = ConstantNode.forFloat(GraalServices.fma(ca.asFloat(), cb.asFloat(), cc.asFloat()));
            } else {
                assert a.getStackKind() == JavaKind.Double;
                res = ConstantNode.forDouble(GraalServices.fma(ca.asDouble(), cb.asDouble(), cc.asDouble()));
            }
            return res;
        }
        return this;
    }

    @Override
    public void generate(NodeLIRBuilderTool builder, ArithmeticLIRGeneratorTool gen) {
        builder.setResult(this, gen.emitFusedMultiplyAdd(builder.operand(getX()), builder.operand(getY()), builder.operand(getZ())));
    }
}
