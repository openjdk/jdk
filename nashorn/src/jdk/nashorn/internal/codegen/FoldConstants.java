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

import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.BinaryNode;
import jdk.nashorn.internal.ir.Block;
import jdk.nashorn.internal.ir.EmptyNode;
import jdk.nashorn.internal.ir.ExecuteNode;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.ir.FunctionNode.CompilationState;
import jdk.nashorn.internal.ir.IfNode;
import jdk.nashorn.internal.ir.LexicalContext;
import jdk.nashorn.internal.ir.LiteralNode;
import jdk.nashorn.internal.ir.LiteralNode.ArrayLiteralNode;
import jdk.nashorn.internal.ir.Node;
import jdk.nashorn.internal.ir.TernaryNode;
import jdk.nashorn.internal.ir.UnaryNode;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.runtime.DebugLogger;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.ScriptRuntime;

/**
 * Simple constant folding pass, executed before IR is starting to be lowered.
 */
final class FoldConstants extends NodeVisitor<LexicalContext> {

    private static final DebugLogger LOG = new DebugLogger("fold");

    FoldConstants() {
        super(new LexicalContext());
    }

    @Override
    public Node leaveUnaryNode(final UnaryNode unaryNode) {
        final LiteralNode<?> literalNode = new UnaryNodeConstantEvaluator(unaryNode).eval();
        if (literalNode != null) {
            LOG.info("Unary constant folded ", unaryNode, " to ", literalNode);
            return literalNode;
        }
        return unaryNode;
    }

    @Override
    public Node leaveBinaryNode(final BinaryNode binaryNode) {
        final LiteralNode<?> literalNode = new BinaryNodeConstantEvaluator(binaryNode).eval();
        if (literalNode != null) {
            LOG.info("Binary constant folded ", binaryNode, " to ", literalNode);
            return literalNode;
        }
        return binaryNode;
    }

    @Override
    public boolean enterFunctionNode(final FunctionNode functionNode) {
        return !functionNode.isLazy();
    }

    @Override
    public Node leaveFunctionNode(final FunctionNode functionNode) {
        return functionNode.setState(lc, CompilationState.CONSTANT_FOLDED);
    }

    @Override
    public Node leaveIfNode(final IfNode ifNode) {
        final Node test = ifNode.getTest();
        if (test instanceof LiteralNode) {
            final Block shortCut = ((LiteralNode<?>)test).isTrue() ? ifNode.getPass() : ifNode.getFail();
            if (shortCut != null) {
                return new ExecuteNode(shortCut.getLineNumber(), shortCut.getToken(), shortCut.getFinish(), shortCut);
            }
            return new EmptyNode(ifNode);
        }
        return ifNode;
    }

    @Override
    public Node leaveTernaryNode(final TernaryNode ternaryNode) {
        final Node test = ternaryNode.lhs();
        if (test instanceof LiteralNode) {
            return ((LiteralNode<?>)test).isTrue() ? ternaryNode.rhs() : ternaryNode.third();
        }
        return ternaryNode;
    }

    /**
     * Helper class to evaluate constant expressions at compile time This is
     * also a simplifier used by BinaryNode visits, UnaryNode visits and
     * conversions.
     */
    abstract static class ConstantEvaluator<T extends Node> {
        protected T            parent;
        protected final long   token;
        protected final int    finish;

        protected ConstantEvaluator(final T parent) {
            this.parent = parent;
            this.token  = parent.getToken();
            this.finish = parent.getFinish();
        }

        /**
         * Returns a literal node that replaces the given parent node, or null if replacement
         * is impossible
         * @return the literal node
         */
        protected abstract LiteralNode<?> eval();
    }

    private static class UnaryNodeConstantEvaluator extends ConstantEvaluator<UnaryNode> {
        UnaryNodeConstantEvaluator(final UnaryNode parent) {
            super(parent);
        }

        @Override
        protected LiteralNode<?> eval() {
            final Node rhsNode = parent.rhs();

            if (!(rhsNode instanceof LiteralNode)) {
                return null;
            }

            if (rhsNode instanceof ArrayLiteralNode) {
                return null;
            }

            final LiteralNode<?> rhs = (LiteralNode<?>)rhsNode;
            final boolean rhsInteger = rhs.getType().isInteger();

            LiteralNode<?> literalNode;

            switch (parent.tokenType()) {
            case ADD:
                if (rhsInteger) {
                    literalNode = LiteralNode.newInstance(token, finish, rhs.getInt32());
                } else {
                    literalNode = LiteralNode.newInstance(token, finish, rhs.getNumber());
                }
                break;
            case SUB:
                if (rhsInteger && rhs.getInt32() != 0) { // @see test/script/basic/minuszero.js
                    literalNode = LiteralNode.newInstance(token, finish, -rhs.getInt32());
                } else {
                    literalNode = LiteralNode.newInstance(token, finish, -rhs.getNumber());
                }
                break;
            case NOT:
                literalNode = LiteralNode.newInstance(token, finish, !rhs.getBoolean());
                break;
            case BIT_NOT:
                literalNode = LiteralNode.newInstance(token, finish, ~rhs.getInt32());
                break;
            default:
                return null;
            }

            return literalNode;
        }
    }

    //TODO add AND and OR with one constant parameter (bitwise)
    private static class BinaryNodeConstantEvaluator extends ConstantEvaluator<BinaryNode> {
        BinaryNodeConstantEvaluator(final BinaryNode parent) {
            super(parent);
        }

        @Override
        protected LiteralNode<?> eval() {
            LiteralNode<?> result;

            result = reduceTwoLiterals();
            if (result != null) {
                return result;
            }

            result = reduceOneLiteral();
            if (result != null) {
                return result;
            }

            return null;
        }

        @SuppressWarnings("static-method")
        private LiteralNode<?> reduceOneLiteral() {
            //TODO handle patterns like AND, OR, numeric ops that can be strength reduced but not replaced by a single literal node etc
            return null;
        }

        private LiteralNode<?> reduceTwoLiterals() {
            if (!(parent.lhs() instanceof LiteralNode && parent.rhs() instanceof LiteralNode)) {
                return null;
            }

            final LiteralNode<?> lhs = (LiteralNode<?>)parent.lhs();
            final LiteralNode<?> rhs = (LiteralNode<?>)parent.rhs();

            if (lhs instanceof ArrayLiteralNode || rhs instanceof ArrayLiteralNode) {
                return null;
            }

            final Type widest = Type.widest(lhs.getType(), rhs.getType());

            boolean isInteger = widest.isInteger();
            boolean isLong    = widest.isLong();

            double value;

            switch (parent.tokenType()) {
            case DIV:
                value = lhs.getNumber() / rhs.getNumber();
                break;
            case ADD:
                if ((lhs.isString() || rhs.isNumeric()) && (rhs.isString() || rhs.isNumeric())) {
                    Object res = ScriptRuntime.ADD(lhs.getObject(), rhs.getObject());
                    if (res instanceof Number) {
                        value = ((Number)res).doubleValue();
                        break;
                    }
                    assert res instanceof CharSequence : res + " was not a CharSequence, it was a " + res.getClass();
                    return LiteralNode.newInstance(token, finish, res.toString());
                }
                return null;
            case MUL:
                value = lhs.getNumber() * rhs.getNumber();
                break;
            case MOD:
                value = lhs.getNumber() % rhs.getNumber();
                break;
            case SUB:
                value = lhs.getNumber() - rhs.getNumber();
                break;
            case SHR:
                return LiteralNode.newInstance(token, finish, (lhs.getInt32() >>> rhs.getInt32()) & JSType.MAX_UINT);
            case SAR:
                return LiteralNode.newInstance(token, finish, lhs.getInt32() >> rhs.getInt32());
            case SHL:
                return LiteralNode.newInstance(token, finish, lhs.getInt32() << rhs.getInt32());
            case BIT_XOR:
                return LiteralNode.newInstance(token, finish, lhs.getInt32() ^ rhs.getInt32());
            case BIT_AND:
                return LiteralNode.newInstance(token, finish, lhs.getInt32() & rhs.getInt32());
            case BIT_OR:
                return LiteralNode.newInstance(token, finish, lhs.getInt32() | rhs.getInt32());
            case GE:
                return LiteralNode.newInstance(token, finish, ScriptRuntime.GE(lhs.getObject(), rhs.getObject()));
            case LE:
                return LiteralNode.newInstance(token, finish, ScriptRuntime.LE(lhs.getObject(), rhs.getObject()));
            case GT:
                return LiteralNode.newInstance(token, finish, ScriptRuntime.GT(lhs.getObject(), rhs.getObject()));
            case LT:
                return LiteralNode.newInstance(token, finish, ScriptRuntime.LT(lhs.getObject(), rhs.getObject()));
            case NE:
                return LiteralNode.newInstance(token, finish, ScriptRuntime.NE(lhs.getObject(), rhs.getObject()));
            case NE_STRICT:
                return LiteralNode.newInstance(token, finish, ScriptRuntime.NE_STRICT(lhs.getObject(), rhs.getObject()));
            case EQ:
                return LiteralNode.newInstance(token, finish, ScriptRuntime.EQ(lhs.getObject(), rhs.getObject()));
            case EQ_STRICT:
                return LiteralNode.newInstance(token, finish, ScriptRuntime.EQ_STRICT(lhs.getObject(), rhs.getObject()));
            default:
                return null;
            }

            isInteger &= value != 0.0 && JSType.isRepresentableAsInt(value);
            isLong    &= value != 0.0 && JSType.isRepresentableAsLong(value);

            if (isInteger) {
                return LiteralNode.newInstance(token, finish, (int)value);
            } else if (isLong) {
                return LiteralNode.newInstance(token, finish, (long)value);
            }

            return LiteralNode.newInstance(token, finish, value);
        }
    }
}
