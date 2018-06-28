/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.nodes;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;

/**
 * Base class for nodes that contain "virtual" state, like FrameState and VirtualObjectState.
 * Subclasses of this class will be treated in a special way by the scheduler.
 */
@NodeInfo(allowedUsageTypes = {InputType.State})
public abstract class VirtualState extends Node {

    protected VirtualState(NodeClass<? extends VirtualState> c) {
        super(c);
    }

    public static final NodeClass<VirtualState> TYPE = NodeClass.create(VirtualState.class);

    public abstract static class NodeClosure<T extends Node> {

        public abstract void apply(Node usage, T node);
    }

    public interface VirtualClosure {

        void apply(VirtualState node);
    }

    public abstract VirtualState duplicateWithVirtualState();

    public abstract void applyToNonVirtual(NodeClosure<? super ValueNode> closure);

    /**
     * Performs a <b>pre-order</b> iteration over all elements reachable from this state that are a
     * subclass of {@link VirtualState}.
     */
    public abstract void applyToVirtual(VirtualClosure closure);

    public abstract boolean isPartOfThisState(VirtualState state);

    @Override
    public final StructuredGraph graph() {
        return (StructuredGraph) super.graph();
    }

}
