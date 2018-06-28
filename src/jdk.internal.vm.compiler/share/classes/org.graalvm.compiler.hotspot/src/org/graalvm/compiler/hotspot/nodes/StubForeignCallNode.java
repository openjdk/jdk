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


package org.graalvm.compiler.hotspot.nodes;

import static org.graalvm.compiler.nodeinfo.InputType.Memory;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_UNKNOWN;

import java.util.Arrays;

import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.Verbosity;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.memory.MemoryCheckpoint;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.internal.vm.compiler.word.LocationIdentity;

import jdk.vm.ci.meta.Value;

/**
 * Node for a {@linkplain ForeignCallDescriptor foreign} call from within a stub.
 */
@NodeInfo(nameTemplate = "StubForeignCall#{p#descriptor/s}", allowedUsageTypes = Memory, cycles = CYCLES_UNKNOWN, size = SIZE_UNKNOWN)
public final class StubForeignCallNode extends FixedWithNextNode implements LIRLowerable, MemoryCheckpoint.Multi {

    public static final NodeClass<StubForeignCallNode> TYPE = NodeClass.create(StubForeignCallNode.class);
    @Input NodeInputList<ValueNode> arguments;
    protected final ForeignCallsProvider foreignCalls;

    protected final ForeignCallDescriptor descriptor;

    public StubForeignCallNode(@InjectedNodeParameter ForeignCallsProvider foreignCalls, @InjectedNodeParameter Stamp stamp, ForeignCallDescriptor descriptor, ValueNode... arguments) {
        super(TYPE, stamp);
        this.arguments = new NodeInputList<>(this, arguments);
        this.descriptor = descriptor;
        this.foreignCalls = foreignCalls;
    }

    public ForeignCallDescriptor getDescriptor() {
        return descriptor;
    }

    @Override
    public LocationIdentity[] getLocationIdentities() {
        LocationIdentity[] killedLocations = foreignCalls.getKilledLocations(descriptor);
        killedLocations = Arrays.copyOf(killedLocations, killedLocations.length + 1);
        killedLocations[killedLocations.length - 1] = HotSpotReplacementsUtil.PENDING_EXCEPTION_LOCATION;
        return killedLocations;
    }

    protected Value[] operands(NodeLIRBuilderTool gen) {
        Value[] operands = new Value[arguments.size()];
        for (int i = 0; i < operands.length; i++) {
            operands[i] = gen.operand(arguments.get(i));
        }
        return operands;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        assert graph().start() instanceof StubStartNode;
        ForeignCallLinkage linkage = foreignCalls.lookupForeignCall(descriptor);
        Value[] operands = operands(gen);
        Value result = gen.getLIRGeneratorTool().emitForeignCall(linkage, null, operands);
        if (result != null) {
            gen.setResult(this, result);
        }
    }

    @Override
    public String toString(Verbosity verbosity) {
        if (verbosity == Verbosity.Name) {
            return super.toString(verbosity) + "#" + descriptor;
        }
        return super.toString(verbosity);
    }
}
