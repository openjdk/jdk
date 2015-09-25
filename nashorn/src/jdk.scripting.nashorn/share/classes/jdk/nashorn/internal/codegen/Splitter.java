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

import static jdk.nashorn.internal.codegen.CompilerConstants.SPLIT_PREFIX;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jdk.nashorn.internal.ir.Block;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.ir.LexicalContext;
import jdk.nashorn.internal.ir.LiteralNode;
import jdk.nashorn.internal.ir.LiteralNode.ArrayLiteralNode;
import jdk.nashorn.internal.ir.LiteralNode.ArrayLiteralNode.ArrayUnit;
import jdk.nashorn.internal.ir.Node;
import jdk.nashorn.internal.ir.SplitNode;
import jdk.nashorn.internal.ir.Statement;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.logging.DebugLogger;
import jdk.nashorn.internal.runtime.logging.Loggable;
import jdk.nashorn.internal.runtime.logging.Logger;
import jdk.nashorn.internal.runtime.options.Options;

/**
 * Split the IR into smaller compile units.
 */
@Logger(name="splitter")
final class Splitter extends NodeVisitor<LexicalContext> implements Loggable {
    /** Current compiler. */
    private final Compiler compiler;

    /** IR to be broken down. */
    private final FunctionNode outermost;

    /** Compile unit for the main script. */
    private final CompileUnit outermostCompileUnit;

    /** Cache for calculated block weights. */
    private final Map<Node, Long> weightCache = new HashMap<>();

    /** Weight threshold for when to start a split. */
    public static final long SPLIT_THRESHOLD = Options.getIntProperty("nashorn.compiler.splitter.threshold", 32 * 1024);

    private final DebugLogger log;

    /**
     * Constructor.
     *
     * @param compiler              the compiler
     * @param functionNode          function node to split
     * @param outermostCompileUnit  compile unit for outermost function, if non-lazy this is the script's compile unit
     */
    public Splitter(final Compiler compiler, final FunctionNode functionNode, final CompileUnit outermostCompileUnit) {
        super(new LexicalContext());
        this.compiler             = compiler;
        this.outermost            = functionNode;
        this.outermostCompileUnit = outermostCompileUnit;
        this.log                  = initLogger(compiler.getContext());
    }

    @Override
    public DebugLogger initLogger(final Context context) {
        return context.getLogger(this.getClass());
    }

    @Override
    public DebugLogger getLogger() {
        return log;
    }

    /**
     * Execute the split.
     * @param fn the function to split
     * @param top whether this is the topmost compiled function (it's either a program, or we're doing a recompilation).
     */
    FunctionNode split(final FunctionNode fn, final boolean top) {
        FunctionNode functionNode = fn;

        log.fine("Initiating split of '", functionNode.getName(), "'");

        long weight = WeighNodes.weigh(functionNode);

        // We know that our LexicalContext is empty outside the call to functionNode.accept(this) below,
        // so we can pass null to all methods expecting a LexicalContext parameter.
        assert lc.isEmpty() : "LexicalContext not empty";

        if (weight >= SPLIT_THRESHOLD) {
            log.info("Splitting '", functionNode.getName(), "' as its weight ", weight, " exceeds split threshold ", SPLIT_THRESHOLD);
            functionNode = (FunctionNode)functionNode.accept(this);

            if (functionNode.isSplit()) {
                // Weight has changed so weigh again, this time using block weight cache
                weight = WeighNodes.weigh(functionNode, weightCache);
                functionNode = functionNode.setBody(null, functionNode.getBody().setNeedsScope(null));
            }

            if (weight >= SPLIT_THRESHOLD) {
                functionNode = functionNode.setBody(null, splitBlock(functionNode.getBody(), functionNode));
                functionNode = functionNode.setFlag(null, FunctionNode.IS_SPLIT);
                weight = WeighNodes.weigh(functionNode.getBody(), weightCache);
            }
        }

        assert functionNode.getCompileUnit() == null : "compile unit already set for " + functionNode.getName();

        if (top) {
            assert outermostCompileUnit != null : "outermost compile unit is null";
            functionNode = functionNode.setCompileUnit(null, outermostCompileUnit);
            outermostCompileUnit.addWeight(weight + WeighNodes.FUNCTION_WEIGHT);
        } else {
            functionNode = functionNode.setCompileUnit(null, findUnit(weight));
        }

        final Block body = functionNode.getBody();
        final List<FunctionNode> dc = directChildren(functionNode);

        final Block newBody = (Block)body.accept(new NodeVisitor<LexicalContext>(new LexicalContext()) {
            @Override
            public boolean enterFunctionNode(final FunctionNode nestedFunction) {
                return dc.contains(nestedFunction);
            }

            @Override
            public Node leaveFunctionNode(final FunctionNode nestedFunction) {
                final FunctionNode split = new Splitter(compiler, nestedFunction, outermostCompileUnit).split(nestedFunction, false);
                lc.replace(nestedFunction, split);
                return split;
            }
        });
        functionNode = functionNode.setBody(null, newBody);

        assert functionNode.getCompileUnit() != null;

        return functionNode;
    }

    private static List<FunctionNode> directChildren(final FunctionNode functionNode) {
        final List<FunctionNode> dc = new ArrayList<>();
        functionNode.accept(new NodeVisitor<LexicalContext>(new LexicalContext()) {
            @Override
            public boolean enterFunctionNode(final FunctionNode child) {
                if (child == functionNode) {
                    return true;
                }
                if (lc.getParentFunction(child) == functionNode) {
                    dc.add(child);
                }
                return false;
            }
        });
        return dc;
    }

    /**
     * Override this logic to look up compile units in a different way
     * @param weight weight needed
     * @return compile unit
     */
    protected CompileUnit findUnit(final long weight) {
        return compiler.findUnit(weight);
    }

    /**
     * Split a block into sub methods.
     *
     * @param block Block or function to split.
     *
     * @return new weight for the resulting block.
     */
    private Block splitBlock(final Block block, final FunctionNode function) {

        final List<Statement> splits = new ArrayList<>();
        List<Statement> statements = new ArrayList<>();
        long statementsWeight = 0;

        for (final Statement statement : block.getStatements()) {
            final long weight = WeighNodes.weigh(statement, weightCache);

            if (statementsWeight + weight >= SPLIT_THRESHOLD || statement.isTerminal()) {
                if (!statements.isEmpty()) {
                    splits.add(createBlockSplitNode(block, function, statements, statementsWeight));
                    statements = new ArrayList<>();
                    statementsWeight = 0;
                }
            }

            if (statement.isTerminal()) {
                splits.add(statement);
            } else {
                statements.add(statement);
                statementsWeight += weight;
            }
        }

        if (!statements.isEmpty()) {
            splits.add(createBlockSplitNode(block, function, statements, statementsWeight));
        }

        return block.setStatements(lc, splits);
    }

    /**
     * Create a new split node from statements contained in a parent block.
     *
     * @param parent     Parent block.
     * @param statements Statements to include.
     *
     * @return New split node.
     */
    private SplitNode createBlockSplitNode(final Block parent, final FunctionNode function, final List<Statement> statements, final long weight) {
        final long   token      = parent.getToken();
        final int    finish     = parent.getFinish();
        final String name       = function.uniqueName(SPLIT_PREFIX.symbolName());

        final Block newBlock = new Block(token, finish, statements);

        return new SplitNode(name, newBlock, compiler.findUnit(weight + WeighNodes.FUNCTION_WEIGHT));
    }

    @Override
    public boolean enterBlock(final Block block) {
        if (block.isCatchBlock()) {
            return false;
        }

        final long weight = WeighNodes.weigh(block, weightCache);

        if (weight < SPLIT_THRESHOLD) {
            weightCache.put(block, weight);
            return false;
        }

        return true;
    }

    @Override
    public Node leaveBlock(final Block block) {
        assert !block.isCatchBlock();

        Block newBlock = block;

        // Block was heavier than SLIT_THRESHOLD in enter, but a sub-block may have
        // been split already, so weigh again before splitting.
        long weight = WeighNodes.weigh(block, weightCache);
        if (weight >= SPLIT_THRESHOLD) {
            final FunctionNode currentFunction = lc.getCurrentFunction();
            newBlock = splitBlock(block, currentFunction);
            weight   = WeighNodes.weigh(newBlock, weightCache);
            lc.setFlag(currentFunction, FunctionNode.IS_SPLIT);
        }
        weightCache.put(newBlock, weight);
        return newBlock;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Node leaveLiteralNode(final LiteralNode literal) {
        long weight = WeighNodes.weigh(literal);

        if (weight < SPLIT_THRESHOLD) {
            return literal;
        }

        final FunctionNode functionNode = lc.getCurrentFunction();

        lc.setFlag(functionNode, FunctionNode.IS_SPLIT);

        if (literal instanceof ArrayLiteralNode) {
            final ArrayLiteralNode arrayLiteralNode = (ArrayLiteralNode) literal;
            final Node[]           value            = arrayLiteralNode.getValue();
            final int[]            postsets         = arrayLiteralNode.getPostsets();
            final List<ArrayUnit>  units            = new ArrayList<>();

            long totalWeight = 0;
            int  lo          = 0;

            for (int i = 0; i < postsets.length; i++) {
                final int  postset = postsets[i];
                final Node element = value[postset];

                weight = WeighNodes.weigh(element);
                totalWeight += WeighNodes.AASTORE_WEIGHT + weight;

                if (totalWeight >= SPLIT_THRESHOLD) {
                    final CompileUnit unit = compiler.findUnit(totalWeight - weight);
                    units.add(new ArrayUnit(unit, lo, i));
                    lo = i;
                    totalWeight = weight;
                }
            }

            if (lo != postsets.length) {
                final CompileUnit unit = compiler.findUnit(totalWeight);
                units.add(new ArrayUnit(unit, lo, postsets.length));
            }

            return arrayLiteralNode.setUnits(lc, units);
        }

        return literal;
    }

    @Override
    public boolean enterFunctionNode(final FunctionNode node) {
        //only go into the function node for this splitter. any subfunctions are rejected
        return node == outermost;
    }
}

