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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import jdk.nashorn.internal.codegen.types.Range;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.Assignment;
import jdk.nashorn.internal.ir.BinaryNode;
import jdk.nashorn.internal.ir.Expression;
import jdk.nashorn.internal.ir.ForNode;
import jdk.nashorn.internal.ir.IdentNode;
import jdk.nashorn.internal.ir.LexicalContext;
import jdk.nashorn.internal.ir.LiteralNode;
import jdk.nashorn.internal.ir.LiteralNode.ArrayLiteralNode;
import jdk.nashorn.internal.ir.LoopNode;
import jdk.nashorn.internal.ir.Node;
import jdk.nashorn.internal.ir.RuntimeNode;
import jdk.nashorn.internal.ir.Symbol;
import jdk.nashorn.internal.ir.UnaryNode;
import jdk.nashorn.internal.ir.VarNode;
import jdk.nashorn.internal.ir.visitor.NodeOperatorVisitor;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.parser.TokenType;
import jdk.nashorn.internal.runtime.DebugLogger;

/**
 * Range analysis and narrowing of type where it can be proven
 * that there is no spillover, e.g.
 *
 *  function func(c) {
 *    var v = c & 0xfff;
 *    var w = c & 0xeee;
 *    var x = v * w;
 *    return x;
 *  }
 *
 *  Proves that the multiplication never exceeds 24 bits and can thus be an int
 */
final class RangeAnalyzer extends NodeOperatorVisitor<LexicalContext> {
    static final DebugLogger LOG = new DebugLogger("ranges");

    private static final Range.Functionality RANGE = new Range.Functionality(LOG);

    private final Map<LoopNode, Symbol> loopCounters = new HashMap<>();

    RangeAnalyzer() {
        super(new LexicalContext());
    }

    @Override
    public boolean enterForNode(final ForNode forNode) {
        //conservatively attempt to identify the loop counter. Null means that it wasn't
        //properly identified and that no optimizations can be made with it - its range is
        //simply unknown in that case, if it is assigned in the loop
        final Symbol counter = findLoopCounter(forNode);
        LOG.fine("Entering forNode " + forNode + " counter = " + counter);
        if (counter != null && !assignedInLoop(forNode,  counter)) {
            loopCounters.put(forNode, counter);
        }
        return true;
    }

    //destination visited
    private Symbol setRange(final Expression dest, final Range range) {
        if (range.isUnknown()) {
            return null;
        }

        final Symbol symbol = dest.getSymbol();
        assert symbol != null : dest + " " + dest.getClass() + " has no symbol";
        assert symbol.getRange() != null : symbol + " has no range";
        final Range symRange = RANGE.join(symbol.getRange(), range);

        //anything assigned in the loop, not being the safe loop counter(s) invalidates its entire range
        if (lc.inLoop() && !isLoopCounter(lc.getCurrentLoop(), symbol)) {
            symbol.setRange(Range.createGenericRange());
            return symbol;
        }

        if (!symRange.equals(symbol.getRange())) {
            LOG.fine("Modify range for " + dest + " " + symbol + " from " + symbol.getRange() + " to " + symRange + " (in node = " + dest + ")" );
            symbol.setRange(symRange);
        }

        return null;
    }

    @Override
    public Node leaveADD(final BinaryNode node) {
        setRange(node, RANGE.add(node.lhs().getSymbol().getRange(), node.rhs().getSymbol().getRange()));
        return node;
    }

    @Override
    public Node leaveSUB(final BinaryNode node) {
        setRange(node, RANGE.sub(node.lhs().getSymbol().getRange(), node.rhs().getSymbol().getRange()));
        return node;
    }

    @Override
    public Node leaveMUL(final BinaryNode node) {
        setRange(node, RANGE.mul(node.lhs().getSymbol().getRange(), node.rhs().getSymbol().getRange()));
        return node;
    }

    @Override
    public Node leaveDIV(final BinaryNode node) {
        setRange(node, RANGE.div(node.lhs().getSymbol().getRange(), node.rhs().getSymbol().getRange()));
        return node;
    }

    @Override
    public Node leaveMOD(final BinaryNode node) {
        setRange(node, RANGE.mod(node.lhs().getSymbol().getRange(), node.rhs().getSymbol().getRange()));
        return node;
    }

    @Override
    public Node leaveBIT_AND(final BinaryNode node) {
        setRange(node, RANGE.and(node.lhs().getSymbol().getRange(), node.rhs().getSymbol().getRange()));
        return node;
    }

    @Override
    public Node leaveBIT_OR(final BinaryNode node) {
        setRange(node, RANGE.or(node.lhs().getSymbol().getRange(), node.rhs().getSymbol().getRange()));
        return node;
    }

    @Override
    public Node leaveBIT_XOR(final BinaryNode node) {
        setRange(node, RANGE.xor(node.lhs().getSymbol().getRange(), node.rhs().getSymbol().getRange()));
        return node;
    }

    @Override
    public Node leaveSAR(final BinaryNode node) {
        setRange(node, RANGE.sar(node.lhs().getSymbol().getRange(), node.rhs().getSymbol().getRange()));
        return node;
    }

    @Override
    public Node leaveSHL(final BinaryNode node) {
        setRange(node, RANGE.shl(node.lhs().getSymbol().getRange(), node.rhs().getSymbol().getRange()));
        return node;
    }

    @Override
    public Node leaveSHR(final BinaryNode node) {
        setRange(node, RANGE.shr(node.lhs().getSymbol().getRange(), node.rhs().getSymbol().getRange()));
        return node;
    }

    private Node leaveCmp(final BinaryNode node) {
        setRange(node, Range.createTypeRange(Type.BOOLEAN));
        return node;
    }

    @Override
    public Node leaveEQ(final BinaryNode node) {
        return leaveCmp(node);
    }

    @Override
    public Node leaveEQ_STRICT(final BinaryNode node) {
        return leaveCmp(node);
    }

    @Override
    public Node leaveNE(final BinaryNode node) {
        return leaveCmp(node);
    }

    @Override
    public Node leaveNE_STRICT(final BinaryNode node) {
        return leaveCmp(node);
    }

    @Override
    public Node leaveLT(final BinaryNode node) {
        return leaveCmp(node);
    }

    @Override
    public Node leaveLE(final BinaryNode node) {
        return leaveCmp(node);
    }

    @Override
    public Node leaveGT(final BinaryNode node) {
        return leaveCmp(node);
    }

    @Override
    public Node leaveGE(final BinaryNode node) {
        return leaveCmp(node);
    }

    @Override
    public Node leaveASSIGN(final BinaryNode node) {
        Range range = node.rhs().getSymbol().getRange();
        if (range.isUnknown()) {
            range = Range.createGenericRange();
        }

        setRange(node.lhs(), range);
        setRange(node, range);

        return node;
    }

    private Node leaveSelfModifyingAssign(final BinaryNode node, final Range range) {
        setRange(node.lhs(), range);
        setRange(node, range);
        return node;
    }

    private Node leaveSelfModifyingAssign(final UnaryNode node, final Range range) {
        setRange(node.rhs(), range);
        setRange(node, range);
        return node;
    }

    @Override
    public Node leaveASSIGN_ADD(final BinaryNode node) {
        return leaveSelfModifyingAssign(node, RANGE.add(node.lhs().getSymbol().getRange(), node.rhs().getSymbol().getRange()));
    }

    @Override
    public Node leaveASSIGN_SUB(final BinaryNode node) {
        return leaveSelfModifyingAssign(node, RANGE.sub(node.lhs().getSymbol().getRange(), node.rhs().getSymbol().getRange()));
    }

    @Override
    public Node leaveASSIGN_MUL(final BinaryNode node) {
        return leaveSelfModifyingAssign(node, RANGE.mul(node.lhs().getSymbol().getRange(), node.rhs().getSymbol().getRange()));
    }

    @Override
    public Node leaveASSIGN_DIV(final BinaryNode node) {
        return leaveSelfModifyingAssign(node, Range.createTypeRange(Type.NUMBER));
    }

    @Override
    public Node leaveASSIGN_MOD(final BinaryNode node) {
        return leaveSelfModifyingAssign(node, Range.createTypeRange(Type.NUMBER));
    }

    @Override
    public Node leaveASSIGN_BIT_AND(final BinaryNode node) {
        return leaveSelfModifyingAssign(node, RANGE.and(node.lhs().getSymbol().getRange(), node.rhs().getSymbol().getRange()));
    }

    @Override
    public Node leaveASSIGN_BIT_OR(final BinaryNode node) {
        return leaveSelfModifyingAssign(node, RANGE.or(node.lhs().getSymbol().getRange(), node.rhs().getSymbol().getRange()));
    }

    @Override
    public Node leaveASSIGN_BIT_XOR(final BinaryNode node) {
        return leaveSelfModifyingAssign(node, RANGE.xor(node.lhs().getSymbol().getRange(), node.rhs().getSymbol().getRange()));
    }

    @Override
    public Node leaveASSIGN_SAR(final BinaryNode node) {
        return leaveSelfModifyingAssign(node, RANGE.sar(node.lhs().getSymbol().getRange(), node.rhs().getSymbol().getRange()));
    }

    @Override
    public Node leaveASSIGN_SHR(final BinaryNode node) {
        return leaveSelfModifyingAssign(node, RANGE.shr(node.lhs().getSymbol().getRange(), node.rhs().getSymbol().getRange()));
    }

    @Override
    public Node leaveASSIGN_SHL(final BinaryNode node) {
        return leaveSelfModifyingAssign(node, RANGE.shl(node.lhs().getSymbol().getRange(), node.rhs().getSymbol().getRange()));
    }

    @Override
    public Node leaveDECINC(final UnaryNode node) {
        switch (node.tokenType()) {
        case DECPREFIX:
        case DECPOSTFIX:
            return leaveSelfModifyingAssign(node, RANGE.sub(node.rhs().getSymbol().getRange(), Range.createRange(1)));
        case INCPREFIX:
        case INCPOSTFIX:
            return leaveSelfModifyingAssign(node, RANGE.add(node.rhs().getSymbol().getRange(), Range.createRange(1)));
        default:
            assert false;
            return node;
        }
    }

    @Override
    public Node leaveADD(final UnaryNode node) {
        Range range = node.rhs().getSymbol().getRange();
        if (!range.getType().isNumeric()) {
           range = Range.createTypeRange(Type.NUMBER);
        }
        setRange(node, range);
        return node;
    }

    @Override
    public Node leaveBIT_NOT(final UnaryNode node) {
        setRange(node, Range.createTypeRange(Type.INT));
        return node;
    }

    @Override
    public Node leaveNOT(final UnaryNode node) {
        setRange(node, Range.createTypeRange(Type.BOOLEAN));
        return node;
    }

    @Override
    public Node leaveSUB(final UnaryNode node) {
        setRange(node, RANGE.neg(node.rhs().getSymbol().getRange()));
        return node;
    }

    @Override
    public Node leaveVarNode(final VarNode node) {
        if (node.isAssignment()) {
            Range range = node.getInit().getSymbol().getRange();
            range = range.isUnknown() ? Range.createGenericRange() : range;

            setRange(node.getName(), range);
        }

        return node;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public boolean enterLiteralNode(final LiteralNode node) {
        // ignore array literals
        return !(node instanceof ArrayLiteralNode);
    }

    @Override
    public Node leaveLiteralNode(@SuppressWarnings("rawtypes") final LiteralNode node) {
        if (node.getType().isInteger()) {
            setRange(node, Range.createRange(node.getInt32()));
        } else if (node.getType().isNumber()) {
            setRange(node, Range.createRange(node.getNumber()));
        } else if (node.getType().isLong()) {
            setRange(node, Range.createRange(node.getLong()));
        } else if (node.getType().isBoolean()) {
            setRange(node, Range.createTypeRange(Type.BOOLEAN));
        } else {
            setRange(node, Range.createGenericRange());
        }
        return node;
    }

    @Override
    public boolean enterRuntimeNode(final RuntimeNode node) {
        // a runtime node that cannot be specialized is no point entering
        return node.getRequest().canSpecialize();
    }

    /**
     * Check whether a symbol is unsafely assigned in a loop - i.e. repeteadly assigned and
     * not being identified as the loop counter. That means we don't really know anything
     * about its range.
     * @param loopNode loop node
     * @param symbol   symbol
     * @return true if assigned in loop
     */
    // TODO - this currently checks for nodes only - needs to be augmented for while nodes
    // assignment analysis is also very conservative
    private static boolean assignedInLoop(final LoopNode loopNode, final Symbol symbol) {
        final HashSet<Node> skip = new HashSet<>();
        final HashSet<Node> assignmentsInLoop = new HashSet<>();

        loopNode.accept(new NodeVisitor<LexicalContext>(new LexicalContext()) {
            private boolean assigns(final Node node, final Symbol s) {
                return node.isAssignment() && ((Assignment<?>)node).getAssignmentDest().getSymbol() == s;
            }

            @Override
            public boolean enterForNode(final ForNode forNode) {
                if (forNode.getInit() != null) {
                    skip.add(forNode.getInit());
                }
                if (forNode.getModify() != null) {
                    skip.add(forNode.getModify());
                }
                return true;
            }

            @Override
            public Node leaveDefault(final Node node) {
                //if this is an assignment to symbol
                if (!skip.contains(node) && assigns(node, symbol)) {
                    assignmentsInLoop.add(node);
                }
                return node;
            }
        });

        return !assignmentsInLoop.isEmpty();
    }

    /**
     * Check for a loop counter. This is currently quite conservative, in that it only handles
     * x <= counter and x < counter.
     *
     * @param node loop node to check
     * @return
     */
    private static Symbol findLoopCounter(final LoopNode node) {
        final Expression test = node.getTest();

        if (test != null && test.isComparison()) {
            final BinaryNode binaryNode = (BinaryNode)test;
            final Expression lhs = binaryNode.lhs();
            final Expression rhs = binaryNode.rhs();

            //detect ident cmp int_literal
            if (lhs instanceof IdentNode && rhs instanceof LiteralNode && ((LiteralNode<?>)rhs).getType().isInteger()) {
                final Symbol    symbol = lhs.getSymbol();
                final int       margin = ((LiteralNode<?>)rhs).getInt32();
                final TokenType op     = test.tokenType();

                switch (op) {
                case LT:
                case LE:
                    symbol.setRange(RANGE.join(symbol.getRange(), Range.createRange(op == TokenType.LT ? margin - 1 : margin)));
                    return symbol;
                case GT:
                case GE:
                    //setRange(lhs, Range.createRange(op == TokenType.GT ? margin + 1 : margin));
                    //return symbol;
                default:
                    break;
                }
            }
        }

        return null;
    }

    private boolean isLoopCounter(final LoopNode loopNode, final Symbol symbol) {
        //this only works if loop nodes aren't replaced by other ones during this transform, but they are not
        return loopCounters.get(loopNode) == symbol;
    }
}
