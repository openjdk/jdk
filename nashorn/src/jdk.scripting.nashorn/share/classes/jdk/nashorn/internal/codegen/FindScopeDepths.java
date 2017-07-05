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

import static jdk.nashorn.internal.runtime.logging.DebugLogger.quote;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import jdk.nashorn.internal.codegen.ObjectClassGenerator.AllocatorDescriptor;
import jdk.nashorn.internal.ir.Block;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.ir.FunctionNode.CompilationState;
import jdk.nashorn.internal.ir.IdentNode;
import jdk.nashorn.internal.ir.LexicalContext;
import jdk.nashorn.internal.ir.Node;
import jdk.nashorn.internal.ir.Symbol;
import jdk.nashorn.internal.ir.WithNode;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.RecompilableScriptFunctionData;
import jdk.nashorn.internal.runtime.logging.DebugLogger;
import jdk.nashorn.internal.runtime.logging.Loggable;
import jdk.nashorn.internal.runtime.logging.Logger;

/**
 * Establishes depth of scope for non local symbols at the start of method.
 * If this is a recompilation, the previous data from eager compilation is
 * stored in the RecompilableScriptFunctionData and is transferred to the
 * FunctionNode being compiled
 */
@Logger(name="scopedepths")
final class FindScopeDepths extends NodeVisitor<LexicalContext> implements Loggable {

    private final Compiler compiler;
    private final Map<Integer, Map<Integer, RecompilableScriptFunctionData>> fnIdToNestedFunctions = new HashMap<>();
    private final Map<Integer, Map<String, Integer>> externalSymbolDepths = new HashMap<>();
    private final Map<Integer, Set<String>> internalSymbols = new HashMap<>();
    private final Set<Block> withBodies = new HashSet<>();

    private final DebugLogger log;

    private int dynamicScopeCount;

    FindScopeDepths(final Compiler compiler) {
        super(new LexicalContext());
        this.compiler = compiler;
        this.log      = initLogger(compiler.getContext());
    }

    @Override
    public DebugLogger getLogger() {
        return log;
    }

    @Override
    public DebugLogger initLogger(final Context context) {
        return context.getLogger(this.getClass());
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

    private static Block findGlobalBlock(final LexicalContext lc, final Block block) {
        final Iterator<Block> iter = lc.getBlocks(block);
        Block globalBlock = null;
        while (iter.hasNext()) {
            globalBlock = iter.next();
        }
        return globalBlock;
    }

    private static boolean isDynamicScopeBoundary(final FunctionNode fn) {
        return fn.needsDynamicScope();
    }

    private boolean isDynamicScopeBoundary(final Block block) {
        return withBodies.contains(block);
    }

    @Override
    public boolean enterFunctionNode(final FunctionNode functionNode) {
        if (compiler.isOnDemandCompilation()) {
            return true;
        }

        if (isDynamicScopeBoundary(functionNode)) {
            increaseDynamicScopeCount(functionNode);
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
        final String name = functionNode.getName();
        FunctionNode newFunctionNode = functionNode.setState(lc, CompilationState.SCOPE_DEPTHS_COMPUTED);

        if (compiler.isOnDemandCompilation()) {
            final RecompilableScriptFunctionData data = compiler.getScriptFunctionData(newFunctionNode.getId());
            if (data.inDynamicContext()) {
                log.fine("Reviving scriptfunction ", quote(name), " as defined in previous (now lost) dynamic scope.");
                newFunctionNode = newFunctionNode.setInDynamicContext(lc);
            }
            return newFunctionNode;
        }

        if (inDynamicScope()) {
            log.fine("Tagging ", quote(name), " as defined in dynamic scope");
            newFunctionNode = newFunctionNode.setInDynamicContext(lc);
        }

        //create recompilable scriptfunctiondata
        final int fnId = newFunctionNode.getId();
        final Map<Integer, RecompilableScriptFunctionData> nestedFunctions = fnIdToNestedFunctions.remove(fnId);

        assert nestedFunctions != null;
        // Generate the object class and property map in case this function is ever used as constructor
        final RecompilableScriptFunctionData data = new RecompilableScriptFunctionData(
                newFunctionNode,
                compiler.getCodeInstaller(),
                new AllocatorDescriptor(newFunctionNode.getThisProperties()),
                nestedFunctions,
                externalSymbolDepths.get(fnId),
                internalSymbols.get(fnId),
                compiler.removeSerializedAst(fnId));

        if (lc.getOutermostFunction() != newFunctionNode) {
            final FunctionNode parentFn = lc.getParentFunction(newFunctionNode);
            if (parentFn != null) {
                fnIdToNestedFunctions.get(parentFn.getId()).put(fnId, data);
            }
        } else {
            compiler.setData(data);
        }

        if (isDynamicScopeBoundary(functionNode)) {
            decreaseDynamicScopeCount(functionNode);
        }

        return newFunctionNode;
    }

    private boolean inDynamicScope() {
        return dynamicScopeCount > 0;
    }

    private void increaseDynamicScopeCount(final Node node) {
        assert dynamicScopeCount >= 0;
        ++dynamicScopeCount;
        if (log.isEnabled()) {
            log.finest(quote(lc.getCurrentFunction().getName()), " ++dynamicScopeCount = ", dynamicScopeCount, " at: ", node, node.getClass());
        }
    }

    private void decreaseDynamicScopeCount(final Node node) {
        --dynamicScopeCount;
        assert dynamicScopeCount >= 0;
        if (log.isEnabled()) {
            log.finest(quote(lc.getCurrentFunction().getName()), " --dynamicScopeCount = ", dynamicScopeCount, " at: ", node, node.getClass());
        }
    }

    @Override
    public boolean enterWithNode(final WithNode node) {
        withBodies.add(node.getBody());
        return true;
    }

    @Override
    public boolean enterBlock(final Block block) {
        if (compiler.isOnDemandCompilation()) {
            return true;
        }

        if (isDynamicScopeBoundary(block)) {
            increaseDynamicScopeCount(block);
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
                if (!compiler.isOnDemandCompilation()) {
                    if (node instanceof IdentNode) {
                        final Symbol symbol = ((IdentNode)node).getSymbol();
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

        final Block globalBlock = findGlobalBlock(lc, block);
        final Block bodyBlock   = findBodyBlock(lc, fn, block);

        assert globalBlock != null;
        assert bodyBlock   != null;

        for (final Symbol symbol : symbols) {
            Iterator<Block> iter;

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

        addInternalSymbols(fn, internals.keySet());

        if (log.isEnabled()) {
            log.info(fn.getName() + " internals=" + internals + " externals=" + externalSymbolDepths.get(fn.getId()));
        }

        return true;
    }

    @Override
    public Node leaveBlock(final Block block) {
        if (compiler.isOnDemandCompilation()) {
            return block;
        }
        if (isDynamicScopeBoundary(block)) {
            decreaseDynamicScopeCount(block);
        }
        return block;
    }

    private void addInternalSymbols(final FunctionNode functionNode, final Set<String> symbols) {
        final int fnId = functionNode.getId();
        assert internalSymbols.get(fnId) == null || internalSymbols.get(fnId).equals(symbols); //e.g. cloned finally block
        internalSymbols.put(fnId, symbols);
    }

    private void addExternalSymbol(final FunctionNode functionNode, final Symbol symbol, final int depthAtStart) {
        final int fnId = functionNode.getId();
        Map<String, Integer> depths = externalSymbolDepths.get(fnId);
        if (depths == null) {
            depths = new HashMap<>();
            externalSymbolDepths.put(fnId, depths);
        }
        depths.put(symbol.getName(), depthAtStart);
    }

}
