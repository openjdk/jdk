/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.phases.common;

import static org.graalvm.compiler.core.common.GraalOptions.GenLoopSafepoints;

import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.LoopEndNode;
import org.graalvm.compiler.nodes.SafepointNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.phases.Phase;

/**
 * Adds safepoints to loops.
 */
public class LoopSafepointInsertionPhase extends Phase {

    @Override
    public boolean checkContract() {
        // the size / cost after is highly dynamic and dependent on the graph, thus we do not verify
        // costs for this phase
        return false;
    }

    @Override
    protected void run(StructuredGraph graph) {
        if (GenLoopSafepoints.getValue(graph.getOptions())) {
            for (LoopBeginNode loopBeginNode : graph.getNodes(LoopBeginNode.TYPE)) {
                for (LoopEndNode loopEndNode : loopBeginNode.loopEnds()) {
                    if (loopEndNode.canSafepoint()) {
                        SafepointNode safepointNode = graph.add(new SafepointNode());
                        graph.addBeforeFixed(loopEndNode, safepointNode);
                    }
                }
            }
        }
    }
}
