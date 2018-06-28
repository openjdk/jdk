/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.nodes.debug;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_IGNORED;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_IGNORED;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;

/**
 * A node for platform dependent verification of the Java heap. Intended to be used for debugging
 * heap corruption issues.
 */
//@formatter:off
@NodeInfo(size = SIZE_IGNORED,
        sizeRationale = "Node is a debugging node that should not be used in production.",
        cycles = CYCLES_IGNORED,
        cyclesRationale = "Node is a debugging node that should not be used in production.")
//@formatter:on
public final class VerifyHeapNode extends FixedWithNextNode implements Lowerable {

    public static final NodeClass<VerifyHeapNode> TYPE = NodeClass.create(VerifyHeapNode.class);

    public VerifyHeapNode() {
        super(TYPE, StampFactory.forVoid());
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    public static void addBefore(FixedNode position) {
        StructuredGraph graph = position.graph();
        graph.addBeforeFixed(position, graph.add(new VerifyHeapNode()));
    }

    public static void addAfter(FixedWithNextNode position) {
        StructuredGraph graph = position.graph();
        graph.addAfterFixed(position, graph.add(new VerifyHeapNode()));
    }

}
