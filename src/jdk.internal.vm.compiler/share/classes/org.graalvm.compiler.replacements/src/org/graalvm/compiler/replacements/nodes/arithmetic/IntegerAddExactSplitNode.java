/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.replacements.nodes.arithmetic;

import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.SimplifierTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.Value;

@NodeInfo
public final class IntegerAddExactSplitNode extends IntegerExactArithmeticSplitNode {
    public static final NodeClass<IntegerAddExactSplitNode> TYPE = NodeClass.create(IntegerAddExactSplitNode.class);

    public IntegerAddExactSplitNode(Stamp stamp, ValueNode x, ValueNode y, AbstractBeginNode next, AbstractBeginNode overflowSuccessor) {
        super(TYPE, stamp, x, y, next, overflowSuccessor);
    }

    @Override
    protected Value generateArithmetic(NodeLIRBuilderTool gen) {
        return gen.getLIRGeneratorTool().getArithmetic().emitAdd(gen.operand(getX()), gen.operand(getY()), true);
    }

    @Override
    public void simplify(SimplifierTool tool) {
        NodeView view = NodeView.from(tool);
        if (!IntegerStamp.addCanOverflow((IntegerStamp) x.stamp(view), (IntegerStamp) y.stamp(view))) {
            tool.deleteBranch(overflowSuccessor);
            tool.addToWorkList(next);
            AddNode replacement = graph().unique(new AddNode(x, y));
            graph().replaceSplitWithFloating(this, replacement, next);
            tool.addToWorkList(replacement);
        }
    }
}
