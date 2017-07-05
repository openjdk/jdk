/*
 * Copyright (c) 2011, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collection;
import java.util.List;

import org.graalvm.compiler.core.common.cfg.BlockMap;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractEndNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.BreakpointNode;
import org.graalvm.compiler.nodes.DeoptimizingNode;
import org.graalvm.compiler.nodes.FullInfopointNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.LoopEndNode;
import org.graalvm.compiler.nodes.SafepointNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.ConditionalNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.extended.SwitchNode;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.meta.Value;

public interface NodeLIRBuilderTool extends NodeValueMap {

    // TODO (je) remove and move into the Node
    LIRFrameState state(DeoptimizingNode deopt);

    void emitIf(IfNode i);

    void emitConditional(ConditionalNode i);

    void emitSwitch(SwitchNode i);

    void emitInvoke(Invoke i);

    // Handling of block-end nodes still needs to be unified in the LIRGenerator.
    void visitMerge(AbstractMergeNode i);

    void visitEndNode(AbstractEndNode i);

    void visitLoopEnd(LoopEndNode i);

    // These methods define the contract a runtime specific backend must provide.

    void visitSafepointNode(SafepointNode i);

    void visitBreakpointNode(BreakpointNode i);

    void visitFullInfopointNode(FullInfopointNode i);

    void setSourcePosition(NodeSourcePosition position);

    LIRGeneratorTool getLIRGeneratorTool();

    void emitOverflowCheckBranch(AbstractBeginNode overflowSuccessor, AbstractBeginNode next, Stamp compareStamp, double probability);

    Value[] visitInvokeArguments(CallingConvention cc, Collection<ValueNode> arguments);

    void doBlock(Block block, StructuredGraph graph, BlockMap<List<Node>> blockMap);
}
