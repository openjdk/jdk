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

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.Block;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.ir.LexicalContext;
import jdk.nashorn.internal.ir.LexicalContextNode;
import jdk.nashorn.internal.ir.Node;
import jdk.nashorn.internal.ir.Symbol;
import jdk.nashorn.internal.ir.WithNode;

/**
 * A lexical context that also tracks if we have any dynamic scopes in the context. Such scopes can have new
 * variables introduced into them at run time - a with block or a function directly containing an eval call.
 * Furthermore, this class keeps track of current discard state, which the current method emitter being used is,
 * the current compile unit, and local variable indexes
 */
final class CodeGeneratorLexicalContext extends LexicalContext {
    private int dynamicScopeCount;

    /** Map of shared scope call sites */
    private final Map<SharedScopeCall, SharedScopeCall> scopeCalls = new HashMap<>();

    /** Compile unit stack - every time we start a sub method (e.g. a split) we push one */
    private final Deque<CompileUnit> compileUnits = new ArrayDeque<>();

    /** Method emitter stack - every time we start a sub method (e.g. a split) we push one */
    private final Deque<MethodEmitter> methodEmitters = new ArrayDeque<>();

    /** The discard stack - whenever we enter a discard node we keep track of its return value status -
     *  i.e. should we keep it or throw it away */
    private final Deque<Node> discard = new ArrayDeque<>();

    /** A stack tracking the next free local variable slot in the blocks. There's one entry for every block
     *  currently on the lexical context stack. */
    private int[] nextFreeSlots = new int[16];

    /** size of next free slot vector */
    private int nextFreeSlotsSize;

    @Override
    public <T extends LexicalContextNode> T push(final T node) {
        if (isDynamicScopeBoundary(node)) {
            ++dynamicScopeCount;
        }
        return super.push(node);
    }

    @Override
    public <T extends LexicalContextNode> T pop(final T node) {
        final T popped = super.pop(node);
        if (isDynamicScopeBoundary(popped)) {
            --dynamicScopeCount;
        }
        if (node instanceof Block) {
            --nextFreeSlotsSize;
        }
        return popped;
    }

    private boolean isDynamicScopeBoundary(final LexicalContextNode node) {
        if (node instanceof Block) {
            // Block's immediate parent is a with node. Note we aren't testing for a WithNode, as that'd capture
            // processing of WithNode.expression too, but it should be unaffected.
            return !isEmpty() && peek() instanceof WithNode;
        } else if (node instanceof FunctionNode) {
            // Function has a direct eval in it (so a top-level "var ..." in the eval code can introduce a new
            // variable into the function's scope), and it isn't strict (as evals in strict functions get an
            // isolated scope).
            return isFunctionDynamicScope((FunctionNode)node);
        }
        return false;
    }

    boolean inDynamicScope() {
        return dynamicScopeCount > 0;
    }

    static boolean isFunctionDynamicScope(FunctionNode fn) {
        return fn.hasEval() && !fn.isStrict();
    }

    MethodEmitter pushMethodEmitter(final MethodEmitter newMethod) {
        methodEmitters.push(newMethod);
        return newMethod;
    }

    MethodEmitter popMethodEmitter(final MethodEmitter oldMethod) {
        assert methodEmitters.peek() == oldMethod;
        methodEmitters.pop();
        return methodEmitters.isEmpty() ? null : methodEmitters.peek();
    }

    CompileUnit pushCompileUnit(final CompileUnit newUnit) {
        compileUnits.push(newUnit);
        return newUnit;
    }

    CompileUnit popCompileUnit(final CompileUnit oldUnit) {
        assert compileUnits.peek() == oldUnit;
        compileUnits.pop();
        return compileUnits.isEmpty() ? null : compileUnits.peek();
    }

    boolean hasCompileUnits() {
        return !compileUnits.isEmpty();
    }

    Collection<SharedScopeCall> getScopeCalls() {
        return Collections.unmodifiableCollection(scopeCalls.values());
    }

    /**
     * Get a shared static method representing a dynamic scope callsite.
     *
     * @param unit current compile unit
     * @param symbol the symbol
     * @param valueType the value type of the symbol
     * @param returnType the return type
     * @param paramTypes the parameter types
     * @param flags the callsite flags
     * @return an object representing a shared scope call
     */
    SharedScopeCall getScopeCall(final CompileUnit unit, final Symbol symbol, final Type valueType, final Type returnType, final Type[] paramTypes, final int flags) {
        final SharedScopeCall scopeCall = new SharedScopeCall(symbol, valueType, returnType, paramTypes, flags);
        if (scopeCalls.containsKey(scopeCall)) {
            return scopeCalls.get(scopeCall);
        }
        scopeCall.setClassAndName(unit, getCurrentFunction().uniqueName(":scopeCall"));
        scopeCalls.put(scopeCall, scopeCall);
        return scopeCall;
    }

    /**
     * Get a shared static method representing a dynamic scope get access.
     *
     * @param unit current compile unit
     * @param type the type of the variable
     * @param symbol the symbol
     * @param flags the callsite flags
     * @return an object representing a shared scope call
     */
    SharedScopeCall getScopeGet(final CompileUnit unit, final Type type, final Symbol symbol, final int flags) {
        final SharedScopeCall scopeCall = new SharedScopeCall(symbol, type, type, null, flags);
        if (scopeCalls.containsKey(scopeCall)) {
            return scopeCalls.get(scopeCall);
        }
        scopeCall.setClassAndName(unit, getCurrentFunction().uniqueName(":scopeCall"));
        scopeCalls.put(scopeCall, scopeCall);
        return scopeCall;
    }


    void nextFreeSlot(final Block block) {
        final boolean isFunctionBody = isFunctionBody();

        final int nextFreeSlot;
        if (isFunctionBody) {
            // On entry to function, start with slot 0
            nextFreeSlot = 0;
        } else {
            // Otherwise, continue from previous block's first free slot
            nextFreeSlot = nextFreeSlots[nextFreeSlotsSize - 1];
        }
        if (nextFreeSlotsSize == nextFreeSlots.length) {
            final int[] newNextFreeSlots = new int[nextFreeSlotsSize * 2];
            System.arraycopy(nextFreeSlots, 0, newNextFreeSlots, 0, nextFreeSlotsSize);
            nextFreeSlots = newNextFreeSlots;
        }
        nextFreeSlots[nextFreeSlotsSize++] = assignSlots(block, nextFreeSlot);
    }

    private static int assignSlots(final Block block, final int firstSlot) {
        int nextSlot = firstSlot;
        for (final Symbol symbol : block.getSymbols()) {
            if (symbol.hasSlot()) {
                symbol.setSlot(nextSlot);
                nextSlot += symbol.slotCount();
            }
        }
        return nextSlot;
    }

    void pushDiscard(final Node node) {
        discard.push(node);
    }

    Node popDiscard() {
        return discard.pop();
    }

    Node getCurrentDiscard() {
        return discard.peek();
    }

    int quickSlot(final Symbol symbol) {
        final int quickSlot = nextFreeSlots[nextFreeSlotsSize - 1];
        nextFreeSlots[nextFreeSlotsSize - 1] = quickSlot + symbol.slotCount();
        return quickSlot;
    }

}

