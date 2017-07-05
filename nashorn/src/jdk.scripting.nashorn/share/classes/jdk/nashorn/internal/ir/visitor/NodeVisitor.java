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

package jdk.nashorn.internal.ir.visitor;

import jdk.nashorn.internal.ir.AccessNode;
import jdk.nashorn.internal.ir.BinaryNode;
import jdk.nashorn.internal.ir.Block;
import jdk.nashorn.internal.ir.BlockStatement;
import jdk.nashorn.internal.ir.BreakNode;
import jdk.nashorn.internal.ir.CallNode;
import jdk.nashorn.internal.ir.CaseNode;
import jdk.nashorn.internal.ir.CatchNode;
import jdk.nashorn.internal.ir.ContinueNode;
import jdk.nashorn.internal.ir.EmptyNode;
import jdk.nashorn.internal.ir.ExpressionStatement;
import jdk.nashorn.internal.ir.ForNode;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.ir.GetSplitState;
import jdk.nashorn.internal.ir.IdentNode;
import jdk.nashorn.internal.ir.IfNode;
import jdk.nashorn.internal.ir.IndexNode;
import jdk.nashorn.internal.ir.JoinPredecessorExpression;
import jdk.nashorn.internal.ir.JumpToInlinedFinally;
import jdk.nashorn.internal.ir.LabelNode;
import jdk.nashorn.internal.ir.LexicalContext;
import jdk.nashorn.internal.ir.LiteralNode;
import jdk.nashorn.internal.ir.Node;
import jdk.nashorn.internal.ir.ObjectNode;
import jdk.nashorn.internal.ir.PropertyNode;
import jdk.nashorn.internal.ir.ReturnNode;
import jdk.nashorn.internal.ir.RuntimeNode;
import jdk.nashorn.internal.ir.SetSplitState;
import jdk.nashorn.internal.ir.SplitNode;
import jdk.nashorn.internal.ir.SplitReturn;
import jdk.nashorn.internal.ir.SwitchNode;
import jdk.nashorn.internal.ir.TernaryNode;
import jdk.nashorn.internal.ir.ThrowNode;
import jdk.nashorn.internal.ir.TryNode;
import jdk.nashorn.internal.ir.UnaryNode;
import jdk.nashorn.internal.ir.VarNode;
import jdk.nashorn.internal.ir.WhileNode;
import jdk.nashorn.internal.ir.WithNode;

/**
 * Visitor used to navigate the IR.
 * @param <T> lexical context class used by this visitor
 */
public abstract class NodeVisitor<T extends LexicalContext> {
    /** lexical context in use */
    protected final T lc;

    /**
     * Constructor
     *
     * @param lc a custom lexical context
     */
    public NodeVisitor(final T lc) {
        this.lc = lc;
    }

    /**
     * Get the lexical context of this node visitor
     * @return lexical context
     */
    public T getLexicalContext() {
        return lc;
    }

    /**
     * Override this method to do a double inheritance pattern, e.g. avoid
     * using
     * <p>
     * if (x instanceof NodeTypeA) {
     *    ...
     * } else if (x instanceof NodeTypeB) {
     *    ...
     * } else {
     *    ...
     * }
     * <p>
     * Use a NodeVisitor instead, and this method contents forms the else case.
     *
     * @see NodeVisitor#leaveDefault(Node)
     * @param node the node to visit
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    protected boolean enterDefault(final Node node) {
        return true;
    }

    /**
     * Override this method to do a double inheritance pattern, e.g. avoid
     * using
     * <p>
     * if (x instanceof NodeTypeA) {
     *    ...
     * } else if (x instanceof NodeTypeB) {
     *    ...
     * } else {
     *    ...
     * }
     * <p>
     * Use a NodeVisitor instead, and this method contents forms the else case.
     *
     * @see NodeVisitor#enterDefault(Node)
     * @param node the node to visit
     * @return the node
     */
    protected Node leaveDefault(final Node node) {
        return node;
    }

    /**
     * Callback for entering an AccessNode
     *
     * @param  accessNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterAccessNode(final AccessNode accessNode) {
        return enterDefault(accessNode);
    }

    /**
     * Callback for entering an AccessNode
     *
     * @param  accessNode the node
     * @return processed node, null if traversal should end
     */
    public Node leaveAccessNode(final AccessNode accessNode) {
        return leaveDefault(accessNode);
    }

    /**
     * Callback for entering a Block
     *
     * @param  block     the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterBlock(final Block block) {
        return enterDefault(block);
    }

    /**
     * Callback for leaving a Block
     *
     * @param  block the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveBlock(final Block block) {
        return leaveDefault(block);
    }

    /**
     * Callback for entering a BinaryNode
     *
     * @param  binaryNode  the node
     * @return processed   node
     */
    public boolean enterBinaryNode(final BinaryNode binaryNode) {
        return enterDefault(binaryNode);
    }

    /**
     * Callback for leaving a BinaryNode
     *
     * @param  binaryNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveBinaryNode(final BinaryNode binaryNode) {
        return leaveDefault(binaryNode);
    }

    /**
     * Callback for entering a BreakNode
     *
     * @param  breakNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterBreakNode(final BreakNode breakNode) {
        return enterDefault(breakNode);
    }

    /**
     * Callback for leaving a BreakNode
     *
     * @param  breakNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveBreakNode(final BreakNode breakNode) {
        return leaveDefault(breakNode);
    }

    /**
     * Callback for entering a CallNode
     *
     * @param  callNode  the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterCallNode(final CallNode callNode) {
        return enterDefault(callNode);
    }

    /**
     * Callback for leaving a CallNode
     *
     * @param  callNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveCallNode(final CallNode callNode) {
        return leaveDefault(callNode);
    }

    /**
     * Callback for entering a CaseNode
     *
     * @param  caseNode  the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterCaseNode(final CaseNode caseNode) {
        return enterDefault(caseNode);
    }

    /**
     * Callback for leaving a CaseNode
     *
     * @param  caseNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveCaseNode(final CaseNode caseNode) {
        return leaveDefault(caseNode);
    }

    /**
     * Callback for entering a CatchNode
     *
     * @param  catchNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterCatchNode(final CatchNode catchNode) {
        return enterDefault(catchNode);
    }

    /**
     * Callback for leaving a CatchNode
     *
     * @param  catchNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveCatchNode(final CatchNode catchNode) {
        return leaveDefault(catchNode);
    }

    /**
     * Callback for entering a ContinueNode
     *
     * @param  continueNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterContinueNode(final ContinueNode continueNode) {
        return enterDefault(continueNode);
    }

    /**
     * Callback for leaving a ContinueNode
     *
     * @param  continueNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveContinueNode(final ContinueNode continueNode) {
        return leaveDefault(continueNode);
    }

    /**
     * Callback for entering an EmptyNode
     *
     * @param  emptyNode   the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterEmptyNode(final EmptyNode emptyNode) {
        return enterDefault(emptyNode);
    }

    /**
     * Callback for leaving an EmptyNode
     *
     * @param  emptyNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveEmptyNode(final EmptyNode emptyNode) {
        return leaveDefault(emptyNode);
    }

    /**
     * Callback for entering an ExpressionStatement
     *
     * @param  expressionStatement the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterExpressionStatement(final ExpressionStatement expressionStatement) {
        return enterDefault(expressionStatement);
    }

    /**
     * Callback for leaving an ExpressionStatement
     *
     * @param  expressionStatement the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveExpressionStatement(final ExpressionStatement expressionStatement) {
        return leaveDefault(expressionStatement);
    }

    /**
     * Callback for entering a BlockStatement
     *
     * @param  blockStatement the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterBlockStatement(final BlockStatement blockStatement) {
        return enterDefault(blockStatement);
    }

    /**
     * Callback for leaving a BlockStatement
     *
     * @param  blockStatement the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveBlockStatement(final BlockStatement blockStatement) {
        return leaveDefault(blockStatement);
    }

    /**
     * Callback for entering a ForNode
     *
     * @param  forNode   the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterForNode(final ForNode forNode) {
        return enterDefault(forNode);
    }

    /**
     * Callback for leaving a ForNode
     *
     * @param  forNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveForNode(final ForNode forNode) {
        return leaveDefault(forNode);
    }

    /**
     * Callback for entering a FunctionNode
     *
     * @param  functionNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterFunctionNode(final FunctionNode functionNode) {
        return enterDefault(functionNode);
    }

    /**
     * Callback for leaving a FunctionNode
     *
     * @param  functionNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveFunctionNode(final FunctionNode functionNode) {
        return leaveDefault(functionNode);
    }

    /**
     * Callback for entering a {@link GetSplitState}.
     *
     * @param  getSplitState the get split state expression
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterGetSplitState(final GetSplitState getSplitState) {
        return enterDefault(getSplitState);
    }

    /**
     * Callback for leaving a {@link GetSplitState}.
     *
     * @param  getSplitState the get split state expression
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveGetSplitState(final GetSplitState getSplitState) {
        return leaveDefault(getSplitState);
    }

    /**
     * Callback for entering an IdentNode
     *
     * @param  identNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterIdentNode(final IdentNode identNode) {
        return enterDefault(identNode);
    }

    /**
     * Callback for leaving an IdentNode
     *
     * @param  identNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveIdentNode(final IdentNode identNode) {
        return leaveDefault(identNode);
    }

    /**
     * Callback for entering an IfNode
     *
     * @param  ifNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterIfNode(final IfNode ifNode) {
        return enterDefault(ifNode);
    }

    /**
     * Callback for leaving an IfNode
     *
     * @param  ifNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveIfNode(final IfNode ifNode) {
        return leaveDefault(ifNode);
    }

    /**
     * Callback for entering an IndexNode
     *
     * @param  indexNode  the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterIndexNode(final IndexNode indexNode) {
        return enterDefault(indexNode);
    }

    /**
     * Callback for leaving an IndexNode
     *
     * @param  indexNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveIndexNode(final IndexNode indexNode) {
        return leaveDefault(indexNode);
    }

    /**
     * Callback for entering a JumpToInlinedFinally
     *
     * @param  jumpToInlinedFinally the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterJumpToInlinedFinally(final JumpToInlinedFinally jumpToInlinedFinally) {
        return enterDefault(jumpToInlinedFinally);
    }

    /**
     * Callback for leaving a JumpToInlinedFinally
     *
     * @param  jumpToInlinedFinally the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveJumpToInlinedFinally(final JumpToInlinedFinally jumpToInlinedFinally) {
        return leaveDefault(jumpToInlinedFinally);
    }

    /**
     * Callback for entering a LabelNode
     *
     * @param  labelNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterLabelNode(final LabelNode labelNode) {
        return enterDefault(labelNode);
    }

    /**
     * Callback for leaving a LabelNode
     *
     * @param  labelNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveLabelNode(final LabelNode labelNode) {
        return leaveDefault(labelNode);
    }

    /**
     * Callback for entering a LiteralNode
     *
     * @param  literalNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterLiteralNode(final LiteralNode<?> literalNode) {
        return enterDefault(literalNode);
    }

    /**
     * Callback for leaving a LiteralNode
     *
     * @param  literalNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveLiteralNode(final LiteralNode<?> literalNode) {
        return leaveDefault(literalNode);
    }

    /**
     * Callback for entering an ObjectNode
     *
     * @param  objectNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterObjectNode(final ObjectNode objectNode) {
        return enterDefault(objectNode);
    }

    /**
     * Callback for leaving an ObjectNode
     *
     * @param  objectNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveObjectNode(final ObjectNode objectNode) {
        return leaveDefault(objectNode);
    }

    /**
     * Callback for entering a PropertyNode
     *
     * @param  propertyNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterPropertyNode(final PropertyNode propertyNode) {
        return enterDefault(propertyNode);
    }

    /**
     * Callback for leaving a PropertyNode
     *
     * @param  propertyNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leavePropertyNode(final PropertyNode propertyNode) {
        return leaveDefault(propertyNode);
    }

    /**
     * Callback for entering a ReturnNode
     *
     * @param  returnNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterReturnNode(final ReturnNode returnNode) {
        return enterDefault(returnNode);
    }

    /**
     * Callback for leaving a ReturnNode
     *
     * @param  returnNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveReturnNode(final ReturnNode returnNode) {
        return leaveDefault(returnNode);
    }

    /**
     * Callback for entering a RuntimeNode
     *
     * @param  runtimeNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterRuntimeNode(final RuntimeNode runtimeNode) {
        return enterDefault(runtimeNode);
    }

    /**
     * Callback for leaving a RuntimeNode
     *
     * @param  runtimeNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveRuntimeNode(final RuntimeNode runtimeNode) {
        return leaveDefault(runtimeNode);
    }

    /**
     * Callback for entering a {@link SetSplitState}.
     *
     * @param  setSplitState the set split state statement
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterSetSplitState(final SetSplitState setSplitState) {
        return enterDefault(setSplitState);
    }

    /**
     * Callback for leaving a {@link SetSplitState}.
     *
     * @param  setSplitState the set split state expression
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveSetSplitState(final SetSplitState setSplitState) {
        return leaveDefault(setSplitState);
    }

    /**
     * Callback for entering a SplitNode
     *
     * @param  splitNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterSplitNode(final SplitNode splitNode) {
        return enterDefault(splitNode);
    }

    /**
     * Callback for leaving a SplitNode
     *
     * @param  splitNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveSplitNode(final SplitNode splitNode) {
        return leaveDefault(splitNode);
    }

    /**
     * Callback for entering a SplitReturn
     *
     * @param  splitReturn the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterSplitReturn(final SplitReturn splitReturn) {
        return enterDefault(splitReturn);
    }

    /**
     * Callback for leaving a SplitReturn
     *
     * @param  splitReturn the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveSplitReturn(final SplitReturn splitReturn) {
        return leaveDefault(splitReturn);
    }

    /**
     * Callback for entering a SwitchNode
     *
     * @param  switchNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterSwitchNode(final SwitchNode switchNode) {
        return enterDefault(switchNode);
    }

    /**
     * Callback for leaving a SwitchNode
     *
     * @param  switchNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveSwitchNode(final SwitchNode switchNode) {
        return leaveDefault(switchNode);
    }

    /**
     * Callback for entering a TernaryNode
     *
     * @param  ternaryNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterTernaryNode(final TernaryNode ternaryNode) {
        return enterDefault(ternaryNode);
    }

    /**
     * Callback for leaving a TernaryNode
     *
     * @param  ternaryNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveTernaryNode(final TernaryNode ternaryNode) {
        return leaveDefault(ternaryNode);
    }

    /**
     * Callback for entering a ThrowNode
     *
     * @param  throwNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterThrowNode(final ThrowNode throwNode) {
        return enterDefault(throwNode);
    }

    /**
     * Callback for leaving a ThrowNode
     *
     * @param  throwNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveThrowNode(final ThrowNode throwNode) {
        return leaveDefault(throwNode);
    }

    /**
     * Callback for entering a TryNode
     *
     * @param  tryNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterTryNode(final TryNode tryNode) {
        return enterDefault(tryNode);
    }

    /**
     * Callback for leaving a TryNode
     *
     * @param  tryNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveTryNode(final TryNode tryNode) {
        return leaveDefault(tryNode);
    }

    /**
     * Callback for entering a UnaryNode
     *
     * @param  unaryNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterUnaryNode(final UnaryNode unaryNode) {
        return enterDefault(unaryNode);
    }

    /**
     * Callback for leaving a UnaryNode
     *
     * @param  unaryNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveUnaryNode(final UnaryNode unaryNode) {
        return leaveDefault(unaryNode);
    }

    /**
     * Callback for entering a {@link JoinPredecessorExpression}.
     *
     * @param  expr the join predecessor expression
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterJoinPredecessorExpression(final JoinPredecessorExpression expr) {
        return enterDefault(expr);
    }

    /**
     * Callback for leaving a {@link JoinPredecessorExpression}.
     *
     * @param  expr the join predecessor expression
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveJoinPredecessorExpression(final JoinPredecessorExpression expr) {
        return leaveDefault(expr);
    }


    /**
     * Callback for entering a VarNode
     *
     * @param  varNode   the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterVarNode(final VarNode varNode) {
        return enterDefault(varNode);
    }

    /**
     * Callback for leaving a VarNode
     *
     * @param  varNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveVarNode(final VarNode varNode) {
        return leaveDefault(varNode);
    }

    /**
     * Callback for entering a WhileNode
     *
     * @param  whileNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterWhileNode(final WhileNode whileNode) {
        return enterDefault(whileNode);
    }

    /**
     * Callback for leaving a WhileNode
     *
     * @param  whileNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveWhileNode(final WhileNode whileNode) {
        return leaveDefault(whileNode);
    }

    /**
     * Callback for entering a WithNode
     *
     * @param  withNode  the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterWithNode(final WithNode withNode) {
        return enterDefault(withNode);
    }

    /**
     * Callback for leaving a WithNode
     *
     * @param  withNode  the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveWithNode(final WithNode withNode) {
        return leaveDefault(withNode);
    }


}
