/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.nashorn.internal.codegen.ObjectClassGenerator.getClassName;
import static jdk.nashorn.internal.codegen.ObjectClassGenerator.getPaddedFieldCount;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import jdk.nashorn.internal.ir.Block;
import jdk.nashorn.internal.ir.Expression;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.ir.FunctionNode.CompilationState;
import jdk.nashorn.internal.ir.LexicalContext;
import jdk.nashorn.internal.ir.Node;
import jdk.nashorn.internal.ir.Symbol;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.runtime.PropertyMap;
import jdk.nashorn.internal.runtime.RecompilableScriptFunctionData;

/**
 * Establishes depth of scope for non local symbols at the start of method.
 * If this is a recompilation, the previous data from eager compilation is
 * stored in the RecompilableScriptFunctionData and is transferred to the
 * FunctionNode being compiled
 */

final class FindScopeDepths extends NodeVisitor<LexicalContext> {

    private final Compiler compiler;
    private final CompilationEnvironment env;
    private final Map<Integer, Map<Integer, RecompilableScriptFunctionData>> fnIdToNestedFunctions = new HashMap<>();
    private final Map<Integer, Map<String, Integer>> externalSymbolDepths = new HashMap<>();

    FindScopeDepths(final Compiler compiler) {
        super(new LexicalContext());
        this.compiler = compiler;
        this.env = compiler.getCompilationEnvironment();
    }

    static int findScopesToStart(final LexicalContext lc, final FunctionNode fn, final Block block) {
        final Block bodyBlock = findBodyBlock(lc, fn, block);
        final Iterator<Block> iter = lc.getBlocks(block);
        Block b = iter.next();
        int scopesToStart = 0;
        while (true) {
            if (b.needsScope()) {
                scopesToStart++;
            }
            if (b == bodyBlock) {
                break;
            }
            b = iter.next();
        }
        return scopesToStart;
    }

    static int findInternalDepth(final LexicalContext lc, final FunctionNode fn, final Block block, final Symbol symbol) {
        final Block bodyBlock = findBodyBlock(lc, fn, block);
        final Iterator<Block> iter = lc.getBlocks(block);
        Block b = iter.next();
        int scopesToStart = 0;
        while (true) {
            if (definedInBlock(b, symbol)) {
                return scopesToStart;
            }
            if (b.needsScope()) {
                scopesToStart++;
            }
            if (b == bodyBlock) {
                break; //don't go past body block, but process it
            }
            b = iter.next();
        }
        return -1;
    }

    private static boolean definedInBlock(final Block block, final Symbol symbol) {
        if (symbol.isGlobal()) {
            if (block.isGlobalScope()) {
                return true;
            }
            //globals cannot be defined anywhere else
            return false;
        }
        return block.getExistingSymbol(symbol.getName()) == symbol;
    }

    static Block findBodyBlock(final LexicalContext lc, final FunctionNode fn, final Block block) {
        final Iterator<Block> iter = lc.getBlocks(block);
        while (iter.hasNext()) {
            final Block next = iter.next();
            if (fn.getBody() == next) {
                return next;
            }
        }
        return null;
    }

    private static Block findGlobalBlock(final LexicalContext lc, final FunctionNode fn, final Block block) {
        final Iterator<Block> iter = lc.getBlocks(block);
        Block globalBlock = null;
        while (iter.hasNext()) {
            globalBlock = iter.next();
        }
        return globalBlock;
    }

    @Override
    public boolean enterFunctionNode(final FunctionNode functionNode) {
        if (env.isOnDemandCompilation()) {
            return true;
        }

        final int fnId = functionNode.getId();
        Map<Integer, RecompilableScriptFunctionData> nestedFunctions = fnIdToNestedFunctions.get(fnId);
        if (nestedFunctions == null) {
            nestedFunctions = new HashMap<>();
            fnIdToNestedFunctions.put(fnId, nestedFunctions);
        }

        return true;
    }

    //external symbols hold the scope depth of sc11 from global at the start of the method
    @Override
    public Node leaveFunctionNode(final FunctionNode functionNode) {
        final FunctionNode newFunctionNode = functionNode.setState(lc, CompilationState.SCOPE_DEPTHS_COMPUTED);
        if (env.isOnDemandCompilation()) {
            final RecompilableScriptFunctionData data = env.getScriptFunctionData(newFunctionNode.getId());
            assert data != null : newFunctionNode.getName() + " lacks data";
            return newFunctionNode;
        }

        //create recompilable scriptfunctiondata
        final int fnId = newFunctionNode.getId();
        final Map<Integer, RecompilableScriptFunctionData> nestedFunctions = fnIdToNestedFunctions.get(fnId);

        assert nestedFunctions != null;
        // Generate the object class and property map in case this function is ever used as constructor
        final int         fieldCount         = getPaddedFieldCount(newFunctionNode.countThisProperties());
        final String      allocatorClassName = Compiler.binaryName(getClassName(fieldCount));
        final PropertyMap allocatorMap       = PropertyMap.newMap(null, 0, fieldCount, 0);
        final RecompilableScriptFunctionData data = new RecompilableScriptFunctionData(
                newFunctionNode,
                compiler.getCodeInstaller(),
                allocatorClassName,
                allocatorMap,
                nestedFunctions,
                compiler.getSourceURL(),
                externalSymbolDepths.get(fnId)
                );

        if (lc.getOutermostFunction() != newFunctionNode) {
            final FunctionNode parentFn = lc.getParentFunction(newFunctionNode);
            if (parentFn != null) {
                fnIdToNestedFunctions.get(parentFn.getId()).put(fnId, data);
            }
        } else {
            env.setData(data);
        }

        return newFunctionNode;
    }

    @Override
    public boolean enterBlock(final Block block) {
        if (env.isOnDemandCompilation()) {
            return true;
        }

        if (!lc.isFunctionBody()) {
            return true;
        }

        //the below part only happens on eager compilation when we have the entire hierarchy
        //block is a function body
        final FunctionNode fn = lc.getCurrentFunction();

        //get all symbols that are referenced inside this function body
        final Set<Symbol> symbols = new HashSet<>();
        block.accept(new NodeVisitor<LexicalContext>(new LexicalContext()) {
            @Override
            public final boolean enterDefault(final Node node) {
                if (!env.isOnDemandCompilation()) {
                    if (node instanceof Expression) {
                        final Symbol symbol = ((Expression)node).getSymbol();
                        if (symbol != null && symbol.isScope()) {
                            //if this is an internal symbol, skip it.
                            symbols.add(symbol);
                        }
                    }
                }
                return true;
            }
        });

        final Map<String, Integer> internals = new HashMap<>();

        for (final Symbol symbol : symbols) {
            Iterator<Block> iter;

            final Block globalBlock = findGlobalBlock(lc, fn, block);
            final Block bodyBlock   = findBodyBlock(lc, fn, block);

            assert globalBlock != null;
            assert bodyBlock   != null;

            final int internalDepth = findInternalDepth(lc, fn, block, symbol);
            final boolean internal = internalDepth >= 0;
            if (internal) {
                internals.put(symbol.getName(), internalDepth);
            }

            // if not internal, we have to continue walking until we reach the top. We
            // start outside the body and each new scope adds a depth count. When we
            // find the symbol, we store its depth count
            if (!internal) {
                int depthAtStart = 0;
                //not internal - keep looking.
                iter = lc.getAncestorBlocks(bodyBlock);
                while (iter.hasNext()) {
                    final Block b2 = iter.next();
                    if (definedInBlock(b2, symbol)) {
                        addExternalSymbol(fn, symbol, depthAtStart);
                        break;
                    }
                    if (b2.needsScope()) {
                        depthAtStart++;
                    }
                }
            }
        }

        return true;
    }

    private void addExternalSymbol(final FunctionNode functionNode, final Symbol symbol, final int depthAtStart) {
        final int fnId = functionNode.getId();
        Map<String, Integer> depths = externalSymbolDepths.get(fnId);
        if (depths == null) {
            depths = new HashMap<>();
            externalSymbolDepths.put(fnId, depths);
        }
        //System.err.println("PUT " + functionNode.getName() + " " + symbol + " " +depthAtStart);
        depths.put(symbol.getName(), depthAtStart);
    }
}
