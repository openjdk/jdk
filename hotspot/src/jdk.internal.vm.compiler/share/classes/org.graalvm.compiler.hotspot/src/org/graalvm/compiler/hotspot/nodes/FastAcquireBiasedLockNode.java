/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_0;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

/**
 * Marks the control flow path where an object acquired a biased lock because the lock was already
 * biased to the object on the current thread.
 */
@NodeInfo(cycles = CYCLES_0, size = SIZE_0)
public final class FastAcquireBiasedLockNode extends FixedWithNextNode implements LIRLowerable {
    public static final NodeClass<FastAcquireBiasedLockNode> TYPE = NodeClass.create(FastAcquireBiasedLockNode.class);

    @Input ValueNode object;

    public FastAcquireBiasedLockNode(ValueNode object) {
        super(TYPE, StampFactory.forVoid());
        this.object = object;
    }

    public ValueNode object() {
        return object;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        // This is just a marker node so it generates nothing
    }

    @NodeIntrinsic
    public static native void mark(Object object);
}
