/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.compiler.core.common.cfg;

import java.util.ArrayList;
import java.util.List;

public abstract class Loop<T extends AbstractBlockBase<T>> {

    private final Loop<T> parent;
    private final List<Loop<T>> children;

    private final int depth;
    private final int index;
    private final T header;
    private final List<T> blocks;
    private final List<T> exits;

    protected Loop(Loop<T> parent, int index, T header) {
        this.parent = parent;
        if (parent != null) {
            this.depth = parent.getDepth() + 1;
        } else {
            this.depth = 1;
        }
        this.index = index;
        this.header = header;
        this.blocks = new ArrayList<>();
        this.children = new ArrayList<>();
        this.exits = new ArrayList<>();
    }

    public abstract long numBackedges();

    @Override
    public String toString() {
        return "loop " + index + " depth " + getDepth() + (parent != null ? " outer " + parent.index : "");
    }

    public Loop<T> getParent() {
        return parent;
    }

    public List<Loop<T>> getChildren() {
        return children;
    }

    public int getDepth() {
        return depth;
    }

    public int getIndex() {
        return index;
    }

    public T getHeader() {
        return header;
    }

    public List<T> getBlocks() {
        return blocks;
    }

    public List<T> getExits() {
        return exits;
    }

    public void addExit(T t) {
        exits.add(t);
    }

    /**
     * Determines if one loop is a transitive parent of another loop.
     *
     * @param childLoop The loop for which parentLoop might be a transitive parent loop.
     * @param parentLoop The loop which might be a transitive parent loop of child loop.
     * @return {@code true} if parentLoop is a (transitive) parent loop of childLoop, {@code false}
     *         otherwise
     */
    public static <T extends AbstractBlockBase<T>> boolean transitiveParentLoop(Loop<T> childLoop, Loop<T> parentLoop) {
        Loop<T> curr = childLoop;
        while (curr != null) {
            if (curr == parentLoop) {
                return true;
            }
            curr = curr.getParent();
        }
        return false;
    }

    @Override
    public int hashCode() {
        return index + depth * 31;
    }
}
