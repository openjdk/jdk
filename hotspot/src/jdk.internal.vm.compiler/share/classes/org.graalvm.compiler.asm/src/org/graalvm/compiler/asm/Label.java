/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.asm;

import java.util.ArrayList;

/**
 * This class represents a label within assembly code.
 */
public final class Label {

    private int position = -1;
    private int blockId = -1;

    /**
     * References to instructions that jump to this unresolved label. These instructions need to be
     * patched when the label is bound using the {@link #patchInstructions(Assembler)} method.
     */
    private ArrayList<Integer> patchPositions = null;

    /**
     * Returns the position of this label in the code buffer.
     *
     * @return the position
     */
    public int position() {
        assert position >= 0 : "Unbound label is being referenced";
        return position;
    }

    public Label() {
    }

    public Label(int id) {
        blockId = id;
    }

    public int getBlockId() {
        return blockId;
    }

    /**
     * Binds the label to the specified position.
     *
     * @param pos the position
     */
    protected void bind(int pos) {
        this.position = pos;
        assert isBound();
    }

    public boolean isBound() {
        return position >= 0;
    }

    public void addPatchAt(int branchLocation) {
        assert !isBound() : "Label is already bound " + this + " " + branchLocation + " at position " + position;
        if (patchPositions == null) {
            patchPositions = new ArrayList<>(2);
        }
        patchPositions.add(branchLocation);
    }

    protected void patchInstructions(Assembler masm) {
        assert isBound() : "Label should be bound";
        if (patchPositions != null) {
            int target = position;
            for (int i = 0; i < patchPositions.size(); ++i) {
                int pos = patchPositions.get(i);
                masm.patchJumpTarget(pos, target);
            }
        }
    }

    public void reset() {
        if (this.patchPositions != null) {
            this.patchPositions.clear();
        }
        this.position = -1;
    }

    @Override
    public String toString() {
        return isBound() ? String.valueOf(position()) : "?";
    }
}
