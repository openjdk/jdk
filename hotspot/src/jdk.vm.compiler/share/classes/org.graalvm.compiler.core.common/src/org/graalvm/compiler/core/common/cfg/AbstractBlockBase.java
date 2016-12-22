/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collections;
import java.util.List;

public abstract class AbstractBlockBase<T extends AbstractBlockBase<T>> {

    protected int id;
    protected int domDepth;

    protected T[] predecessors;
    protected T[] successors;

    private T dominator;
    private List<T> dominated;
    private int domNumber;
    private int maxChildDomNumber;

    private boolean align;
    private int linearScanNumber;

    protected AbstractBlockBase() {
        this.id = AbstractControlFlowGraph.BLOCK_ID_INITIAL;
        this.linearScanNumber = -1;
        this.domNumber = -1;
        this.maxChildDomNumber = -1;
    }

    public void setDominatorNumber(int domNumber) {
        this.domNumber = domNumber;
    }

    public void setMaxChildDomNumber(int maxChildDomNumber) {
        this.maxChildDomNumber = maxChildDomNumber;
    }

    public int getDominatorNumber() {
        return domNumber;
    }

    public int getMaxChildDominatorNumber() {
        return this.maxChildDomNumber;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public T[] getPredecessors() {
        return predecessors;
    }

    public void setPredecessors(T[] predecessors) {
        this.predecessors = predecessors;
    }

    public T[] getSuccessors() {
        return successors;
    }

    public void setSuccessors(T[] successors) {
        this.successors = successors;
    }

    public T getDominator() {
        return dominator;
    }

    public void setDominator(T dominator) {
        this.dominator = dominator;
        this.domDepth = dominator.domDepth + 1;
    }

    public int getDominatorDepth() {
        return domDepth;
    }

    public List<T> getDominated() {
        if (dominated == null) {
            return Collections.emptyList();
        }
        return dominated;
    }

    public void setDominated(List<T> blocks) {
        dominated = blocks;
    }

    @Override
    public String toString() {
        return "B" + id;
    }

    public int getPredecessorCount() {
        return getPredecessors().length;
    }

    public int getSuccessorCount() {
        return getSuccessors().length;
    }

    public int getLinearScanNumber() {
        return linearScanNumber;
    }

    public void setLinearScanNumber(int linearScanNumber) {
        this.linearScanNumber = linearScanNumber;
    }

    public boolean isAligned() {
        return align;
    }

    public void setAlign(boolean align) {
        this.align = align;
    }

    public abstract boolean isExceptionEntry();

    public abstract Loop<T> getLoop();

    public abstract int getLoopDepth();

    public abstract void delete();

    public abstract boolean isLoopEnd();

    public abstract boolean isLoopHeader();

    public abstract T getPostdominator();

    public abstract double probability();

    public abstract T getDominator(int distance);
}
