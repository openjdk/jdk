/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.phases.graph;

import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;

public class InferStamps {

    /**
     * Infer the stamps for all Object nodes in the graph, to make the stamps as precise as
     * possible. For example, this propagates the word-type through phi functions. To handle phi
     * functions at loop headers, the stamp inference is called until a fix point is reached.
     * <p>
     * This method can be used when it is needed that stamps are inferred before the first run of
     * the canonicalizer. For example, word type rewriting must run before the first run of the
     * canonicalizer because many nodes are not prepared to see the word type during
     * canonicalization.
     */
    public static void inferStamps(StructuredGraph graph) {
        /*
         * We want to make the stamps more precise. For cyclic phi functions, this means we have to
         * ignore the initial stamp because the imprecise stamp would always propagate around the
         * cycle. We therefore set the stamp to an illegal stamp, which is automatically ignored
         * when the phi function performs the "meet" operator on its input stamps.
         */
        for (Node n : graph.getNodes()) {
            if (n instanceof ValuePhiNode) {
                ValueNode node = (ValueNode) n;
                if (node.stamp(NodeView.DEFAULT) instanceof ObjectStamp) {
                    assert node.stamp(NodeView.DEFAULT).hasValues() : "We assume all Phi and Proxy stamps are legal before the analysis";
                    node.setStamp(node.stamp(NodeView.DEFAULT).empty());
                }
            }
        }

        boolean stampChanged;
        // The algorithm is not guaranteed to reach a stable state.
        int z = 0;
        do {
            stampChanged = false;
            /*
             * We could use GraphOrder.forwardGraph() to process the nodes in a defined order and
             * propagate long def-use chains in fewer iterations. However, measurements showed that
             * we have few iterations anyway, and the overhead of computing the order is much higher
             * than the benefit.
             */
            for (Node n : graph.getNodes()) {
                if (n instanceof ValueNode) {
                    ValueNode node = (ValueNode) n;
                    if (node.stamp(NodeView.DEFAULT) instanceof ObjectStamp) {
                        stampChanged |= node.inferStamp();
                    }
                }
            }
            ++z;
        } while (stampChanged && z < 10000);

        /*
         * Check that all the illegal stamps we introduced above are correctly replaced with real
         * stamps again.
         */
        assert checkNoEmptyStamp(graph);
    }

    private static boolean checkNoEmptyStamp(StructuredGraph graph) {
        for (Node n : graph.getNodes()) {
            if (n instanceof ValuePhiNode) {
                ValueNode node = (ValueNode) n;
                assert node.stamp(
                                NodeView.DEFAULT).hasValues() : "Stamp is empty after analysis. This is not necessarily an error, but a condition that we want to investigate (and then maybe relax or remove the assertion).";
            }
        }
        return true;
    }
}
