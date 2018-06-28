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


package org.graalvm.compiler.replacements.amd64;

import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_64;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValueNodeUtil;
import org.graalvm.compiler.nodes.memory.MemoryAccess;
import org.graalvm.compiler.nodes.memory.MemoryNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.internal.vm.compiler.word.LocationIdentity;
import jdk.internal.vm.compiler.word.Pointer;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

@NodeInfo(size = SIZE_64, cycles = NodeCycles.CYCLES_UNKNOWN)
public class AMD64StringIndexOfNode extends FixedWithNextNode implements LIRLowerable, MemoryAccess {
    public static final NodeClass<AMD64StringIndexOfNode> TYPE = NodeClass.create(AMD64StringIndexOfNode.class);

    @OptionalInput(InputType.Memory) protected MemoryNode lastLocationAccess;

    @Input protected NodeInputList<ValueNode> arguments;

    public AMD64StringIndexOfNode(ValueNode sourcePointer, ValueNode sourceCount, ValueNode targetPointer, ValueNode targetCount) {
        super(TYPE, StampFactory.forInteger(32));
        this.arguments = new NodeInputList<>(this, new ValueNode[]{sourcePointer, sourceCount, targetPointer, targetCount});
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return NamedLocationIdentity.getArrayLocation(JavaKind.Char);
    }

    ValueNode sourcePointer() {
        return arguments.get(0);
    }

    ValueNode sourceCount() {
        return arguments.get(1);
    }

    ValueNode targetPointer() {
        return arguments.get(2);
    }

    ValueNode targetCount() {
        return arguments.get(3);
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        int constantTargetCount = -1;
        if (targetCount().isConstant()) {
            constantTargetCount = targetCount().asJavaConstant().asInt();
        }
        Value result = gen.getLIRGeneratorTool().emitStringIndexOf(gen.operand(sourcePointer()), gen.operand(sourceCount()), gen.operand(targetPointer()), gen.operand(targetCount()),
                        constantTargetCount);
        gen.setResult(this, result);
    }

    @Override
    public MemoryNode getLastLocationAccess() {
        return lastLocationAccess;
    }

    @Override
    public void setLastLocationAccess(MemoryNode lla) {
        updateUsages(ValueNodeUtil.asNode(lastLocationAccess), ValueNodeUtil.asNode(lla));
        lastLocationAccess = lla;
    }

    @NodeIntrinsic
    public static native int optimizedStringIndexPointer(Pointer sourcePointer, int sourceCount, Pointer targetPointer, int targetCount);
}
