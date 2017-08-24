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

import jdk.nashorn.internal.ir.BinaryNode;
import jdk.nashorn.internal.ir.LexicalContext;
import jdk.nashorn.internal.ir.Node;
import jdk.nashorn.internal.ir.UnaryNode;

/**
 * Like NodeVisitor but navigating further into operators.
 * @param <T> Lexical context class for this NodeOperatorVisitor
 */
public abstract class NodeOperatorVisitor<T extends LexicalContext> extends NodeVisitor<T> {
    /**
     * Constructor
     *
     * @param lc a custom lexical context
     */
    public NodeOperatorVisitor(final T lc) {
        super(lc);
    }

    @Override
    public boolean enterUnaryNode(final UnaryNode unaryNode) {
        switch (unaryNode.tokenType()) {
        case POS:
            return enterPOS(unaryNode);
        case BIT_NOT:
            return enterBIT_NOT(unaryNode);
        case DELETE:
            return enterDELETE(unaryNode);
        case NEW:
            return enterNEW(unaryNode);
        case NOT:
            return enterNOT(unaryNode);
        case NEG:
            return enterNEG(unaryNode);
        case TYPEOF:
            return enterTYPEOF(unaryNode);
        case VOID:
            return enterVOID(unaryNode);
        case DECPREFIX:
        case DECPOSTFIX:
        case INCPREFIX:
        case INCPOSTFIX:
            return enterDECINC(unaryNode);
        default:
            return super.enterUnaryNode(unaryNode);
        }
    }

    @Override
    public final Node leaveUnaryNode(final UnaryNode unaryNode) {
        switch (unaryNode.tokenType()) {
        case POS:
            return leavePOS(unaryNode);
        case BIT_NOT:
            return leaveBIT_NOT(unaryNode);
        case DELETE:
            return leaveDELETE(unaryNode);
        case NEW:
            return leaveNEW(unaryNode);
        case NOT:
            return leaveNOT(unaryNode);
        case NEG:
            return leaveNEG(unaryNode);
        case TYPEOF:
            return leaveTYPEOF(unaryNode);
        case VOID:
            return leaveVOID(unaryNode);
        case DECPREFIX:
        case DECPOSTFIX:
        case INCPREFIX:
        case INCPOSTFIX:
            return leaveDECINC(unaryNode);
        default:
            return super.leaveUnaryNode(unaryNode);
        }
    }

    @Override
    public final boolean enterBinaryNode(final BinaryNode binaryNode) {
        switch (binaryNode.tokenType()) {
        case ADD:
            return enterADD(binaryNode);
        case AND:
            return enterAND(binaryNode);
        case ASSIGN:
            return enterASSIGN(binaryNode);
        case ASSIGN_ADD:
            return enterASSIGN_ADD(binaryNode);
        case ASSIGN_BIT_AND:
            return enterASSIGN_BIT_AND(binaryNode);
        case ASSIGN_BIT_OR:
            return enterASSIGN_BIT_OR(binaryNode);
        case ASSIGN_BIT_XOR:
            return enterASSIGN_BIT_XOR(binaryNode);
        case ASSIGN_DIV:
            return enterASSIGN_DIV(binaryNode);
        case ASSIGN_MOD:
            return enterASSIGN_MOD(binaryNode);
        case ASSIGN_MUL:
            return enterASSIGN_MUL(binaryNode);
        case ASSIGN_SAR:
            return enterASSIGN_SAR(binaryNode);
        case ASSIGN_SHL:
            return enterASSIGN_SHL(binaryNode);
        case ASSIGN_SHR:
            return enterASSIGN_SHR(binaryNode);
        case ASSIGN_SUB:
            return enterASSIGN_SUB(binaryNode);
        case ARROW:
            return enterARROW(binaryNode);
        case BIT_AND:
            return enterBIT_AND(binaryNode);
        case BIT_OR:
            return enterBIT_OR(binaryNode);
        case BIT_XOR:
            return enterBIT_XOR(binaryNode);
        case COMMARIGHT:
            return enterCOMMARIGHT(binaryNode);
        case COMMALEFT:
            return enterCOMMALEFT(binaryNode);
        case DIV:
            return enterDIV(binaryNode);
        case EQ:
            return enterEQ(binaryNode);
        case EQ_STRICT:
            return enterEQ_STRICT(binaryNode);
        case GE:
            return enterGE(binaryNode);
        case GT:
            return enterGT(binaryNode);
        case IN:
            return enterIN(binaryNode);
        case INSTANCEOF:
            return enterINSTANCEOF(binaryNode);
        case LE:
            return enterLE(binaryNode);
        case LT:
            return enterLT(binaryNode);
        case MOD:
            return enterMOD(binaryNode);
        case MUL:
            return enterMUL(binaryNode);
        case NE:
            return enterNE(binaryNode);
        case NE_STRICT:
            return enterNE_STRICT(binaryNode);
        case OR:
            return enterOR(binaryNode);
        case SAR:
            return enterSAR(binaryNode);
        case SHL:
            return enterSHL(binaryNode);
        case SHR:
            return enterSHR(binaryNode);
        case SUB:
            return enterSUB(binaryNode);
        default:
            return super.enterBinaryNode(binaryNode);
        }
    }

    @Override
    public final Node leaveBinaryNode(final BinaryNode binaryNode) {
        switch (binaryNode.tokenType()) {
        case ADD:
            return leaveADD(binaryNode);
        case AND:
            return leaveAND(binaryNode);
        case ASSIGN:
            return leaveASSIGN(binaryNode);
        case ASSIGN_ADD:
            return leaveASSIGN_ADD(binaryNode);
        case ASSIGN_BIT_AND:
            return leaveASSIGN_BIT_AND(binaryNode);
        case ASSIGN_BIT_OR:
            return leaveASSIGN_BIT_OR(binaryNode);
        case ASSIGN_BIT_XOR:
            return leaveASSIGN_BIT_XOR(binaryNode);
        case ASSIGN_DIV:
            return leaveASSIGN_DIV(binaryNode);
        case ASSIGN_MOD:
            return leaveASSIGN_MOD(binaryNode);
        case ASSIGN_MUL:
            return leaveASSIGN_MUL(binaryNode);
        case ASSIGN_SAR:
            return leaveASSIGN_SAR(binaryNode);
        case ASSIGN_SHL:
            return leaveASSIGN_SHL(binaryNode);
        case ASSIGN_SHR:
            return leaveASSIGN_SHR(binaryNode);
        case ASSIGN_SUB:
            return leaveASSIGN_SUB(binaryNode);
        case ARROW:
            return leaveARROW(binaryNode);
        case BIT_AND:
            return leaveBIT_AND(binaryNode);
        case BIT_OR:
            return leaveBIT_OR(binaryNode);
        case BIT_XOR:
            return leaveBIT_XOR(binaryNode);
        case COMMARIGHT:
            return leaveCOMMARIGHT(binaryNode);
        case COMMALEFT:
            return leaveCOMMALEFT(binaryNode);
        case DIV:
            return leaveDIV(binaryNode);
        case EQ:
            return leaveEQ(binaryNode);
        case EQ_STRICT:
            return leaveEQ_STRICT(binaryNode);
        case GE:
            return leaveGE(binaryNode);
        case GT:
            return leaveGT(binaryNode);
        case IN:
            return leaveIN(binaryNode);
        case INSTANCEOF:
            return leaveINSTANCEOF(binaryNode);
        case LE:
            return leaveLE(binaryNode);
        case LT:
            return leaveLT(binaryNode);
        case MOD:
            return leaveMOD(binaryNode);
        case MUL:
            return leaveMUL(binaryNode);
        case NE:
            return leaveNE(binaryNode);
        case NE_STRICT:
            return leaveNE_STRICT(binaryNode);
        case OR:
            return leaveOR(binaryNode);
        case SAR:
            return leaveSAR(binaryNode);
        case SHL:
            return leaveSHL(binaryNode);
        case SHR:
            return leaveSHR(binaryNode);
        case SUB:
            return leaveSUB(binaryNode);
        default:
            return super.leaveBinaryNode(binaryNode);
        }
    }

    /*
     * Unary entries and exists.
     */

    /**
     * Unary enter - callback for entering a unary +
     *
     * @param  unaryNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterPOS(final UnaryNode unaryNode) {
        return enterDefault(unaryNode);
    }

    /**
     * Unary leave - callback for leaving a unary +
     *
     * @param  unaryNode the node
     * @return processed node, which will replace the original one, or the original node
     */
     public Node leavePOS(final UnaryNode unaryNode) {
        return leaveDefault(unaryNode);
    }

    /**
     * Unary enter - callback for entering a ~ operator
     *
     * @param  unaryNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterBIT_NOT(final UnaryNode unaryNode) {
        return enterDefault(unaryNode);
    }

    /**
     * Unary leave - callback for leaving a unary ~
     *
     * @param  unaryNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveBIT_NOT(final UnaryNode unaryNode) {
        return leaveDefault(unaryNode);
    }

    /**
     * Unary enter - callback for entering a ++ or -- operator
     *
     * @param  unaryNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterDECINC(final UnaryNode unaryNode) {
        return enterDefault(unaryNode);
    }

    /**
     * Unary leave - callback for leaving a ++ or -- operator
     *
     * @param  unaryNode the node
     * @return processed node, which will replace the original one, or the original node
     */
     public Node leaveDECINC(final UnaryNode unaryNode) {
        return leaveDefault(unaryNode);
    }

    /**
     * Unary enter - callback for entering a delete operator
     *
     * @param  unaryNode the node
     * @return processed node
     */
    public boolean enterDELETE(final UnaryNode unaryNode) {
        return enterDefault(unaryNode);
    }

    /**
     * Unary leave - callback for leaving a delete operator
     *
     * @param  unaryNode the node
     * @return processed node, which will replace the original one, or the original node
     */
     public Node leaveDELETE(final UnaryNode unaryNode) {
        return leaveDefault(unaryNode);
    }

    /**
     * Unary enter - callback for entering a new operator
     *
     * @param  unaryNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterNEW(final UnaryNode unaryNode) {
        return enterDefault(unaryNode);
    }

    /**
     * Unary leave - callback for leaving a new operator
     *
     * @param  unaryNode the node
     * @return processed node, which will replace the original one, or the original node
     */
     public Node leaveNEW(final UnaryNode unaryNode) {
        return leaveDefault(unaryNode);
    }

    /**
     * Unary enter - callback for entering a ! operator
     *
     * @param  unaryNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterNOT(final UnaryNode unaryNode) {
        return enterDefault(unaryNode);
    }

    /**
     * Unary leave - callback for leaving a ! operator
     *
     * @param  unaryNode the node
     * @return processed node, which will replace the original one, or the original node
     */
     public Node leaveNOT(final UnaryNode unaryNode) {
        return leaveDefault(unaryNode);
    }

    /**
     * Unary enter - callback for entering a unary -
     *
     * @param  unaryNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterNEG(final UnaryNode unaryNode) {
        return enterDefault(unaryNode);
    }

    /**
     * Unary leave - callback for leaving a unary -
     *
     * @param  unaryNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveNEG(final UnaryNode unaryNode) {
        return leaveDefault(unaryNode);
    }

    /**
     * Unary enter - callback for entering a typeof
     *
     * @param  unaryNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterTYPEOF(final UnaryNode unaryNode) {
        return enterDefault(unaryNode);
    }

    /**
     * Unary leave - callback for leaving a typeof operator
     *
     * @param  unaryNode the node
     * @return processed node, which will replace the original one, or the original node
     */
     public Node leaveTYPEOF(final UnaryNode unaryNode) {
        return leaveDefault(unaryNode);
    }

    /**
     * Unary enter - callback for entering a void
     *
     * @param  unaryNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterVOID(final UnaryNode unaryNode) {
        return enterDefault(unaryNode);
    }

    /**
     * Unary leave - callback for leaving a void
     *
     * @param  unaryNode the node
     * @return processed node, which will replace the original one, or the original node
     */
     public Node leaveVOID(final UnaryNode unaryNode) {
        return leaveDefault(unaryNode);
    }

    /**
     * Binary enter - callback for entering + operator
     *
     * @param  binaryNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterADD(final BinaryNode binaryNode) {
        return enterDefault(binaryNode);
    }

    /**
     * Binary leave - callback for leaving a + operator
     *
     * @param  binaryNode the node
     * @return processed node, which will replace the original one, or the original node
     */
     public Node leaveADD(final BinaryNode binaryNode) {
        return leaveDefault(binaryNode);
    }

    /**
     * Binary enter - callback for entering {@literal &&} operator
     *
     * @param  binaryNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterAND(final BinaryNode binaryNode) {
        return enterDefault(binaryNode);
    }

    /**
     * Binary leave - callback for leaving a {@literal &&} operator
     *
     * @param  binaryNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveAND(final BinaryNode binaryNode) {
        return leaveDefault(binaryNode);
    }

    /**
     * Binary enter - callback for entering an assignment
     *
     * @param  binaryNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterASSIGN(final BinaryNode binaryNode) {
        return enterDefault(binaryNode);
    }

    /**
     * Binary leave - callback for leaving an assignment
     *
     * @param  binaryNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveASSIGN(final BinaryNode binaryNode) {
        return leaveDefault(binaryNode);
    }

    /**
     * Binary enter - callback for entering += operator
     *
     * @param  binaryNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterASSIGN_ADD(final BinaryNode binaryNode) {
        return enterDefault(binaryNode);
    }

    /**
     * Binary leave - callback for leaving a += operator
     *
     * @param  binaryNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveASSIGN_ADD(final BinaryNode binaryNode) {
        return leaveDefault(binaryNode);
    }

    /**
     * Binary enter - callback for entering {@literal &=} operator
     *
     * @param  binaryNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterASSIGN_BIT_AND(final BinaryNode binaryNode) {
        return enterDefault(binaryNode);
    }

    /**
     * Binary leave - callback for leaving a {@literal &=} operator
     *
     * @param  binaryNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveASSIGN_BIT_AND(final BinaryNode binaryNode) {
        return leaveDefault(binaryNode);
    }

    /**
     * Binary enter - callback for entering |= operator
     *
     * @param  binaryNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterASSIGN_BIT_OR(final BinaryNode binaryNode) {
        return enterDefault(binaryNode);
    }

    /**
     * Binary leave - callback for leaving a |= operator
     *
     * @param  binaryNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveASSIGN_BIT_OR(final BinaryNode binaryNode) {
        return leaveDefault(binaryNode);
    }

    /**
     * Binary enter - callback for entering ^= operator
     *
     * @param  binaryNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterASSIGN_BIT_XOR(final BinaryNode binaryNode) {
        return enterDefault(binaryNode);
    }

    /**
     * Binary leave - callback for leaving a ^= operator
     *
     * @param  binaryNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveASSIGN_BIT_XOR(final BinaryNode binaryNode) {
        return leaveDefault(binaryNode);
    }

    /**
     * Binary enter - callback for entering /= operator
     *
     * @param  binaryNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterASSIGN_DIV(final BinaryNode binaryNode) {
        return enterDefault(binaryNode);
    }

    /**
     * Binary leave - callback for leaving a /= operator
     *
     * @param  binaryNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveASSIGN_DIV(final BinaryNode binaryNode) {
        return leaveDefault(binaryNode);
    }

    /**
     * Binary enter - callback for entering %= operator
     *
     * @param  binaryNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterASSIGN_MOD(final BinaryNode binaryNode) {
        return enterDefault(binaryNode);
    }

    /**
     * Binary leave - callback for leaving a %= operator
     *
     * @param  binaryNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveASSIGN_MOD(final BinaryNode binaryNode) {
        return leaveDefault(binaryNode);
    }

    /**
     * Binary enter - callback for entering *= operator
     *
     * @param  binaryNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterASSIGN_MUL(final BinaryNode binaryNode) {
        return enterDefault(binaryNode);
    }

    /**
     * Binary leave - callback for leaving a *= operator
     *
     * @param  binaryNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveASSIGN_MUL(final BinaryNode binaryNode) {
        return leaveDefault(binaryNode);
    }

    /**
     * Binary enter - callback for entering {@literal >>=} operator
     *
     * @param  binaryNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterASSIGN_SAR(final BinaryNode binaryNode) {
        return enterDefault(binaryNode);
    }

    /**
     * Binary leave - callback for leaving a {@literal >>=} operator
     *
     * @param  binaryNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveASSIGN_SAR(final BinaryNode binaryNode) {
        return leaveDefault(binaryNode);
    }

    /**
     * Binary enter - callback for entering a {@literal <<=} operator
     *
     * @param  binaryNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterASSIGN_SHL(final BinaryNode binaryNode) {
        return enterDefault(binaryNode);
    }

    /**
     * Binary leave - callback for leaving a {@literal <<=} operator
     *
     * @param  binaryNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveASSIGN_SHL(final BinaryNode binaryNode) {
        return leaveDefault(binaryNode);
    }

    /**
     * Binary enter - callback for entering {@literal >>>=} operator
     *
     * @param  binaryNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterASSIGN_SHR(final BinaryNode binaryNode) {
        return enterDefault(binaryNode);
    }

    /**
     * Binary leave - callback for leaving a {@literal >>>=} operator
     *
     * @param  binaryNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveASSIGN_SHR(final BinaryNode binaryNode) {
        return leaveDefault(binaryNode);
    }

    /**
     * Binary enter - callback for entering -= operator
     *
     * @param  binaryNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterASSIGN_SUB(final BinaryNode binaryNode) {
        return enterDefault(binaryNode);
    }

    /**
     * Binary leave - callback for leaving a -= operator
     *
     * @param  binaryNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveASSIGN_SUB(final BinaryNode binaryNode) {
        return leaveDefault(binaryNode);
    }

    /**
     * Binary enter - callback for entering a arrow operator
     *
     * @param  binaryNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterARROW(final BinaryNode binaryNode) {
        return enterDefault(binaryNode);
    }

    /**
     * Binary leave - callback for leaving a arrow operator
     *
     * @param  binaryNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveARROW(final BinaryNode binaryNode) {
        return leaveDefault(binaryNode);
    }

    /**
     * Binary enter - callback for entering {@literal &} operator
     *
     * @param  binaryNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterBIT_AND(final BinaryNode binaryNode) {
        return enterDefault(binaryNode);
    }

    /**
     * Binary leave - callback for leaving a {@literal &} operator
     *
     * @param  binaryNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveBIT_AND(final BinaryNode binaryNode) {
        return leaveDefault(binaryNode);
    }

    /**
     * Binary enter - callback for entering | operator
     *
     * @param  binaryNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterBIT_OR(final BinaryNode binaryNode) {
        return enterDefault(binaryNode);
    }

    /**
     * Binary leave - callback for leaving a | operator
     *
     * @param  binaryNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveBIT_OR(final BinaryNode binaryNode) {
        return leaveDefault(binaryNode);
    }

    /**
     * Binary enter - callback for entering ^ operator
     *
     * @param  binaryNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterBIT_XOR(final BinaryNode binaryNode) {
        return enterDefault(binaryNode);
    }

    /**
     * Binary leave - callback for leaving a  operator
     *
     * @param  binaryNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveBIT_XOR(final BinaryNode binaryNode) {
        return leaveDefault(binaryNode);
    }

    /**
     * Binary enter - callback for entering comma left operator
     * (a, b) where the result is a
     *
     * @param  binaryNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterCOMMALEFT(final BinaryNode binaryNode) {
        return enterDefault(binaryNode);
    }

    /**
     * Binary leave - callback for leaving a comma left operator
     * (a, b) where the result is a
     *
     * @param  binaryNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveCOMMALEFT(final BinaryNode binaryNode) {
        return leaveDefault(binaryNode);
    }

    /**
     * Binary enter - callback for entering comma right operator
     * (a, b) where the result is b
     *
     * @param  binaryNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterCOMMARIGHT(final BinaryNode binaryNode) {
        return enterDefault(binaryNode);
    }

    /**
     * Binary leave - callback for leaving a comma left operator
     * (a, b) where the result is b
     *
     * @param  binaryNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveCOMMARIGHT(final BinaryNode binaryNode) {
        return leaveDefault(binaryNode);
    }

    /**
     * Binary enter - callback for entering a division
     *
     * @param  binaryNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterDIV(final BinaryNode binaryNode) {
        return enterDefault(binaryNode);
    }

    /**
     * Binary leave - callback for leaving a division
     *
     * @param  binaryNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveDIV(final BinaryNode binaryNode) {
        return leaveDefault(binaryNode);
    }

    /**
     * Binary enter - callback for entering == operator
     *
     * @param  binaryNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterEQ(final BinaryNode binaryNode) {
        return enterDefault(binaryNode);
    }

    /**
     * Binary leave - callback for leaving == operator
     *
     * @param  binaryNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveEQ(final BinaryNode binaryNode) {
        return leaveDefault(binaryNode);
    }

    /**
     * Binary enter - callback for entering === operator
     *
     * @param  binaryNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterEQ_STRICT(final BinaryNode binaryNode) {
        return enterDefault(binaryNode);
    }

    /**
     * Binary leave - callback for leaving === operator
     *
     * @param  binaryNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveEQ_STRICT(final BinaryNode binaryNode) {
        return leaveDefault(binaryNode);
    }

    /**
     * Binary enter - callback for entering {@literal >=} operator
     *
     * @param  binaryNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterGE(final BinaryNode binaryNode) {
        return enterDefault(binaryNode);
    }

    /**
     * Binary leave - callback for leaving {@literal >=} operator
     *
     * @param  binaryNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveGE(final BinaryNode binaryNode) {
        return leaveDefault(binaryNode);
    }

    /**
     * Binary enter - callback for entering {@literal >} operator
     *
     * @param  binaryNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterGT(final BinaryNode binaryNode) {
        return enterDefault(binaryNode);
    }

    /**
     * Binary leave - callback for leaving {@literal >} operator
     *
     * @param  binaryNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveGT(final BinaryNode binaryNode) {
        return leaveDefault(binaryNode);
    }

    /**
     * Binary enter - callback for entering in operator
     *
     * @param  binaryNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterIN(final BinaryNode binaryNode) {
        return enterDefault(binaryNode);
    }

    /**
     * Binary leave - callback for leaving in operator
     *
     * @param  binaryNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveIN(final BinaryNode binaryNode) {
        return leaveDefault(binaryNode);
    }

    /**
     * Binary enter - callback for entering instanceof operator
     *
     * @param  binaryNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterINSTANCEOF(final BinaryNode binaryNode) {
        return enterDefault(binaryNode);
    }

    /**
     * Binary leave - callback for leaving instanceof operator
     *
     * @param  binaryNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveINSTANCEOF(final BinaryNode binaryNode) {
        return leaveDefault(binaryNode);
    }

    /**
     * Binary enter - callback for entering {@literal <=} operator
     *
     * @param  binaryNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterLE(final BinaryNode binaryNode) {
        return enterDefault(binaryNode);
    }

    /**
     * Binary leave - callback for leaving {@literal <=} operator
     *
     * @param  binaryNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveLE(final BinaryNode binaryNode) {
        return leaveDefault(binaryNode);
    }

    /**
     * Binary enter - callback for entering {@literal <} operator
     *
     * @param  binaryNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterLT(final BinaryNode binaryNode) {
        return enterDefault(binaryNode);
    }

    /**
     * Binary leave - callback for leaving {@literal <} operator
     *
     * @param  binaryNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveLT(final BinaryNode binaryNode) {
        return leaveDefault(binaryNode);
    }
    /**
     * Binary enter - callback for entering % operator
     *
     * @param  binaryNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterMOD(final BinaryNode binaryNode) {
        return enterDefault(binaryNode);
    }

    /**
     * Binary leave - callback for leaving % operator
     *
     * @param  binaryNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveMOD(final BinaryNode binaryNode) {
        return leaveDefault(binaryNode);
    }

    /**
     * Binary enter - callback for entering * operator
     *
     * @param  binaryNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterMUL(final BinaryNode binaryNode) {
        return enterDefault(binaryNode);
    }

    /**
     * Binary leave - callback for leaving * operator
     *
     * @param  binaryNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveMUL(final BinaryNode binaryNode) {
        return leaveDefault(binaryNode);
    }

    /**
     * Binary enter - callback for entering != operator
     *
     * @param  binaryNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterNE(final BinaryNode binaryNode) {
        return enterDefault(binaryNode);
    }

    /**
     * Binary leave - callback for leaving != operator
     *
     * @param  binaryNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveNE(final BinaryNode binaryNode) {
        return leaveDefault(binaryNode);
    }

    /**
     * Binary enter - callback for entering a !== operator
     *
     * @param  binaryNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterNE_STRICT(final BinaryNode binaryNode) {
        return enterDefault(binaryNode);
    }

    /**
     * Binary leave - callback for leaving !== operator
     *
     * @param  binaryNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveNE_STRICT(final BinaryNode binaryNode) {
        return leaveDefault(binaryNode);
    }

    /**
     * Binary enter - callback for entering || operator
     *
     * @param  binaryNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterOR(final BinaryNode binaryNode) {
        return enterDefault(binaryNode);
    }

    /**
     * Binary leave - callback for leaving || operator
     *
     * @param  binaryNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveOR(final BinaryNode binaryNode) {
        return leaveDefault(binaryNode);
    }

    /**
     * Binary enter - callback for entering {@literal >>} operator
     *
     * @param  binaryNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterSAR(final BinaryNode binaryNode) {
        return enterDefault(binaryNode);
    }

    /**
     * Binary leave - callback for leaving {@literal >>} operator
     *
     * @param  binaryNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveSAR(final BinaryNode binaryNode) {
        return leaveDefault(binaryNode);
    }

    /**
     * Binary enter - callback for entering {@literal <<} operator
     *
     * @param  binaryNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterSHL(final BinaryNode binaryNode) {
        return enterDefault(binaryNode);
    }

    /**
     * Binary leave - callback for leaving {@literal <<} operator
     *
     * @param  binaryNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveSHL(final BinaryNode binaryNode) {
        return leaveDefault(binaryNode);
    }
    /**
     * Binary enter - callback for entering {@literal >>>} operator
     *
     * @param  binaryNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterSHR(final BinaryNode binaryNode) {
        return enterDefault(binaryNode);
    }

    /**
     * Binary leave - callback for leaving {@literal >>>} operator
     *
     * @param  binaryNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveSHR(final BinaryNode binaryNode) {
        return leaveDefault(binaryNode);
    }

    /**
     * Binary enter - callback for entering - operator
     *
     * @param  binaryNode the node
     * @return true if traversal should continue and node children be traversed, false otherwise
     */
    public boolean enterSUB(final BinaryNode binaryNode) {
        return enterDefault(binaryNode);
    }

    /**
     * Binary leave - callback for leaving - operator
     *
     * @param  binaryNode the node
     * @return processed node, which will replace the original one, or the original node
     */
    public Node leaveSUB(final BinaryNode binaryNode) {
        return leaveDefault(binaryNode);
    }
}
