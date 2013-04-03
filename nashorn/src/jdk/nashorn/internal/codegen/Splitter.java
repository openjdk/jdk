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
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import jdk.nashorn.internal.ir.Block;
import jdk.nashorn.internal.ir.BreakNode;
import jdk.nashorn.internal.ir.ContinueNode;
import jdk.nashorn.internal.ir.DoWhileNode;
import jdk.nashorn.internal.ir.ForNode;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.ir.FunctionNode.CompilationState;
import jdk.nashorn.internal.ir.LabelNode;
import jdk.nashorn.internal.ir.LexicalContext;
import jdk.nashorn.internal.ir.LiteralNode;
import jdk.nashorn.internal.ir.LiteralNode.ArrayLiteralNode;
import jdk.nashorn.internal.ir.LiteralNode.ArrayLiteralNode.ArrayUnit;
import jdk.nashorn.internal.ir.Node;
import jdk.nashorn.internal.ir.ReturnNode;
import jdk.nashorn.internal.ir.SplitNode;
import jdk.nashorn.internal.ir.SwitchNode;
import jdk.nashorn.internal.ir.WhileNode;
import jdk.nashorn.internal.ir.visitor.NodeOperatorVisitor;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.runtime.DebugLogger;
import jdk.nashorn.internal.runtime.Source;
import jdk.nashorn.internal.runtime.options.Options;

/**
 * Split the IR into smaller compile units.
 */
final class Splitter extends NodeVisitor {
    /** Current compiler. */
    private final Compiler compiler;

    /** IR to be broken down. */
    private final FunctionNode functionNode;

    /** Compile unit for the main script. */
    private final CompileUnit outermostCompileUnit;

    /** Cache for calculated block weights. */
    private final Map<Node, Long> weightCache = new HashMap<>();

    private final LexicalContext lexicalContext = new LexicalContext();

    /** Weight threshold for when to start a split. */
    public static final long SPLIT_THRESHOLD = Options.getIntProperty("nashorn.compiler.splitter.threshold", 32 * 1024);

    private static final DebugLogger LOG = Compiler.LOG;

    /**
     * Constructor.
     *
     * @param compiler              the compiler
     * @param functionNode          function node to split
     * @param outermostCompileUnit  compile unit for outermost function, if non-lazy this is the script's compile unit
     */
    public Splitter(final Compiler compiler, final FunctionNode functionNode, final CompileUnit outermostCompileUnit) {
        this.compiler             = compiler;
        this.functionNode         = functionNode;
        this.outermostCompileUnit = outermostCompileUnit;
    }

    /**
     * Execute the split
     */
    void split() {
        if (functionNode.isLazy()) {
            LOG.finest("Postponing split of '" + functionNode.getName() + "' as it's lazy");
            return;
        }

        LOG.finest("Initiating split of '" + functionNode.getName() + "'");

        long weight = WeighNodes.weigh(functionNode);

        if (weight >= SPLIT_THRESHOLD) {
            LOG.finest("Splitting '" + functionNode.getName() + "' as its weight " + weight + " exceeds split threshold " + SPLIT_THRESHOLD);

            functionNode.accept(this);

            if (functionNode.isSplit()) {
                // Weight has changed so weigh again, this time using block weight cache
                weight = WeighNodes.weigh(functionNode, weightCache);
            }

            if (weight >= SPLIT_THRESHOLD) {
                weight = splitBlock(functionNode, functionNode);
            }

            if (functionNode.isSplit()) {
                functionNode.accept(new SplitFlowAnalyzer());
            }
        }

        assert functionNode.getCompileUnit() == null : "compile unit already set";

        if (compiler.getFunctionNode() == functionNode) { //functionNode.isScript()) {
            assert outermostCompileUnit != null : "outermost compile unit is null";

            functionNode.setCompileUnit(outermostCompileUnit);
            outermostCompileUnit.addWeight(weight + WeighNodes.FUNCTION_WEIGHT);
        } else {
            functionNode.setCompileUnit(findUnit(weight));
        }

        // Recursively split nested functions
        functionNode.accept(new NodeOperatorVisitor() {
            @Override
            public Node enterFunctionNode(FunctionNode function) {
                if(function == functionNode) {
                    // Don't process outermost function (it was already processed) but descend into it to find nested
                    // functions.
                    return function;
                }
                // Process a nested function
                new Splitter(compiler, function, outermostCompileUnit).split();
                // Don't descend into a a nested function; Splitter.split() has taken care of nested-in-nested functions.
                return null;
            }
        });

        functionNode.setState(CompilationState.SPLIT);
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
    private long splitBlock(final Block block, final FunctionNode function) {
        functionNode.setIsSplit();

        final List<Node> splits = new ArrayList<>();
        List<Node> statements = new ArrayList<>();
        long statementsWeight = 0;

        for (final Node statement : block.getStatements()) {
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

        block.setStatements(splits);

        return WeighNodes.weigh(block, weightCache);
    }

    /**
     * Create a new split node from statements contained in a parent block.
     *
     * @param parent     Parent block.
     * @param statements Statements to include.
     *
     * @return New split node.
     */
    private SplitNode createBlockSplitNode(final Block parent, final FunctionNode function, final List<Node> statements, final long weight) {
        final Source source = parent.getSource();
        final long   token  = parent.getToken();
        final int    finish = parent.getFinish();
        final String name   = function.uniqueName(SPLIT_PREFIX.tag());

        final Block newBlock = new Block(source, token, finish);
        newBlock.setFrame(new Frame(parent.getFrame()));
        newBlock.setStatements(statements);

        final SplitNode splitNode = new SplitNode(name, functionNode, newBlock);

        splitNode.setCompileUnit(compiler.findUnit(weight + WeighNodes.FUNCTION_WEIGHT));

        return splitNode;
    }

    @Override
    public Node enterBlock(final Block block) {
        if (block.isCatchBlock()) {
            return null;
        }
        lexicalContext.push(block);

        final long weight = WeighNodes.weigh(block, weightCache);

        if (weight < SPLIT_THRESHOLD) {
            weightCache.put(block, weight);
            lexicalContext.pop(block);
            return null;
        }

        return block;
    }

    @Override
    public Node leaveBlock(final Block block) {
        assert !block.isCatchBlock();

        // Block was heavier than SLIT_THRESHOLD in enter, but a sub-block may have
        // been split already, so weigh again before splitting.
        long weight = WeighNodes.weigh(block, weightCache);
        if (weight >= SPLIT_THRESHOLD) {
            weight = splitBlock(block, lexicalContext.getFunction(block));
        }
        weightCache.put(block, weight);

        lexicalContext.pop(block);
        return block;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Node leaveLiteralNode(final LiteralNode literal) {
        long weight = WeighNodes.weigh(literal);

        if (weight < SPLIT_THRESHOLD) {
            return literal;
        }

        functionNode.setIsSplit();

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
                totalWeight += weight;

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

            arrayLiteralNode.setUnits(units);
        }

        return literal;
    }

    @Override
    public Node enterFunctionNode(final FunctionNode node) {
        if(node == functionNode && !node.isLazy()) {
            lexicalContext.push(node);
            node.visitStatements(this);
            lexicalContext.pop(node);
        }
        return null;
    }

    static class SplitFlowAnalyzer extends NodeVisitor {

        /** Stack of visited Split nodes, deepest node first. */
        private final Deque<SplitNode> splitStack;

        /** Map of possible jump targets to containing split node */
        private final Map<Node,SplitNode> targetNodes = new HashMap<>();

        SplitFlowAnalyzer() {
            this.splitStack = new LinkedList<>();
        }

        @Override
        public Node enterLabelNode(final LabelNode labelNode) {
            registerJumpTarget(labelNode.getBreakNode());
            registerJumpTarget(labelNode.getContinueNode());
            return labelNode;
        }

        @Override
        public Node enterWhileNode(final WhileNode whileNode) {
            registerJumpTarget(whileNode);
            return whileNode;
        }

        @Override
        public Node enterDoWhileNode(final DoWhileNode doWhileNode) {
            registerJumpTarget(doWhileNode);
            return doWhileNode;
        }

        @Override
        public Node enterForNode(final ForNode forNode) {
            registerJumpTarget(forNode);
            return forNode;
        }

        @Override
        public Node enterSwitchNode(final SwitchNode switchNode) {
            registerJumpTarget(switchNode);
            return switchNode;
        }

        @Override
        public Node enterReturnNode(final ReturnNode returnNode) {
            for (final SplitNode split : splitStack) {
                split.setHasReturn(true);
            }
            return returnNode;
        }

        @Override
        public Node enterContinueNode(final ContinueNode continueNode) {
            searchJumpTarget(continueNode.getTargetNode(), continueNode.getTargetLabel());
            return continueNode;
        }

        @Override
        public Node enterBreakNode(final BreakNode breakNode) {
            searchJumpTarget(breakNode.getTargetNode(), breakNode.getTargetLabel());
            return breakNode;
        }

        @Override
        public Node enterSplitNode(final SplitNode splitNode) {
            splitStack.addFirst(splitNode);
            return splitNode;
        }

        @Override
        public Node leaveSplitNode(final SplitNode splitNode) {
            assert splitNode == splitStack.peekFirst();
            splitStack.removeFirst();
            return splitNode;
        }

        /**
         * Register the split node containing a potential jump target.
         * @param targetNode a potential target node.
         */
        private void registerJumpTarget(final Node targetNode) {
            final SplitNode splitNode = splitStack.peekFirst();
            if (splitNode != null) {
                targetNodes.put(targetNode, splitNode);
            }
        }

        /**
         * Check if a jump target is outside the current split node and its parent split nodes.
         * @param targetNode the jump target node.
         * @param targetLabel the jump target label.
         */
        private void searchJumpTarget(final Node targetNode, final Label targetLabel) {

            final SplitNode targetSplit = targetNodes.get(targetNode);
            // Note that targetSplit may be null, indicating that targetNode is in top level method.
            // In this case we have to add the external jump target to all split nodes.

            for (final SplitNode split : splitStack) {
                if (split == targetSplit) {
                    break;
                }
                final List<Label> externalTargets = split.getExternalTargets();
                if (!externalTargets.contains(targetLabel)) {
                    split.addExternalTarget(targetLabel);
                }
            }
        }
    }
}

