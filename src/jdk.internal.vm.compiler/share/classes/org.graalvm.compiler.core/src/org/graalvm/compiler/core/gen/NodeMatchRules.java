/*
 * Copyright (c) 2009, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.core.gen;

import org.graalvm.compiler.core.match.MatchableNode;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LabelRef;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.DeoptimizingNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.AndNode;
import org.graalvm.compiler.nodes.calc.FloatConvertNode;
import org.graalvm.compiler.nodes.calc.FloatEqualsNode;
import org.graalvm.compiler.nodes.calc.FloatLessThanNode;
import org.graalvm.compiler.nodes.calc.IntegerBelowNode;
import org.graalvm.compiler.nodes.calc.IntegerEqualsNode;
import org.graalvm.compiler.nodes.calc.IntegerLessThanNode;
import org.graalvm.compiler.nodes.calc.IntegerTestNode;
import org.graalvm.compiler.nodes.calc.LeftShiftNode;
import org.graalvm.compiler.nodes.calc.MulNode;
import org.graalvm.compiler.nodes.calc.NarrowNode;
import org.graalvm.compiler.nodes.calc.ObjectEqualsNode;
import org.graalvm.compiler.nodes.calc.OrNode;
import org.graalvm.compiler.nodes.calc.PointerEqualsNode;
import org.graalvm.compiler.nodes.calc.ReinterpretNode;
import org.graalvm.compiler.nodes.calc.SignExtendNode;
import org.graalvm.compiler.nodes.calc.SubNode;
import org.graalvm.compiler.nodes.calc.UnsignedRightShiftNode;
import org.graalvm.compiler.nodes.calc.XorNode;
import org.graalvm.compiler.nodes.calc.ZeroExtendNode;
import org.graalvm.compiler.nodes.java.LogicCompareAndSwapNode;
import org.graalvm.compiler.nodes.java.ValueCompareAndSwapNode;
import org.graalvm.compiler.nodes.memory.FloatingReadNode;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.WriteNode;

import jdk.vm.ci.meta.Value;

@MatchableNode(nodeClass = ConstantNode.class, shareable = true)
@MatchableNode(nodeClass = FloatConvertNode.class, inputs = {"value"})
@MatchableNode(nodeClass = FloatingReadNode.class, inputs = {"address"})
@MatchableNode(nodeClass = IfNode.class, inputs = {"condition"})
@MatchableNode(nodeClass = SubNode.class, inputs = {"x", "y"})
@MatchableNode(nodeClass = LeftShiftNode.class, inputs = {"x", "y"})
@MatchableNode(nodeClass = NarrowNode.class, inputs = {"value"})
@MatchableNode(nodeClass = ReadNode.class, inputs = {"address"})
@MatchableNode(nodeClass = ReinterpretNode.class, inputs = {"value"})
@MatchableNode(nodeClass = SignExtendNode.class, inputs = {"value"})
@MatchableNode(nodeClass = UnsignedRightShiftNode.class, inputs = {"x", "y"})
@MatchableNode(nodeClass = WriteNode.class, inputs = {"address", "value"})
@MatchableNode(nodeClass = ZeroExtendNode.class, inputs = {"value"})
@MatchableNode(nodeClass = AndNode.class, inputs = {"x", "y"}, commutative = true)
@MatchableNode(nodeClass = FloatEqualsNode.class, inputs = {"x", "y"}, commutative = true)
@MatchableNode(nodeClass = FloatLessThanNode.class, inputs = {"x", "y"}, commutative = true)
@MatchableNode(nodeClass = PointerEqualsNode.class, inputs = {"x", "y"}, commutative = true)
@MatchableNode(nodeClass = AddNode.class, inputs = {"x", "y"}, commutative = true)
@MatchableNode(nodeClass = IntegerBelowNode.class, inputs = {"x", "y"}, commutative = true)
@MatchableNode(nodeClass = IntegerEqualsNode.class, inputs = {"x", "y"}, commutative = true)
@MatchableNode(nodeClass = IntegerLessThanNode.class, inputs = {"x", "y"}, commutative = true)
@MatchableNode(nodeClass = MulNode.class, inputs = {"x", "y"}, commutative = true)
@MatchableNode(nodeClass = IntegerTestNode.class, inputs = {"x", "y"}, commutative = true)
@MatchableNode(nodeClass = ObjectEqualsNode.class, inputs = {"x", "y"}, commutative = true)
@MatchableNode(nodeClass = OrNode.class, inputs = {"x", "y"}, commutative = true)
@MatchableNode(nodeClass = XorNode.class, inputs = {"x", "y"}, commutative = true)
@MatchableNode(nodeClass = PiNode.class, inputs = {"object"})
@MatchableNode(nodeClass = LogicCompareAndSwapNode.class, inputs = {"address", "expectedValue", "newValue"})
@MatchableNode(nodeClass = ValueCompareAndSwapNode.class, inputs = {"address", "expectedValue", "newValue"})
public abstract class NodeMatchRules {

    NodeLIRBuilder lirBuilder;
    protected final LIRGeneratorTool gen;

    protected NodeMatchRules(LIRGeneratorTool gen) {
        this.gen = gen;
    }

    protected LIRGeneratorTool getLIRGeneratorTool() {
        return gen;
    }

    /*
     * For now we do not want to expose the full lirBuilder to subclasses, so we delegate the few
     * methods that are actually needed. If the list grows too long, exposing lirBuilder might be
     * the better approach.
     */

    protected final Value operand(Node node) {
        return lirBuilder.operand(node);
    }

    protected final LIRFrameState state(DeoptimizingNode deopt) {
        return lirBuilder.state(deopt);
    }

    protected final LabelRef getLIRBlock(FixedNode b) {
        return lirBuilder.getLIRBlock(b);
    }

    protected final void append(LIRInstruction op) {
        lirBuilder.append(op);
    }
}
