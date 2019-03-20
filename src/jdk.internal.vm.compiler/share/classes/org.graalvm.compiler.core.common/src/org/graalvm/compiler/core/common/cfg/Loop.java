/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.core.common.cfg.AbstractBlockBase.BLOCK_ID_COMPARATOR;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class Loop<T extends AbstractBlockBase<T>> {

    private final Loop<T> parent;
    private final ArrayList<Loop<T>> children;

    private final int depth;
    private final int index;
    private final T header;
    private final ArrayList<T> blocks;
    private final ArrayList<T> exits;
    /**
     * Natural exits, ignoring LoopExitNodes.
     *
     * @see #getNaturalExits()
     */
    private final ArrayList<T> naturalExits;

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
        this.naturalExits = new ArrayList<>();
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

    /**
     * Returns the loop exits.
     *
     * This might be a conservative set: before framestate assignment it matches the LoopExitNodes
     * even if earlier blocks could be considered as exits. After framestate assignments, this is
     * the same as {@link #getNaturalExits()}.
     *
     * <p>
     * LoopExitNodes are inserted in the control-flow during parsing and are natural exits: they are
     * the earliest block at which we are guaranteed to have exited the loop. However, after some
     * transformations of the graph, the natural exit might go up but the LoopExitNodes are not
     * updated.
     * </p>
     *
     * <p>
     * For example in:
     *
     * <pre>
     * for (int i = 0; i < N; i++) {
     *     if (c) {
     *         // Block 1
     *         if (dummy) {
     *             // ...
     *         } else {
     *             // ...
     *         }
     *         if (b) {
     *             continue;
     *         } else {
     *             // Block 2
     *             // LoopExitNode
     *             break;
     *         }
     *     }
     * }
     * </pre>
     *
     * After parsing, the natural exits match the LoopExitNode: Block 2 is a natural exit and has a
     * LoopExitNode. If the {@code b} condition gets canonicalized to {@code false}, the natural
     * exit moves to Block 1 while the LoopExitNode remains in Block 2. In such a scenario,
     * {@code getLoopExits()} will contain block 2 while {@link #getNaturalExits()} will contain
     * block 1.
     *
     *
     * @see #getNaturalExits()
     */
    public List<T> getLoopExits() {
        return exits;
    }

    public boolean isLoopExit(T block) {
        assert isSorted(exits);
        return Collections.binarySearch(exits, block, BLOCK_ID_COMPARATOR) >= 0;
    }

    /**
     * Returns the natural exit points: these are the earliest block that are guaranteed to never
     * reach a back-edge.
     *
     * This can not be used in the context of preserving or using loop-closed form.
     *
     * @see #getLoopExits()
     */
    public ArrayList<T> getNaturalExits() {
        return naturalExits;
    }

    public boolean isNaturalExit(T block) {
        assert isSorted(naturalExits);
        return Collections.binarySearch(naturalExits, block, BLOCK_ID_COMPARATOR) >= 0;
    }

    private static <T extends AbstractBlockBase<T>> boolean isSorted(List<T> list) {
        int lastId = Integer.MIN_VALUE;
        for (AbstractBlockBase<?> block : list) {
            if (block.getId() < lastId) {
                return false;
            }
            lastId = block.getId();
        }
        return true;
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

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }
}
