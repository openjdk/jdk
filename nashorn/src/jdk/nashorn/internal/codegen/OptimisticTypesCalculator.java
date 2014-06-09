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

import static jdk.nashorn.internal.runtime.UnwarrantedOptimismException.isValid;

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Deque;
import jdk.nashorn.internal.IntDeque;
import jdk.nashorn.internal.ir.AccessNode;
import jdk.nashorn.internal.ir.BinaryNode;
import jdk.nashorn.internal.ir.CallNode;
import jdk.nashorn.internal.ir.CatchNode;
import jdk.nashorn.internal.ir.Expression;
import jdk.nashorn.internal.ir.ExpressionStatement;
import jdk.nashorn.internal.ir.ForNode;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.ir.FunctionNode.CompilationState;
import jdk.nashorn.internal.ir.IdentNode;
import jdk.nashorn.internal.ir.IfNode;
import jdk.nashorn.internal.ir.IndexNode;
import jdk.nashorn.internal.ir.JoinPredecessorExpression;
import jdk.nashorn.internal.ir.LexicalContext;
import jdk.nashorn.internal.ir.LoopNode;
import jdk.nashorn.internal.ir.Node;
import jdk.nashorn.internal.ir.Optimistic;
import jdk.nashorn.internal.ir.PropertyNode;
import jdk.nashorn.internal.ir.SplitNode;
import jdk.nashorn.internal.ir.Symbol;
import jdk.nashorn.internal.ir.TernaryNode;
import jdk.nashorn.internal.ir.UnaryNode;
import jdk.nashorn.internal.ir.VarNode;
import jdk.nashorn.internal.ir.WhileNode;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.parser.TokenType;
import jdk.nashorn.internal.runtime.ScriptObject;

/**
 * Assigns optimistic types to expressions that can have them. This class mainly contains logic for which expressions
 * must not ever be marked as optimistic, assigning narrowest non-invalidated types to program points from the
 * compilation environment, as well as initializing optimistic types of global properties for scripts.
 */
final class OptimisticTypesCalculator extends NodeVisitor<LexicalContext> {

    final Compiler compiler;

    // Per-function bit set of program points that must never be optimistic.
    final Deque<BitSet> neverOptimistic = new ArrayDeque<>();
    // Per-function depth of split nodes
    final IntDeque splitDepth = new IntDeque();

    OptimisticTypesCalculator(final Compiler compiler) {
        super(new LexicalContext());
        this.compiler = compiler;
    }

    @Override
    public boolean enterAccessNode(final AccessNode accessNode) {
        tagNeverOptimistic(accessNode.getBase());
        return true;
    }

    @Override
    public boolean enterPropertyNode(final PropertyNode propertyNode) {
        if(propertyNode.getKeyName().equals(ScriptObject.PROTO_PROPERTY_NAME)) {
            tagNeverOptimistic(propertyNode.getValue());
        }
        return super.enterPropertyNode(propertyNode);
    }

    @Override
    public boolean enterBinaryNode(final BinaryNode binaryNode) {
        if(binaryNode.isAssignment()) {
            final Expression lhs = binaryNode.lhs();
            if(!binaryNode.isSelfModifying()) {
                tagNeverOptimistic(lhs);
            }
            if(lhs instanceof IdentNode) {
                final Symbol symbol = ((IdentNode)lhs).getSymbol();
                // Assignment to internal symbols is never optimistic, except for self-assignment expressions
                if(symbol.isInternal() && !binaryNode.rhs().isSelfModifying()) {
                    tagNeverOptimistic(binaryNode.rhs());
                }
            }
        } else if(binaryNode.isTokenType(TokenType.INSTANCEOF)) {
            tagNeverOptimistic(binaryNode.lhs());
            tagNeverOptimistic(binaryNode.rhs());
        }
        return true;
    }

    @Override
    public boolean enterCallNode(final CallNode callNode) {
        tagNeverOptimistic(callNode.getFunction());
        return true;
    }

    @Override
    public boolean enterCatchNode(final CatchNode catchNode) {
        // Condition is never optimistic (always coerced to boolean).
        tagNeverOptimistic(catchNode.getExceptionCondition());
        return true;
    }

    @Override
    public boolean enterExpressionStatement(final ExpressionStatement expressionStatement) {
        final Expression expr = expressionStatement.getExpression();
        if(!expr.isSelfModifying()) {
            tagNeverOptimistic(expr);
        }
        return true;
    }

    @Override
    public boolean enterForNode(final ForNode forNode) {
        if(forNode.isForIn()) {
            // for..in has the iterable in its "modify"
            tagNeverOptimistic(forNode.getModify());
        } else {
            // Test is never optimistic (always coerced to boolean).
            tagNeverOptimisticLoopTest(forNode);
        }
        return true;
    }

    @Override
    public boolean enterFunctionNode(final FunctionNode functionNode) {
        if (!neverOptimistic.isEmpty() && compiler.isOnDemandCompilation()) {
            // This is a nested function, and we're doing on-demand compilation. In these compilations, we never descend
            // into nested functions.
            return false;
        }
        neverOptimistic.push(new BitSet());
        splitDepth.push(0);
        return true;
    }

    @Override
    public boolean enterIfNode(final IfNode ifNode) {
        // Test is never optimistic (always coerced to boolean).
        tagNeverOptimistic(ifNode.getTest());
        return true;
    }

    @Override
    public boolean enterIndexNode(final IndexNode indexNode) {
        tagNeverOptimistic(indexNode.getBase());
        return true;
    }

    @Override
    public boolean enterTernaryNode(final TernaryNode ternaryNode) {
        // Test is never optimistic (always coerced to boolean).
        tagNeverOptimistic(ternaryNode.getTest());
        return true;
    }

    @Override
    public boolean enterUnaryNode(final UnaryNode unaryNode) {
        if(unaryNode.isTokenType(TokenType.NOT) || unaryNode.isTokenType(TokenType.NEW)) {
            // Operand of boolean negation is never optimistic (always coerced to boolean).
            // Operand of "new" is never optimistic (always coerced to Object).
            tagNeverOptimistic(unaryNode.getExpression());
        }
        return true;
    }

    @Override
    public boolean enterSplitNode(final SplitNode splitNode) {
        splitDepth.getAndIncrement();
        return true;
    }

    @Override
    public Node leaveSplitNode(final SplitNode splitNode) {
        final int depth = splitDepth.decrementAndGet();
        assert depth >= 0;
        return splitNode;
    }

    @Override
    public boolean enterVarNode(final VarNode varNode) {
        tagNeverOptimistic(varNode.getName());
        return true;
    }

    @Override
    public boolean enterWhileNode(final WhileNode whileNode) {
        // Test is never optimistic (always coerced to boolean).
        tagNeverOptimisticLoopTest(whileNode);
        return true;
    }

    @Override
    protected Node leaveDefault(final Node node) {
        if(node instanceof Optimistic) {
            return leaveOptimistic((Optimistic)node);
        }
        return node;
    }

    @Override
    public Node leaveFunctionNode(final FunctionNode functionNode) {
        neverOptimistic.pop();
        final int lastSplitDepth = splitDepth.pop();
        assert lastSplitDepth == 0;
        return functionNode.setState(lc, CompilationState.OPTIMISTIC_TYPES_ASSIGNED);
    }

    @Override
    public Node leaveIdentNode(final IdentNode identNode) {
        if(inSplitNode()) {
            return identNode;
        }
        final Symbol symbol = identNode.getSymbol();
        if(symbol == null) {
            assert identNode.isPropertyName();
            return identNode;
        } else if(symbol.isBytecodeLocal()) {
            // Identifiers accessing bytecode local variables will never be optimistic, as type calculation phase over
            // them will always assign them statically provable types. Note that access to function parameters can still
            // be optimistic if the parameter needs to be in scope as it's used by a nested function.
            return identNode;
        } else if(symbol.isParam() && lc.getCurrentFunction().isVarArg()) {
            // Parameters in vararg methods are not optimistic; we always access them using Object getters.
            return identNode.setType(identNode.getMostPessimisticType());
        } else {
            assert symbol.isScope();
            return leaveOptimistic(identNode);
        }
    }

    private Expression leaveOptimistic(final Optimistic opt) {
        final int pp = opt.getProgramPoint();
        if(isValid(pp) && !inSplitNode() && !neverOptimistic.peek().get(pp)) {
            return (Expression)opt.setType(compiler.getOptimisticType(opt));
        }
        return (Expression)opt;
    }

    private void tagNeverOptimistic(final Expression expr) {
        if(expr instanceof Optimistic) {
            final int pp = ((Optimistic)expr).getProgramPoint();
            if(isValid(pp)) {
                neverOptimistic.peek().set(pp);
            }
        }
    }

    private void tagNeverOptimisticLoopTest(final LoopNode loopNode) {
        final JoinPredecessorExpression test = loopNode.getTest();
        if(test != null) {
            tagNeverOptimistic(test.getExpression());
        }
    }

    private boolean inSplitNode() {
        return splitDepth.peek() > 0;
    }
}
