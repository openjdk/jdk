/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.graph;

/**
 * An entity that depends upon {@linkplain Graph#maybeCompress() stable} node identifiers.
 */
class NodeIdAccessor {
    final Graph graph;
    final int epoch;

    NodeIdAccessor(Graph graph) {
        this.graph = graph;
        this.epoch = graph.compressions;
    }

    Graph getGraph() {
        return graph;
    }

    /**
     * Verifies that node identifiers have not changed since this object was created.
     *
     * @return true if the check succeeds
     * @throws VerificationError if the check fails
     */
    boolean verifyIdsAreStable() {
        int compressions = graph.compressions - epoch;
        if (compressions != 0) {
            throw new VerificationError("accessing node id in %s across %d graph compression%s", graph, compressions, compressions == 1 ? "" : "s");
        }
        return true;
    }

    /**
     * Gets the identifier for a node. If assertions are enabled, this method asserts that the
     * identifier is stable.
     */
    int getNodeId(Node node) {
        assert verifyIdsAreStable();
        return node.id();
    }
}
