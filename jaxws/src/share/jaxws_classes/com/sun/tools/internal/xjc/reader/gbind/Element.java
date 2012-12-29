/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * {@link Expression} that represents an alphabet of a regular language.
 *
 * <p>
 * Since this package is about a regular expression over element declarations,
 * this represents an XML element declaration (hence the name.)
 *
 * Element needs to be interned, meaning one {@link Element} per one tag name.
 *
 * <p>
 * Implements {@link ElementSet} to represent a self.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class Element extends Expression implements ElementSet {
    /**
     * Once we build a graph from {@link Expression},
     * we represent an edge e1 -> e2 by {@code e1.foreEdges.contains(e2)}
     * and {@code e2.backEdges.contains(e1)}.
     */
    final Set<Element> foreEdges = new LinkedHashSet<Element>();
    final Set<Element> backEdges = new LinkedHashSet<Element>();

    /**
     * Previous element in the DFS post-order traveral
     * of the element graph.
     *
     * <p>
     * We use {@code prevPostOrder==null} as a check if the element is visted in DFS,
     * so this chain terminates by a self-reference, not by having null.
     *
     * Set in {@link #assignDfsPostOrder(Element)}
     */
    /*package*/ Element prevPostOrder;

    /**
     * {@link ConnectedComponent} to which this element belongs.
     *
     * Set in {@link #buildStronglyConnectedComponents(List<ConnectedComponent>)}
     */
    private ConnectedComponent cc;

    protected Element() {
    }

    ElementSet lastSet() {
        return this;
    }

    boolean isNullable() {
        return false;
    }

    /**
     * True if this {@link Element} is {@link SourceNode}.
     */
    boolean isSource() {
        return false;
    }

    /**
     * True if this {@link Element} is {@link SinkNode}.
     */
    boolean isSink() {
        return false;
    }

    void buildDAG(ElementSet incoming) {
        incoming.addNext(this);
    }

    public void addNext(Element element) {
        foreEdges.add(element);
        element.backEdges.add(this);
    }

    public boolean contains(ElementSet rhs) {
        return this==rhs || rhs==ElementSet.EMPTY_SET;
    }

    /**
     * Just to satisfy the {@link ElementSet} contract.
     *
     * @deprecated
     *      if you statically call this method, there's something wrong.
     */
    public Iterator<Element> iterator() {
        return Collections.singleton(this).iterator();
    }

    /**
     * Traverses the {@link Element} graph with DFS
     * and set {@link #prevPostOrder}.
     *
     * Should be first invoked on the source node of the graph.
     */
    /*package*/ Element assignDfsPostOrder(Element prev) {
        if(prevPostOrder!=null)
            return prev;        // already visited

        prevPostOrder = this;   // set a dummy value to prepare for cycles

        for (Element next : foreEdges) {
            prev = next.assignDfsPostOrder(prev);
        }
        this.prevPostOrder = prev;  // set to the real value
        return this;
    }

    /**
     * Builds a set of strongly connected components and puts them
     * all into the given set.
     */
    public void buildStronglyConnectedComponents(List<ConnectedComponent> ccs) {

        // store visited elements - loop detection
        List<Element> visitedElements = new ArrayList<Element>();

        for(Element cur=this; cur!=cur.prevPostOrder; cur=cur.prevPostOrder) {

            if(visitedElements.contains(cur)) {
                // if I've already processed cur element, I'm in a loop
                break;
            } else {
                visitedElements.add(cur);
            }

            if(cur.belongsToSCC())
                continue;

            // start a new component
            ConnectedComponent cc = new ConnectedComponent();
            ccs.add(cc);

            cur.formConnectedComponent(cc);
        }
    }

    private boolean belongsToSCC() {
        return cc!=null || isSource() || isSink();
    }

    /**
     * Forms a strongly connected component by doing a reverse DFS.
     */
    private void formConnectedComponent(ConnectedComponent group) {
        if(belongsToSCC())
            return;

        this.cc=group;
        group.add(this);
        for (Element prev : backEdges)
            prev.formConnectedComponent(group);
    }

    public boolean hasSelfLoop() {
        // if foreEdges have a loop, backEdges must have one. Or vice versa
        assert foreEdges.contains(this)==backEdges.contains(this);

        return foreEdges.contains(this);
    }

    /**
     * Checks if the given {@link ConnectedComponent} forms a cut-set
     * of a graph.
     *
     * @param visited
     *      Used to keep track of visited nodes.
     * @return
     *      true if it is indeed a cut-set. false if not.
     */
    /*package*/ final boolean checkCutSet(ConnectedComponent cc, Set<Element> visited) {
        assert belongsToSCC();  // SCC discomposition must be done first

        if(isSink())
            // the definition of the cut set is that without those nodes
            // you can't reach from soruce to sink
            return false;

        if(!visited.add(this))
            return true;

        if(this.cc==cc)
            return true;

        for (Element next : foreEdges) {
            if(!next.checkCutSet(cc,visited))
                // we've found a path to the sink
                return false;
        }

        return true;
    }
}
