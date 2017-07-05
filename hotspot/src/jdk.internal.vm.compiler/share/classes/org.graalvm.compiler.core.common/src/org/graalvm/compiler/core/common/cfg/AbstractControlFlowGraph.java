/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collection;

public interface AbstractControlFlowGraph<T extends AbstractBlockBase<T>> {

    int BLOCK_ID_INITIAL = -1;
    int BLOCK_ID_VISITED = -2;

    /**
     * Returns the list blocks contained in this control flow graph.
     *
     * It is {@linkplain CFGVerifier guaranteed} that the blocks are numbered and ordered according
     * to a reverse post order traversal of the control flow graph.
     *
     * @see CFGVerifier
     */
    T[] getBlocks();

    Collection<Loop<T>> getLoops();

    T getStartBlock();

    /**
     * True if block {@code a} is dominated by block {@code b}.
     */
    static boolean isDominatedBy(AbstractBlockBase<?> a, AbstractBlockBase<?> b) {
        int domNumberA = a.getDominatorNumber();
        int domNumberB = b.getDominatorNumber();
        return domNumberA >= domNumberB && domNumberA <= b.getMaxChildDominatorNumber();
    }

    /**
     * True if block {@code a} dominates block {@code b} and {@code a} is not identical block to
     * {@code b}.
     */
    static boolean strictlyDominates(AbstractBlockBase<?> a, AbstractBlockBase<?> b) {
        return a != b && dominates(a, b);
    }

    /**
     * True if block {@code a} dominates block {@code b}.
     */
    static boolean dominates(AbstractBlockBase<?> a, AbstractBlockBase<?> b) {
        assert a != null && b != null;
        return isDominatedBy(b, a);
    }

    /**
     * Calculates the common dominator of two blocks.
     *
     * Note that this algorithm makes use of special properties regarding the numbering of blocks.
     *
     * @see #getBlocks()
     * @see CFGVerifier
     */
    static AbstractBlockBase<?> commonDominator(AbstractBlockBase<?> a, AbstractBlockBase<?> b) {
        if (a == null) {
            return b;
        } else if (b == null) {
            return a;
        } else {
            int aDomDepth = a.getDominatorDepth();
            int bDomDepth = b.getDominatorDepth();
            AbstractBlockBase<?> aTemp;
            AbstractBlockBase<?> bTemp;
            if (aDomDepth > bDomDepth) {
                aTemp = a;
                bTemp = b;
            } else {
                aTemp = b;
                bTemp = a;
            }
            return commonDominatorHelper(aTemp, bTemp);
        }
    }

    static AbstractBlockBase<?> commonDominatorHelper(AbstractBlockBase<?> a, AbstractBlockBase<?> b) {
        int domNumberA = a.getDominatorNumber();
        AbstractBlockBase<?> result = b;
        while (domNumberA < result.getDominatorNumber()) {
            result = result.getDominator();
        }
        while (domNumberA > result.getMaxChildDominatorNumber()) {
            result = result.getDominator();
        }
        return result;
    }

    /**
     * @see AbstractControlFlowGraph#commonDominator(AbstractBlockBase, AbstractBlockBase)
     */
    @SuppressWarnings("unchecked")
    static <T extends AbstractBlockBase<T>> T commonDominatorTyped(T a, T b) {
        return (T) commonDominator(a, b);
    }
}
