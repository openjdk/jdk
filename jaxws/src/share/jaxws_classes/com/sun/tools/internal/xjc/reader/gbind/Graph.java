/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package com.sun.tools.internal.xjc.reader.gbind;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Graph of {@link Element}s.
 *
 * @author Kohsuke Kawaguchi
 */
public final class Graph implements Iterable<ConnectedComponent> {
    private final Element source = new SourceNode();
    private final Element sink = new SinkNode();

    /**
     * Strongly connected components of this graph.
     */
    private final List<ConnectedComponent> ccs = new ArrayList<ConnectedComponent>();

    /**
     * Builds a {@link Graph} from an {@link Expression} tree.
     *
     * {@link Expression} given to the graph will be modified forever,
     * and it will not be able to create another {@link Graph}.
     */
    public Graph(Expression body) {
        // attach source and sink
        Expression whole = new Sequence(new Sequence(source,body),sink);

        // build up a graph
        whole.buildDAG(ElementSet.EMPTY_SET);

        // decompose into strongly connected components.
        // the algorithm is standard DFS-based algorithm,
        // one illustration of this algorithm is available at
        // http://www.personal.kent.edu/~rmuhamma/Algorithms/MyAlgorithms/GraphAlgor/strongComponent.htm
        source.assignDfsPostOrder(sink);
        source.buildStronglyConnectedComponents(ccs);

        // cut-set check
        Set<Element> visited = new HashSet<Element>();
        for (ConnectedComponent cc : ccs) {
            visited.clear();
            if(source.checkCutSet(cc,visited)) {
                cc.isRequired = true;
            }
        }
    }

    /**
     * List up {@link ConnectedComponent}s of this graph in an order.
     */
    public Iterator<ConnectedComponent> iterator() {
        return ccs.iterator();
    }

    public String toString() {
        return ccs.toString();
    }
}
