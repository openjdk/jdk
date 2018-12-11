/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Iterator;
import java.util.NoSuchElementException;

class TypedGraphNodeIterator<T extends IterableNodeType> implements Iterator<T> {

    private final Graph graph;
    private final int[] ids;
    private final Node[] current;

    private int currentIdIndex;
    private boolean needsForward;

    TypedGraphNodeIterator(NodeClass<?> clazz, Graph graph) {
        this.graph = graph;
        ids = clazz.iterableIds();
        currentIdIndex = 0;
        current = new Node[ids.length];
        needsForward = true;
    }

    private Node findNext() {
        if (needsForward) {
            forward();
        } else {
            Node c = current();
            Node afterDeleted = graph.getIterableNodeNext(c);
            if (afterDeleted == null) {
                needsForward = true;
            } else if (c != afterDeleted) {
                setCurrent(afterDeleted);
            }
        }
        if (needsForward) {
            return null;
        }
        return current();
    }

    private void forward() {
        needsForward = false;
        int startIdx = currentIdIndex;
        while (true) {
            Node next;
            if (current() == null) {
                next = graph.getIterableNodeStart(ids[currentIdIndex]);
            } else {
                next = graph.getIterableNodeNext(current().typeCacheNext);
            }
            if (next == null) {
                currentIdIndex++;
                if (currentIdIndex >= ids.length) {
                    currentIdIndex = 0;
                }
                if (currentIdIndex == startIdx) {
                    needsForward = true;
                    return;
                }
            } else {
                setCurrent(next);
                break;
            }
        }
    }

    private Node current() {
        return current[currentIdIndex];
    }

    private void setCurrent(Node n) {
        current[currentIdIndex] = n;
    }

    @Override
    public boolean hasNext() {
        return findNext() != null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T next() {
        Node result = findNext();
        if (result == null) {
            throw new NoSuchElementException();
        }
        needsForward = true;
        return (T) result;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }
}
