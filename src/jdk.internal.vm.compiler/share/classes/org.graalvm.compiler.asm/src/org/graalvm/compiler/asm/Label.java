/*
 * Copyright (c) 2009, 2019, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.debug.GraalError;

/**
 * This class represents a label within assembly code.
 */
public final class Label {

    private int position = -1;
    private int blockId = -1;

    /**
     * Positions of instructions that jump to this unresolved label. These instructions are patched
     * when the label is bound.
     */
    ArrayList<Integer> patchPositions;

    /**
     * Link in list of labels with instructions to be patched.
     */
    Label nextWithPatches;

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
     * Binds the label to {@code pos} and patches all instructions added by
     * {@link #addPatchAt(int, Assembler)}.
     */
    protected void bind(int pos, Assembler asm) {
        if (pos < 0) {
            throw new GraalError("Cannot bind label to negative position %d", pos);
        }
        this.position = pos;
        if (patchPositions != null) {
            for (int i = 0; i < patchPositions.size(); ++i) {
                asm.patchJumpTarget(patchPositions.get(i), position);
            }
            patchPositions = null;
        }
    }

    public boolean isBound() {
        return position >= 0;
    }

    public void addPatchAt(int branchLocation, Assembler asm) {
        assert !isBound() : "Label is already bound " + this + " " + branchLocation + " at position " + position;
        if (patchPositions == null) {
            patchPositions = new ArrayList<>(2);
            nextWithPatches = asm.labelsWithPatches;
            asm.labelsWithPatches = this;
        }
        patchPositions.add(branchLocation);

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
