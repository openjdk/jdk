/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.nashorn.internal.codegen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jdk.nashorn.internal.ir.Symbol;

/**
 * Tracks the variable area state.
 *
 */
public final class Frame {
    /** Previous frame. */
    private Frame previous;

    /** Current variables. */
    private final ArrayList<Symbol> symbols;

    /** Number of slots in previous frame. */
    private int baseCount;

    /** Number of slots in this frame. */
    private int count;

    /**
     * Constructor.
     *
     * @param previous frame, the parent variable frame
     */
    public Frame(final Frame previous) {
        this.previous  = previous;
        this.symbols   = new ArrayList<>();
        this.baseCount = getBaseCount();
        this.count     = 0;
    }

    /**
     * Copy constructor
     * @param frame
     * @param symbols
     */
    private Frame(final Frame frame, final List<Symbol> symbols) {
        this.previous  = frame.getPrevious() == null ? null : new Frame(frame.getPrevious(), frame.getPrevious().getSymbols());
        this.symbols   = new ArrayList<>(frame.getSymbols());
        this.baseCount = frame.getBaseCount();
        this.count     = frame.getCount();
    }

    /**
     * Copy the frame
     *
     * @return a new frame with the identical contents
     */
    public Frame copy() {
        return new Frame(this, getSymbols());
    }

    /**
     * Add a new variable to the frame.
     * @param symbol Symbol representing variable.
     */
    public void addSymbol(final Symbol symbol) {
        final int slot = symbol.getSlot();
        if (slot < 0) {
            symbols.add(symbol);
            count += symbol.slotCount();
        }
    }

    /**
     * Realign slot numbering prior to code generation.
     * @return Number of slots in frame.
     */
    public int realign() {
        baseCount = getBaseCount();
        count     = 0;

        for (final Symbol symbol : symbols) {
            if (symbol.hasSlot()) {
                symbol.setSlot(baseCount + count);
                count += symbol.slotCount();
            }
        }

        return count;
    }

    /**
     * Return the slot count of previous frames.
     * @return Number of slots in previous frames.
     */
    private int getBaseCount() {
        return previous != null ? previous.getSlotCount() : 0;
    }

    /**
     * Determine the number of slots to top of frame.
     * @return Number of slots in total.
     */
    public int getSlotCount() {
        return baseCount + count;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        Frame f = this;
        boolean hasPrev = false;
        int pos = 0;

        do {
            if (hasPrev) {
                sb.append("\n");
            }

            sb.append("#").
                append(pos++).
                append(" {baseCount:").
                append(baseCount).
                append(", ").
                append("count:").
                append(count).
                append("} ");

            for (final Symbol var : f.getSymbols()) {
                sb.append('[').
                    append(var.toString()).
                    append(' ').
                    append(var.hashCode()).
                    append("] ");
            }

            f = f.getPrevious();
            hasPrev = true;
        } while (f != null);

        return sb.toString();
    }

    /**
     * Get variable count for this frame
     * @return variable count
     */
    public int getCount() {
        return count;
    }

    /**
     * Get previous frame
     * @return previous frame
     */
    public Frame getPrevious() {
        return previous;
    }

    /**
     * Set previous frame
     * @param previous previous frame
     */
    public void setPrevious(final Frame previous) {
        this.previous = previous;
    }

    /**
     * Get symbols in frame
     * @return a list of symbols in this frame
     */
    public List<Symbol> getSymbols() {
        return Collections.unmodifiableList(symbols);
    }
 }
