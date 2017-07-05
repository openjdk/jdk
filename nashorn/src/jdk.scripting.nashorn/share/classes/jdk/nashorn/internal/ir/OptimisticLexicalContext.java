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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import jdk.nashorn.internal.codegen.types.Type;

/**
 * Lexical context that keeps track of optimistic assumptions (if any)
 * made during code generation. Used from Attr and FinalizeTypes
 */
public class OptimisticLexicalContext extends LexicalContext {

    private final boolean isEnabled;

    class Assumption {
        Symbol symbol;
        Type   type;

        Assumption(final Symbol symbol, final Type type) {
            this.symbol = symbol;
            this.type   = type;
        }
        @Override
        public String toString() {
            return symbol.getName() + "=" + type;
        }
    }

    /** Optimistic assumptions that could be made per function */
    private final Deque<List<Assumption>> optimisticAssumptions = new ArrayDeque<>();

    /**
     * Constructor
     * @param isEnabled are optimistic types enabled?
     */
    public OptimisticLexicalContext(final boolean isEnabled) {
        super();
        this.isEnabled = isEnabled;
    }

    /**
     * Are optimistic types enabled
     * @return true if optimistic types
     */
    public boolean isEnabled() {
        return isEnabled;
    }

    /**
     * Log an optimistic assumption during codegen
     * TODO : different parameters and more info about the assumption for future profiling
     * needs
     * @param symbol symbol
     * @param type   type
     */
    public void logOptimisticAssumption(final Symbol symbol, final Type type) {
        if (isEnabled) {
            final List<Assumption> peek = optimisticAssumptions.peek();
            peek.add(new Assumption(symbol, type));
        }
    }

    /**
     * Get the list of optimistic assumptions made
     * @return optimistic assumptions
     */
    public List<Assumption> getOptimisticAssumptions() {
        return Collections.unmodifiableList(optimisticAssumptions.peek());
    }

    /**
     * Does this method have optimistic assumptions made during codegen?
     * @return true if optimistic assumptions were made
     */
    public boolean hasOptimisticAssumptions() {
        return !optimisticAssumptions.isEmpty() && !getOptimisticAssumptions().isEmpty();
    }

    @Override
    public <T extends LexicalContextNode> T push(final T node) {
        if (isEnabled) {
            if(node instanceof FunctionNode) {
                optimisticAssumptions.push(new ArrayList<Assumption>());
            }
        }

        return super.push(node);
    }

    @Override
    public <T extends Node> T pop(final T node) {
        final T popped = super.pop(node);
        if (isEnabled) {
            if(node instanceof FunctionNode) {
                optimisticAssumptions.pop();
            }
        }
        return popped;
    }

}
