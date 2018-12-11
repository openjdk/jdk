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


package org.graalvm.compiler.nodes.virtual;

import static org.graalvm.compiler.nodeinfo.InputType.Extension;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_0;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.ArrayLengthProvider;
import org.graalvm.compiler.nodes.spi.Virtualizable;
import org.graalvm.compiler.nodes.spi.VirtualizerTool;
import org.graalvm.compiler.nodes.util.GraphUtil;

/**
 * Selects one object from a {@link CommitAllocationNode}. The object is identified by its
 * {@link VirtualObjectNode}.
 */
@NodeInfo(cycles = CYCLES_0, size = SIZE_0)
public final class AllocatedObjectNode extends FloatingNode implements Virtualizable, ArrayLengthProvider {

    public static final NodeClass<AllocatedObjectNode> TYPE = NodeClass.create(AllocatedObjectNode.class);
    @Input VirtualObjectNode virtualObject;
    @Input(Extension) CommitAllocationNode commit;

    public AllocatedObjectNode(VirtualObjectNode virtualObject) {
        super(TYPE, StampFactory.objectNonNull(TypeReference.createExactTrusted(virtualObject.type())));
        this.virtualObject = virtualObject;
    }

    public VirtualObjectNode getVirtualObject() {
        return virtualObject;
    }

    public CommitAllocationNode getCommit() {
        return commit;
    }

    public void setCommit(CommitAllocationNode x) {
        updateUsages(commit, x);
        commit = x;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        tool.replaceWithVirtual(getVirtualObject());
    }

    @Override
    public ValueNode findLength(FindLengthMode mode, ConstantReflectionProvider constantReflection) {
        return GraphUtil.arrayLength(virtualObject, mode, constantReflection);
    }
}
