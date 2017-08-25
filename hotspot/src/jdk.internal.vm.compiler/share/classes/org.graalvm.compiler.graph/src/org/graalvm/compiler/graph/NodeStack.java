/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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

public final class NodeStack {
    private static final int DEFAULT_INITIAL_SIZE = 8;

    protected Node[] values;
    public int tos;

    public NodeStack() {
        this(DEFAULT_INITIAL_SIZE);
    }

    public NodeStack(int initialSize) {
        values = new Node[initialSize];
    }

    public int size() {
        return tos;
    }

    public void push(Node n) {
        int newIndex = tos++;
        int valuesLength = values.length;
        if (newIndex >= valuesLength) {
            grow();
        }
        values[newIndex] = n;
    }

    private void grow() {
        int valuesLength = values.length;
        Node[] newValues = new Node[valuesLength << 1];
        System.arraycopy(values, 0, newValues, 0, valuesLength);
        values = newValues;
    }

    public Node get(int index) {
        return values[index];
    }

    public Node pop() {
        assert tos > 0 : "stack must be non-empty";
        return values[--tos];
    }

    public Node peek() {
        assert tos > 0 : "stack must be non-empty";
        return values[tos - 1];
    }

    public boolean isEmpty() {
        return tos == 0;
    }

    @Override
    public String toString() {
        if (tos == 0) {
            return "NodeStack: []";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tos; i++) {
            sb.append(", ");
            sb.append(values[i]);
        }
        return "NodeStack: [" + sb.substring(2) + "]";
    }
}
