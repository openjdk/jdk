/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.orbutil.graph ;

import java.util.Collection ;
import java.util.AbstractSet ;
import java.util.Iterator ;
import java.util.Map ;
import java.util.HashMap ;
import java.util.Set ;
import java.util.HashSet ;

public class GraphImpl extends AbstractSet implements Graph
{
    private Map /* Map<Node,NodeData> */ nodeToData ;

    public GraphImpl()
    {
        nodeToData = new HashMap() ;
    }

    public GraphImpl( Collection coll )
    {
        this() ;
        addAll( coll ) ;
    }

/***********************************************************************************/
/************ AbstractSet implementation *******************************************/
/***********************************************************************************/

    // Required for AbstractSet
    public boolean add( Object obj ) // obj must be a Node
    {
        if (!(obj instanceof Node))
            throw new IllegalArgumentException( "Graphs must contain only Node instances" ) ;

        Node node = (Node)obj ;
        boolean found = nodeToData.keySet().contains( obj ) ;

        if (!found) {
            NodeData nd = new NodeData() ;
            nodeToData.put( node, nd ) ;
        }

        return !found ;
    }

    // Required for AbstractSet
    public Iterator iterator()
    {
        return nodeToData.keySet().iterator() ;
    }

    // Required for AbstractSet
    public int size()
    {
        return nodeToData.keySet().size() ;
    }

/***********************************************************************************/

    public NodeData getNodeData( Node node )
    {
        return (NodeData)nodeToData.get( node ) ;
    }

    private void clearNodeData()
    {
        // Clear every node
        Iterator iter = nodeToData.entrySet().iterator() ;
        while (iter.hasNext()) {
            Map.Entry entry = (Map.Entry)iter.next() ;
            NodeData nd = (NodeData)(entry.getValue()) ;
            nd.clear( ) ;
        }
    }

    interface NodeVisitor
    {
        void visit( Graph graph, Node node, NodeData nd ) ;
    }

    // This visits every node in the graph exactly once.  A
    // visitor is allowed to modify the graph during the
    // traversal.
    void visitAll( NodeVisitor nv )
    {
        boolean done = false ;

        // Repeat the traversal until every node has been visited.  Since
        // it takes one pass to determine whether or not each node has
        // already been visited, this loop always runs at least once.
        do {
            done = true ;

            // Copy entries to array to avoid concurrent modification
            // problem with iterator if the visitor is updating the graph.
            Map.Entry[] entries =
                (Map.Entry[])nodeToData.entrySet().toArray( new Map.Entry[0] ) ;

            // Visit each node in the graph that has not already been visited.
            // If any node is visited in this pass, we must run at least one more
            // pass.
            for (int ctr=0; ctr<entries.length; ctr++) {
                Map.Entry current = entries[ctr] ;
                Node node = (Node)current.getKey() ;
                NodeData nd = (NodeData)current.getValue() ;

                if (!nd.isVisited()) {
                    nd.visited() ;
                    done = false ;

                    nv.visit( this, node, nd ) ;
                }
            }
        } while (!done) ;
    }

    private void markNonRoots()
    {
        visitAll(
            new NodeVisitor() {
                public void visit( Graph graph, Node node, NodeData nd )
                {
                    Iterator iter = node.getChildren().iterator() ; // Iterator<Node>
                    while (iter.hasNext()) {
                        Node child = (Node)iter.next() ;

                        // Make sure the child is in the graph so it can be
                        // visited later if necessary.
                        graph.add( child ) ;

                        // Mark the child as a non-root, since a child is never a root.
                        NodeData cnd = graph.getNodeData( child ) ;
                        cnd.notRoot() ;
                    }
                }
            } ) ;
    }

    private Set collectRootSet()
    {
        final Set result = new HashSet() ;

        Iterator iter = nodeToData.entrySet().iterator() ;
        while (iter.hasNext()) {
            Map.Entry entry = (Map.Entry)iter.next() ;
            Node node = (Node)entry.getKey() ;
            NodeData nd = (NodeData)entry.getValue() ;
            if (nd.isRoot())
                result.add( node ) ;
        }

        return result ;
    }

    public Set /* Set<Node> */ getRoots()
    {
        clearNodeData() ;
        markNonRoots() ;
        return collectRootSet() ;
    }
}
