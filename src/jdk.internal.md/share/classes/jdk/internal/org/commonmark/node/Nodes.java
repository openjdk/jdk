/*
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

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, a notice that is now available elsewhere in this distribution
 * accompanied the original version of this file, and, per its terms,
 * should not be removed.
 */

package jdk.internal.org.commonmark.node;

import java.util.Iterator;

/**
 * Utility class for working with multiple {@link Node}s.
 *
 * @since 0.16.0
 */
public class Nodes {

    private Nodes() {
    }

    /**
     * The nodes between (not including) start and end.
     */
    public static Iterable<Node> between(Node start, Node end) {
        return new NodeIterable(start.getNext(), end);
    }

    private static class NodeIterable implements Iterable<Node> {

        private final Node first;
        private final Node end;

        private NodeIterable(Node first, Node end) {
            this.first = first;
            this.end = end;
        }

        @Override
        public Iterator<Node> iterator() {
            return new NodeIterator(first, end);
        }
    }

    private static class NodeIterator implements Iterator<Node> {

        private Node node;
        private final Node end;

        private NodeIterator(Node first, Node end) {
            node = first;
            this.end = end;
        }

        @Override
        public boolean hasNext() {
            return node != null && node != end;
        }

        @Override
        public Node next() {
            Node result = node;
            node = node.getNext();
            return result;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("remove");
        }
    }
}

