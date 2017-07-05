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

package jdk.nashorn.internal.ir;

import static jdk.nashorn.internal.codegen.CompilerConstants.TEMP_PREFIX;
import static jdk.nashorn.internal.ir.Symbol.IS_TEMP;

import java.util.HashMap;
import java.util.Map;
import jdk.nashorn.internal.codegen.types.Type;

/**
 * Class that holds reusable temporary symbols by type.
 *
 */
public class TemporarySymbols {
    private static final String prefix = TEMP_PREFIX.symbolName() + "$";

    private int totalSymbolCount;
    private final Map<Type, TypedTemporarySymbols> temporarySymbolsByType = new HashMap<>();

    /**
     * Associates a temporary symbol of a given type with a node, if the node doesn't already have any symbol.
     * @param lc the current lexical context
     * @param type the type of the temporary symbol
     * @param node the node
     * @return the node that is guaranteed to have a symbol.
     */
    public Expression ensureSymbol(final LexicalContext lc, final Type type, final Expression node) {
        final Symbol symbol = node.getSymbol();
        if (symbol != null) {
            return node;
        }
        return node.setSymbol(lc, getTypedTemporarySymbol(type));
    }

    /**
     * Given a type, returns a temporary symbol of that type.
     * @param type the required type of the symbol.
     * @return a temporary symbol of the required type.
     */
    public Symbol getTypedTemporarySymbol(final Type type) {
        return getTypedTemporarySymbols(type).getTemporarySymbol(type);
    }

    private TypedTemporarySymbols getTypedTemporarySymbols(final Type type) {
        TypedTemporarySymbols temporarySymbols = temporarySymbolsByType.get(type);
        if(temporarySymbols == null) {
            temporarySymbols = new TypedTemporarySymbols();
            temporarySymbolsByType.put(type, temporarySymbols);
        }
        return temporarySymbols;
    }

    /**
     * This method is called to signal to this object that all the symbols it holds can be reused now.
     */
    public void reuse() {
        for(TypedTemporarySymbols ts: temporarySymbolsByType.values()) {
            ts.reuse();
        }
    }

    /**
     * Given a shared symbol, creates an unshared copy of it with a unique name.
     * @param symbol the shared symbol
     * @return the unshared, uniquely named copy of the symbol
     */
    public Symbol createUnshared(Symbol symbol) {
        return symbol.createUnshared(getUniqueName());
    }

    private String getUniqueName() {
        return prefix + (++totalSymbolCount);
    }

    /**
     * Returns the total number of symbols this object created during its lifetime.
     * @return the total number of symbols this object created during its lifetime.
     */
    public int getTotalSymbolCount() {
        return totalSymbolCount;
    }

    private class TypedTemporarySymbols {
        private Symbol[] symbols = new Symbol[16];
        private int nextFreeSymbol = 0;
        private int symbolCount = 0;

        Symbol getTemporarySymbol(final Type type) {
            while(nextFreeSymbol < symbolCount) {
                final Symbol nextSymbol = symbols[nextFreeSymbol];
                assert nextSymbol != null;
                // If it has a slot, we can't reuse it.
                if(!nextSymbol.hasSlot()) {
                    final Type symbolType = nextSymbol.getSymbolType();
                    if(symbolType == type) {
                        assert nextSymbol.isTemp();
                        assert !nextSymbol.isScope();
                        // If types match, we can reuse it.
                        nextSymbol.setIsShared();
                        nextFreeSymbol++;
                        return nextSymbol;
                    }
                    // If its type changed, but it doesn't have a slot then move it to its new home according to its
                    // new type.
                    getTypedTemporarySymbols(symbolType).addSymbol(nextSymbol);
                }
                // If we can move another symbol into its place, do that and repeat the analysis for this symbol.
                --symbolCount;
                if(symbolCount != nextFreeSymbol) {
                    final Symbol lastFreeSymbol = symbols[symbolCount];
                    symbols[nextFreeSymbol] = lastFreeSymbol;
                }
                symbols[symbolCount] = null;
            }
            return createNewSymbol(type);
        }

        private Symbol createNewSymbol(final Type type) {
            ensureCapacity();
            final Symbol symbol = symbols[nextFreeSymbol] = new Symbol(getUniqueName(), IS_TEMP, type);
            nextFreeSymbol++;
            symbolCount++;
            return symbol;
        }

        private void addSymbol(Symbol symbol) {
            ensureCapacity();
            symbols[symbolCount++] = symbol;
        }

        void reuse() {
            nextFreeSymbol = 0;
        }

        private void ensureCapacity() {
            if(symbolCount == symbols.length) {
                final Symbol[] newSymbols = new Symbol[symbolCount * 2];
                System.arraycopy(symbols, 0, newSymbols, 0, symbolCount);
                symbols = newSymbols;
            }
        }
    }

}
