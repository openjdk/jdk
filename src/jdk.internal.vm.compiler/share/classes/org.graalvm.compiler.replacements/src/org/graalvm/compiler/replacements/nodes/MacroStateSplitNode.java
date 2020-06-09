/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.StateSplit;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.memory.MemoryKill;
import org.graalvm.compiler.nodes.memory.SingleMemoryKill;
import jdk.internal.vm.compiler.word.LocationIdentity;

import jdk.vm.ci.code.BytecodeFrame;

/**
 * This is an extension of {@link MacroNode} that is a {@link StateSplit} and a {@link MemoryKill}.
 */
@NodeInfo
public abstract class MacroStateSplitNode extends MacroNode implements StateSplit, SingleMemoryKill {

    public static final NodeClass<MacroStateSplitNode> TYPE = NodeClass.create(MacroStateSplitNode.class);
    @OptionalInput(InputType.State) protected FrameState stateAfter;

    protected MacroStateSplitNode(NodeClass<? extends MacroNode> c, MacroParams p) {
        super(c, p);
    }

    @Override
    public FrameState stateAfter() {
        return stateAfter;
    }

    @Override
    public void setStateAfter(FrameState x) {
        assert x == null || x.isAlive() : "frame state must be in a graph";
        updateUsages(stateAfter, x);
        stateAfter = x;
    }

    @Override
    public boolean hasSideEffect() {
        return true;
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return LocationIdentity.any();
    }

    protected void replaceSnippetInvokes(StructuredGraph snippetGraph) {
        for (MethodCallTargetNode call : snippetGraph.getNodes(MethodCallTargetNode.TYPE)) {
            Invoke invoke = call.invoke();
            if (!call.targetMethod().equals(getTargetMethod())) {
                throw new GraalError("unexpected invoke %s in snippet", getClass().getSimpleName());
            }
            assert invoke.stateAfter().bci == BytecodeFrame.AFTER_BCI;
            // Here we need to fix the bci of the invoke
            InvokeNode newInvoke = snippetGraph.add(new InvokeNode(invoke.callTarget(), bci(), invoke.getKilledLocationIdentity()));
            newInvoke.setStateAfter(invoke.stateAfter());
            snippetGraph.replaceFixedWithFixed((InvokeNode) invoke.asNode(), newInvoke);
        }
    }
}
