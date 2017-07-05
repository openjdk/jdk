/*
 * Copyright (c) 2000, Oracle and/or its affiliates. All rights reserved.
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

package javax.imageio.spi;

import java.util.AbstractSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

/**
 * A set of <code>Object</code>s with pairwise orderings between them.
 * The <code>iterator</code> method provides the elements in
 * topologically sorted order.  Elements participating in a cycle
 * are not returned.
 *
 * Unlike the <code>SortedSet</code> and <code>SortedMap</code>
 * interfaces, which require their elements to implement the
 * <code>Comparable</code> interface, this class receives ordering
 * information via its <code>setOrdering</code> and
 * <code>unsetPreference</code> methods.  This difference is due to
 * the fact that the relevant ordering between elements is unlikely to
 * be inherent in the elements themselves; rather, it is set
 * dynamically accoring to application policy.  For example, in a
 * service provider registry situation, an application might allow the
 * user to set a preference order for service provider objects
 * supplied by a trusted vendor over those supplied by another.
 *
 */
class PartiallyOrderedSet extends AbstractSet {

    // The topological sort (roughly) follows the algorithm described in
    // Horowitz and Sahni, _Fundamentals of Data Structures_ (1976),
    // p. 315.

    // Maps Objects to DigraphNodes that contain them
    private Map poNodes = new HashMap();

    // The set of Objects
    private Set nodes = poNodes.keySet();

    /**
     * Constructs a <code>PartiallyOrderedSet</code>.
     */
    public PartiallyOrderedSet() {}

    public int size() {
        return nodes.size();
    }

    public boolean contains(Object o) {
        return nodes.contains(o);
    }

    /**
     * Returns an iterator over the elements contained in this
     * collection, with an ordering that respects the orderings set
     * by the <code>setOrdering</code> method.
     */
    public Iterator iterator() {
        return new PartialOrderIterator(poNodes.values().iterator());
    }

    /**
     * Adds an <code>Object</code> to this
     * <code>PartiallyOrderedSet</code>.
     */
    public boolean add(Object o) {
        if (nodes.contains(o)) {
            return false;
        }

        DigraphNode node = new DigraphNode(o);
        poNodes.put(o, node);
        return true;
    }

    /**
     * Removes an <code>Object</code> from this
     * <code>PartiallyOrderedSet</code>.
     */
    public boolean remove(Object o) {
        DigraphNode node = (DigraphNode)poNodes.get(o);
        if (node == null) {
            return false;
        }

        poNodes.remove(o);
        node.dispose();
        return true;
    }

    public void clear() {
        poNodes.clear();
    }

    /**
     * Sets an ordering between two nodes.  When an iterator is
     * requested, the first node will appear earlier in the
     * sequence than the second node.  If a prior ordering existed
     * between the nodes in the opposite order, it is removed.
     *
     * @return <code>true</code> if no prior ordering existed
     * between the nodes, <code>false</code>otherwise.
     */
    public boolean setOrdering(Object first, Object second) {
        DigraphNode firstPONode =
            (DigraphNode)poNodes.get(first);
        DigraphNode secondPONode =
            (DigraphNode)poNodes.get(second);

        secondPONode.removeEdge(firstPONode);
        return firstPONode.addEdge(secondPONode);
    }

    /**
     * Removes any ordering between two nodes.
     *
     * @return true if a prior prefence existed between the nodes.
     */
    public boolean unsetOrdering(Object first, Object second) {
        DigraphNode firstPONode =
            (DigraphNode)poNodes.get(first);
        DigraphNode secondPONode =
            (DigraphNode)poNodes.get(second);

        return firstPONode.removeEdge(secondPONode) ||
            secondPONode.removeEdge(firstPONode);
    }

    /**
     * Returns <code>true</code> if an ordering exists between two
     * nodes.
     */
    public boolean hasOrdering(Object preferred, Object other) {
        DigraphNode preferredPONode =
            (DigraphNode)poNodes.get(preferred);
        DigraphNode otherPONode =
            (DigraphNode)poNodes.get(other);

        return preferredPONode.hasEdge(otherPONode);
    }
}

class PartialOrderIterator implements Iterator {

    LinkedList zeroList = new LinkedList();
    Map inDegrees = new HashMap(); // DigraphNode -> Integer

    public PartialOrderIterator(Iterator iter) {
        // Initialize scratch in-degree values, zero list
        while (iter.hasNext()) {
            DigraphNode node = (DigraphNode)iter.next();
            int inDegree = node.getInDegree();
            inDegrees.put(node, new Integer(inDegree));

            // Add nodes with zero in-degree to the zero list
            if (inDegree == 0) {
                zeroList.add(node);
            }
        }
    }

    public boolean hasNext() {
        return !zeroList.isEmpty();
    }

    public Object next() {
        DigraphNode first = (DigraphNode)zeroList.removeFirst();

        // For each out node of the output node, decrement its in-degree
        Iterator outNodes = first.getOutNodes();
        while (outNodes.hasNext()) {
            DigraphNode node = (DigraphNode)outNodes.next();
            int inDegree = ((Integer)inDegrees.get(node)).intValue() - 1;
            inDegrees.put(node, new Integer(inDegree));

            // If the in-degree has fallen to 0, place the node on the list
            if (inDegree == 0) {
                zeroList.add(node);
            }
        }

        return first.getData();
    }

    public void remove() {
        throw new UnsupportedOperationException();
    }
}
