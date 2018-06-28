/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.core.common.alloc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;

/**
 * Represents a list of sequentially executed {@code AbstractBlockBase blocks}.
 */
public class Trace {
    private final AbstractBlockBase<?>[] blocks;
    private final ArrayList<Trace> successors;
    private int id = -1;

    public Trace(Collection<AbstractBlockBase<?>> blocks) {
        this(blocks.toArray(new AbstractBlockBase<?>[0]));
    }

    public Trace(AbstractBlockBase<?>[] blocks) {
        this.blocks = blocks;
        this.successors = new ArrayList<>();
    }

    public AbstractBlockBase<?>[] getBlocks() {
        return blocks;
    }

    public ArrayList<Trace> getSuccessors() {
        return successors;
    }

    public int size() {
        return getBlocks().length;
    }

    @Override
    public String toString() {
        return "Trace" + Arrays.toString(blocks);
    }

    public int getId() {
        assert id != -1 : "id not initialized!";
        return id;
    }

    void setId(int id) {
        this.id = id;
    }
}
