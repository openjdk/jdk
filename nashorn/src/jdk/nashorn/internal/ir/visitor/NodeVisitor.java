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

import jdk.nashorn.internal.codegen.CompileUnit;
import jdk.nashorn.internal.codegen.MethodEmitter;
import jdk.nashorn.internal.ir.AccessNode;
import jdk.nashorn.internal.ir.BinaryNode;
import jdk.nashorn.internal.ir.Block;
import jdk.nashorn.internal.ir.BreakNode;
import jdk.nashorn.internal.ir.CallNode;
import jdk.nashorn.internal.ir.CaseNode;
import jdk.nashorn.internal.ir.CatchNode;
import jdk.nashorn.internal.ir.ContinueNode;
import jdk.nashorn.internal.ir.DoWhileNode;
import jdk.nashorn.internal.ir.EmptyNode;
import jdk.nashorn.internal.ir.ExecuteNode;
import jdk.nashorn.internal.ir.ForNode;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.ir.IdentNode;
import jdk.nashorn.internal.ir.IfNode;
import jdk.nashorn.internal.ir.IndexNode;
import jdk.nashorn.internal.ir.LabelNode;
import jdk.nashorn.internal.ir.LineNumberNode;
import jdk.nashorn.internal.ir.LiteralNode;
import jdk.nashorn.internal.ir.Node;
import jdk.nashorn.internal.ir.ObjectNode;
import jdk.nashorn.internal.ir.PropertyNode;
import jdk.nashorn.internal.ir.ReturnNode;
import jdk.nashorn.internal.ir.RuntimeNode;
import jdk.nashorn.internal.ir.SplitNode;
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
 */
public abstract class NodeVisitor {
    /** Current functionNode. */
    private FunctionNode currentFunctionNode;

    /** Current compile unit used for class generation. */
    private CompileUnit compileUnit;

    /**
     * Current method visitor used for method generation.
     * <p>
     * TODO: protected is just for convenience and readability, so that
     * subclasses can directly use 'method' - might want to change that
     */
    protected MethodEmitter method;

    /** Current block. */
    private Block currentBlock;

    /**
     * Constructor.
     */
    public NodeVisitor() {
        this(null, null);
    }

    /**
     * Constructor
     *
     * @param compileUnit compile unit for this node visitor
     * @param method method emitter for this node visitor
     */
    public NodeVisitor(final CompileUnit compileUnit, final MethodEmitter method) {
        super();

        this.compileUnit = compileUnit;
        this.method      = method;
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
     * @return the node
     */
    protected Node enterDefault(final Node node) {
        return node;
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
     * @return processed node, null if traversal should end, null if traversal should end
     */
    public Node enterAccessNode(final AccessNode accessNode) {
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
     * @return processed node, null if traversal should end
     */
    public Node enterBlock(final Block block) {
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
    public Node enterBinaryNode(final BinaryNode binaryNode) {
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
     * @return processed node, null if traversal should end
     */
    public Node enterBreakNode(final BreakNode breakNode) {
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
     * @return processed node, null if traversal should end
     */
    public Node enterCallNode(final CallNode callNode) {
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
     * @return processed node, null if traversal should end
     */
    public Node enterCaseNode(final CaseNode caseNode) {
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
     * @return processed node, null if traversal should end
     */
    public Node enterCatchNode(final CatchNode catchNode) {
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
     * @return processed node, null if traversal should end
     */
    public Node enterContinueNode(final ContinueNode continueNode) {
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
     * Callback for entering a DoWhileNode
     *
     * @param  doWhileNode the node
     * @return processed   node
     */
    public Node enterDoWhileNode(final DoWhileNode doWhileNode) {
        return enterDefault(doWhileNode);
    }

    /**
     * Callback for leaving a DoWhileNode
     *
     * @param  doWhileNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveDoWhileNode(final DoWhileNode doWhileNode) {
        return leaveDefault(doWhileNode);
    }

    /**
     * Callback for entering an EmptyNode
     *
     * @param  emptyNode   the node
     * @return processed   node
     */
    public Node enterEmptyNode(final EmptyNode emptyNode) {
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
     * Callback for entering an ExecuteNode
     *
     * @param  executeNode the node
     * @return processed node, null if traversal should end
     */
    public Node enterExecuteNode(final ExecuteNode executeNode) {
        return enterDefault(executeNode);
    }

    /**
     * Callback for leaving an ExecuteNode
     *
     * @param  executeNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveExecuteNode(final ExecuteNode executeNode) {
        return leaveDefault(executeNode);
    }

    /**
     * Callback for entering a ForNode
     *
     * @param  forNode   the node
     * @return processed node, null if traversal should end
     */
    public Node enterForNode(final ForNode forNode) {
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
     * @return processed    node
     */
    public Node enterFunctionNode(final FunctionNode functionNode) {
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
     * Callback for entering an IdentNode
     *
     * @param  identNode the node
     * @return processed node, null if traversal should end
     */
    public Node enterIdentNode(final IdentNode identNode) {
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
     * @param  ifNode    the node
     * @return processed node, null if traversal should end
     */
    public Node enterIfNode(final IfNode ifNode) {
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
     * @return processed node, null if traversal should end
     */
    public Node enterIndexNode(final IndexNode indexNode) {
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
     * Callback for entering a LabelNode
     *
     * @param  labelNode the node
     * @return processed node, null if traversal should end
     */
    public Node enterLabelNode(final LabelNode labelNode) {
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
     * Callback for entering a LineNumberNode
     *
     * @param  lineNumberNode the node
     * @return processed node, null if traversal should end
     */
    public Node enterLineNumberNode(final LineNumberNode lineNumberNode) {
        return enterDefault(lineNumberNode);
    }

    /**
     * Callback for leaving a LineNumberNode
     *
     * @param  lineNumberNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveLineNumberNode(final LineNumberNode lineNumberNode) {
        return leaveDefault(lineNumberNode);
    }

    /**
     * Callback for entering a LiteralNode
     *
     * @param  literalNode the node
     * @return processed   node
     */
    public Node enterLiteralNode(final LiteralNode<?> literalNode) {
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
     * @return processed  node
     */
    public Node enterObjectNode(final ObjectNode objectNode) {
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
     * @return processed node, null if traversal should end
     */
    public Node enterPropertyNode(final PropertyNode propertyNode) {
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
     * @return processed node, null if traversal should end
     */
    public Node enterReturnNode(final ReturnNode returnNode) {
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
     * @return processed node, null if traversal should end
     */
    public Node enterRuntimeNode(final RuntimeNode runtimeNode) {
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
     * Callback for entering a SplitNode
     *
     * @param  splitNode the node
     * @return processed node, null if traversal should end
     */
    public Node enterSplitNode(final SplitNode splitNode) {
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
     * Callback for entering a SwitchNode
     *
     * @param  switchNode the node
     * @return processed node, null if traversal should end
     */
    public Node enterSwitchNode(final SwitchNode switchNode) {
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
     * @return processed node, null if traversal should end
     */
    public Node enterTernaryNode(final TernaryNode ternaryNode) {
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
     * @return processed node, null if traversal should end
     */
    public Node enterThrowNode(final ThrowNode throwNode) {
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
     * @return processed node, null if traversal should end
     */
    public Node enterTryNode(final TryNode tryNode) {
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
     * @return processed node, null if traversal should end
     */
    public Node enterUnaryNode(final UnaryNode unaryNode) {
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
     * Callback for entering a VarNode
     *
     * @param  varNode   the node
     * @return processed node, null if traversal should end
     */
    public Node enterVarNode(final VarNode varNode) {
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
     * @return processed node, null if traversal should end
     */
    public Node enterWhileNode(final WhileNode whileNode) {
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
     * @return processed node, null if traversal should end
     */
    public Node enterWithNode(final WithNode withNode) {
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

    /**
     * Get the current function node for this NodeVisitor
     * @see FunctionNode
     * @return the function node being visited
     */
    public FunctionNode getCurrentFunctionNode() {
        return currentFunctionNode;
    }

    /**
     * Reset the current function node being visited for this NodeVisitor
     * @see FunctionNode
     * @param currentFunctionNode a new function node to traverse
     */
    public void setCurrentFunctionNode(final FunctionNode currentFunctionNode) {
        this.currentFunctionNode = currentFunctionNode;
    }

    /**
     * Get the current compile unit for this NodeVisitor
     * @see CompileUnit
     * @return a compile unit, or null if not a compiling NodeVisitor
     */
    public CompileUnit getCurrentCompileUnit() {
        return compileUnit;
    }

    /**
     * Set the current compile unit for this NodeVisitor
     * @see CompileUnit
     * @param compileUnit a new compile unit
     */
    public void setCurrentCompileUnit(final CompileUnit compileUnit) {
        this.compileUnit = compileUnit;
    }

    /**
     * Get the current method emitter for this NodeVisitor
     * @see MethodEmitter
     * @return the method emitter
     */
    public MethodEmitter getCurrentMethodEmitter() {
        return method;
    }

    /**
     * Reset the current method emitter for this NodeVisitor
     * @see MethodEmitter
     * @param method a new method emitter
     */
    public void setCurrentMethodEmitter(final MethodEmitter method) {
        this.method = method;
    }

    /**
     * Get the current Block being traversed for this NodeVisitor
     * @return the current block
     */
    public Block getCurrentBlock() {
        return currentBlock;
    }

    /**
     * Reset the Block to be traversed for this NodeVisitor
     * @param currentBlock the new current block
     */
    public void setCurrentBlock(final Block currentBlock) {
        this.currentBlock = currentBlock;
    }

}
