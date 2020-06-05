/*
 * Copyright (c) 2009, 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.nodes.java;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_8;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_8;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.DeoptimizingFixedWithNextNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.extended.MembarNode;
import org.graalvm.compiler.nodes.spi.Lowerable;

import jdk.vm.ci.code.MemoryBarriers;

/**
 * The {@code AbstractNewObjectNode} is the base class for the new instance and new array nodes.
 */
@NodeInfo(cycles = CYCLES_8, cyclesRationale = "tlab alloc + header init", size = SIZE_8)
public abstract class AbstractNewObjectNode extends DeoptimizingFixedWithNextNode implements Lowerable {

    public static final NodeClass<AbstractNewObjectNode> TYPE = NodeClass.create(AbstractNewObjectNode.class);
    protected final boolean fillContents;

    /**
     * Controls whether this allocation emits a {@link MembarNode} with
     * {@link MemoryBarriers#STORE_STORE} as part of the object initialization.
     */
    protected boolean emitMemoryBarrier = true;

    protected AbstractNewObjectNode(NodeClass<? extends AbstractNewObjectNode> c, Stamp stamp, boolean fillContents, FrameState stateBefore) {
        super(c, stamp, stateBefore);
        this.fillContents = fillContents;
    }

    /**
     * @return <code>true</code> if the object's contents should be initialized to zero/null.
     */
    public boolean fillContents() {
        return fillContents;
    }

    @Override
    public boolean canDeoptimize() {
        return true;
    }

    public boolean emitMemoryBarrier() {
        return emitMemoryBarrier;
    }

    public void clearEmitMemoryBarrier() {
        this.emitMemoryBarrier = false;
    }
}
