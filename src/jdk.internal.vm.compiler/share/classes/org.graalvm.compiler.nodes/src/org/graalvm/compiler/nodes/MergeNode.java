/*
 * Copyright (c) 2009, 2015, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;

/**
 * Denotes the merging of multiple control-flow paths.
 */
@NodeInfo
public final class MergeNode extends AbstractMergeNode {

    public static final NodeClass<MergeNode> TYPE = NodeClass.create(MergeNode.class);

    public MergeNode() {
        super(TYPE);
    }

    public static void removeMergeIfDegenerated(MergeNode node) {
        if (node.forwardEndCount() == 1 && node.hasNoUsages()) {
            FixedNode currentNext = node.next();
            node.setNext(null);
            EndNode forwardEnd = node.forwardEndAt(0);
            forwardEnd.replaceAtPredecessor(currentNext);
            node.markDeleted();
            forwardEnd.markDeleted();
        }
    }

    @Override
    public boolean verify() {
        assertTrue(this.forwardEndCount() > 1, "Must merge more than one end.");
        return true;
    }
}
