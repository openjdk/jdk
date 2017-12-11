/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;

/**
 * Interface that overrides properties of a node, such as the node's stamp.
 *
 * This interface allows richer canonicalizations when the current compilation context can provide a
 * narrower stamp than the one stored in the node itself. One such example is performing
 * canonicalization late in the compilation, when the nodes are already scheduled, and benefit from
 * additional stamp information from conditional checks in branches.
 *
 * For example, in the following code, <code>offset + i</code> can be canonicalized once it is
 * scheduled into the branch:
 *
 * <pre>
 * public void update(int offset, int i) {
 *     if (i == 0) {
 *         array[offset + i];
 *     }
 * }
 * </pre>
 */
public interface NodeView {

    NodeView DEFAULT = new Default();

    class Default implements NodeView {
        @Override
        public Stamp stamp(ValueNode node) {
            return node.stamp;
        }
    }

    /**
     * Return a view-specific stamp of the node.
     *
     * This stamp must be more specific than the default stamp.
     */
    Stamp stamp(ValueNode node);

    static NodeView from(CanonicalizerTool tool) {
        if (tool instanceof NodeView) {
            return (NodeView) tool;
        }
        return DEFAULT;
    }
}
