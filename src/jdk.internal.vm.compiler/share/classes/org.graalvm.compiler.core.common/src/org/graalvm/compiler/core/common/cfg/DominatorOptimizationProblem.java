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

import java.util.Arrays;
import java.util.BitSet;
import java.util.EnumMap;
import java.util.Set;
import java.util.stream.Stream;

/**
 * This class represents a dominator tree problem, i.e. a problem which can be solved by traversing
 * the dominator (sub-)tree.
 *
 * @param <E> An enum that describes the flags that can be associated with a block.
 * @param <C> An arbitrary cost type that is associated with a block. It is intended to carry
 *            information needed to calculate the solution. Note that {@code C} should not contain
 *            boolean flags. Use an enum entry in {@code E} instead.
 */
public abstract class DominatorOptimizationProblem<E extends Enum<E>, C> {

    private AbstractBlockBase<?>[] blocks;
    private EnumMap<E, BitSet> flags;
    private BlockMap<C> costs;

    protected DominatorOptimizationProblem(Class<E> flagType, AbstractControlFlowGraph<?> cfg) {
        this.blocks = cfg.getBlocks();
        flags = new EnumMap<>(flagType);
        costs = new BlockMap<>(cfg);
        assert verify(blocks);
    }

    private static boolean verify(AbstractBlockBase<?>[] blocks) {
        for (int i = 0; i < blocks.length; i++) {
            AbstractBlockBase<?> block = blocks[i];
            if (i != block.getId()) {
                assert false : String.format("Id index mismatch @ %d vs. %s.getId()==%d", i, block, block.getId());
                return false;
            }
        }
        return true;
    }

    public final AbstractBlockBase<?>[] getBlocks() {
        return blocks;
    }

    public final AbstractBlockBase<?> getBlockForId(int id) {
        AbstractBlockBase<?> block = blocks[id];
        assert block.getId() == id : "wrong block-to-id mapping";
        return block;
    }

    /**
     * Sets a flag for a block.
     */
    public final void set(E flag, AbstractBlockBase<?> block) {
        BitSet bitSet = flags.get(flag);
        if (bitSet == null) {
            bitSet = new BitSet(blocks.length);
            flags.put(flag, bitSet);
        }
        bitSet.set(block.getId());
    }

    /**
     * Checks whether a flag is set for a block.
     */
    public final boolean get(E flag, AbstractBlockBase<?> block) {
        BitSet bitSet = flags.get(flag);
        return bitSet == null ? false : bitSet.get(block.getId());
    }

    /**
     * Returns a {@linkplain Stream} of blocks for which {@code flag} is set.
     */
    public final Stream<? extends AbstractBlockBase<?>> stream(E flag) {
        return Arrays.asList(getBlocks()).stream().filter(block -> get(flag, block));
    }

    /**
     * Returns the cost object associated with {@code block}. Might return {@code null} if not set.
     */
    public final C getCost(AbstractBlockBase<?> block) {
        C cost = costs.get(block);
        return cost;
    }

    /**
     * Sets the cost for a {@code block}.
     */
    public final void setCost(AbstractBlockBase<?> block, C cost) {
        costs.put(block, cost);
    }

    /**
     * Sets {@code flag} for all blocks along the dominator path from {@code block} to the root
     * until a block it finds a block where {@code flag} is already set.
     */
    public final void setDominatorPath(E flag, AbstractBlockBase<?> block) {
        BitSet bitSet = flags.get(flag);
        if (bitSet == null) {
            bitSet = new BitSet(blocks.length);
            flags.put(flag, bitSet);
        }
        for (AbstractBlockBase<?> b = block; b != null && !bitSet.get(b.getId()); b = b.getDominator()) {
            // mark block
            bitSet.set(b.getId());
        }
    }

    /**
     * Returns a {@link Stream} of flags associated with {@code block}.
     */
    public final Stream<E> getFlagsForBlock(AbstractBlockBase<?> block) {
        return getFlags().stream().filter(flag -> get(flag, block));
    }

    /**
     * Returns the {@link Set} of flags that can be set for this
     * {@linkplain DominatorOptimizationProblem problem}.
     */
    public final Set<E> getFlags() {
        return flags.keySet();
    }

    /**
     * Returns the name of a flag.
     */
    public String getName(E flag) {
        return flag.toString();
    }
}
