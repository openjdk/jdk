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

package jdk.nashorn.internal.ir;

import static jdk.nashorn.internal.runtime.UnwarrantedOptimismException.INVALID_PROGRAM_POINT;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.annotations.Ignore;
import jdk.nashorn.internal.ir.annotations.Immutable;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.parser.TokenType;

/**
 * BinaryNode nodes represent two operand operations.
 */
@Immutable
public final class BinaryNode extends Expression implements Assignment<Expression>, Optimistic {
    private static final long serialVersionUID = 1L;

    // Placeholder for "undecided optimistic ADD type". Unfortunately, we can't decide the type of ADD during optimistic
    // type calculation as it can have local variables as its operands that will decide its ultimate type.
    private static final Type OPTIMISTIC_UNDECIDED_TYPE = Type.typeFor(new Object(){/*empty*/}.getClass());

    /** Left hand side argument. */
    private final Expression lhs;

    private final Expression rhs;

    private final int programPoint;

    private final Type type;

    private transient Type cachedType;
    private transient Object cachedTypeFunction;

    @Ignore
    private static final Set<TokenType> CAN_OVERFLOW =
        Collections.unmodifiableSet(new HashSet<>(Arrays.asList(new TokenType[] {
                TokenType.ADD,
                TokenType.DIV,
                TokenType.MOD,
                TokenType.MUL,
                TokenType.SUB,
                TokenType.ASSIGN_ADD,
                TokenType.ASSIGN_DIV,
                TokenType.ASSIGN_MOD,
                TokenType.ASSIGN_MUL,
                TokenType.ASSIGN_SUB
            })));

    /**
     * Constructor
     *
     * @param token  token
     * @param lhs    left hand side
     * @param rhs    right hand side
     */
    public BinaryNode(final long token, final Expression lhs, final Expression rhs) {
        super(token, lhs.getStart(), rhs.getFinish());
        assert !(isTokenType(TokenType.AND) || isTokenType(TokenType.OR)) || lhs instanceof JoinPredecessorExpression;
        this.lhs   = lhs;
        this.rhs   = rhs;
        this.programPoint = INVALID_PROGRAM_POINT;
        this.type = null;
    }

    private BinaryNode(final BinaryNode binaryNode, final Expression lhs, final Expression rhs, final Type type, final int programPoint) {
        super(binaryNode);
        this.lhs = lhs;
        this.rhs = rhs;
        this.programPoint = programPoint;
        this.type = type;
    }

    /**
     * Returns true if the node is a comparison operation.
     * @return true if the node is a comparison operation.
     */
    public boolean isComparison() {
        switch (tokenType()) {
        case EQ:
        case EQ_STRICT:
        case NE:
        case NE_STRICT:
        case LE:
        case LT:
        case GE:
        case GT:
            return true;
        default:
            return false;
        }
    }

    /**
     * Returns true if the node is a logical operation.
     * @return true if the node is a logical operation.
     */
    public boolean isLogical() {
        return isLogical(tokenType());
    }

    /**
     * Returns true if the token type represents a logical operation.
     * @param tokenType the token type
     * @return true if the token type represents a logical operation.
     */
    public static boolean isLogical(final TokenType tokenType) {
        switch (tokenType) {
        case AND:
        case OR:
            return true;
        default:
            return false;
        }
    }

    private static final Function<Symbol, Type> UNKNOWN_LOCALS = new Function<Symbol, Type>() {
        @Override
        public Type apply(final Symbol t) {
            return null;
        }
    };

    /**
     * Return the widest possible type for this operation. This is used for compile time
     * static type inference
     *
     * @return Type
     */
    @Override
    public Type getWidestOperationType() {
        return getWidestOperationType(UNKNOWN_LOCALS);
    }

    /**
     * Return the widest possible operand type for this operation.
     *
     * @return Type
     */
    public Type getWidestOperandType() {
        switch (tokenType()) {
        case SHR:
        case ASSIGN_SHR:
            return Type.INT;
        case INSTANCEOF:
            return Type.OBJECT;
        default:
            if (isComparison()) {
                return Type.OBJECT;
            }
            return getWidestOperationType();
        }
    }

    private Type getWidestOperationType(final Function<Symbol, Type> localVariableTypes) {
        switch (tokenType()) {
        case ADD:
        case ASSIGN_ADD: {
            // Compare this logic to decideType(Type, Type); it's similar, but it handles the optimistic type
            // calculation case while this handles the conservative case.
            final Type lhsType = lhs.getType(localVariableTypes);
            final Type rhsType = rhs.getType(localVariableTypes);
            if(lhsType == Type.BOOLEAN && rhsType == Type.BOOLEAN) {
                // Will always fit in an int, as the value range is [0, 1, 2]. If we didn't treat them specially here,
                // they'd end up being treated as generic INT operands and their sum would be conservatively considered
                // to be a LONG in the generic case below; we can do better here.
                return Type.INT;
            } else if(isString(lhsType) || isString(rhsType)) {
                // We can statically figure out that this is a string if either operand is a string. In this case, use
                // CHARSEQUENCE to prevent it from being proactively flattened.
                return Type.CHARSEQUENCE;
            }
            final Type widestOperandType = Type.widest(undefinedToNumber(booleanToInt(lhsType)), undefinedToNumber(booleanToInt(rhsType)));
            if(widestOperandType == Type.INT) {
                return Type.LONG;
            } else if (widestOperandType.isNumeric()) {
                return Type.NUMBER;
            }
            // We pretty much can't know what it will be statically. Must presume OBJECT conservatively, as we can end
            // up getting either a string or an object when adding something + object, e.g.:
            // 1 + {} == "1[object Object]", but
            // 1 + {valueOf: function() { return 2 }} == 3. Also:
            // 1 + {valueOf: function() { return "2" }} == "12".
            return Type.OBJECT;
        }
        case SHR:
        case ASSIGN_SHR:
            return Type.LONG;
        case ASSIGN_SAR:
        case ASSIGN_SHL:
        case BIT_AND:
        case BIT_OR:
        case BIT_XOR:
        case ASSIGN_BIT_AND:
        case ASSIGN_BIT_OR:
        case ASSIGN_BIT_XOR:
        case SAR:
        case SHL:
            return Type.INT;
        case DIV:
        case MOD:
        case ASSIGN_DIV:
        case ASSIGN_MOD: {
            // Naively, one might think MOD has the same type as the widest of its operands, this is unfortunately not
            // true when denominator is zero, so even type(int % int) == double.
            return Type.NUMBER;
        }
        case MUL:
        case SUB:
        case ASSIGN_MUL:
        case ASSIGN_SUB: {
            final Type lhsType = lhs.getType(localVariableTypes);
            final Type rhsType = rhs.getType(localVariableTypes);
            if(lhsType == Type.BOOLEAN && rhsType == Type.BOOLEAN) {
                return Type.INT;
            }
            final Type widestOperandType = Type.widest(booleanToInt(lhsType), booleanToInt(rhsType));
            if(widestOperandType == Type.INT) {
                return Type.LONG;
            }
            return Type.NUMBER;
        }
        case VOID: {
            return Type.UNDEFINED;
        }
        case ASSIGN: {
            return rhs.getType(localVariableTypes);
        }
        case INSTANCEOF: {
            return Type.BOOLEAN;
        }
        case COMMALEFT: {
            return lhs.getType(localVariableTypes);
        }
        case COMMARIGHT: {
            return rhs.getType(localVariableTypes);
        }
        case AND:
        case OR:{
            return Type.widestReturnType(lhs.getType(localVariableTypes), rhs.getType(localVariableTypes));
        }
        default:
            if (isComparison()) {
                return Type.BOOLEAN;
            }
            return Type.OBJECT;
        }
    }

    private static boolean isString(final Type type) {
        return type == Type.STRING || type == Type.CHARSEQUENCE;
    }

    private static Type booleanToInt(final Type type) {
        return type == Type.BOOLEAN ? Type.INT : type;
    }

    private static Type undefinedToNumber(final Type type) {
        return type == Type.UNDEFINED ? Type.NUMBER : type;
    }

    /**
     * Check if this node is an assignment
     *
     * @return true if this node assigns a value
     */
    @Override
    public boolean isAssignment() {
        switch (tokenType()) {
        case ASSIGN:
        case ASSIGN_ADD:
        case ASSIGN_BIT_AND:
        case ASSIGN_BIT_OR:
        case ASSIGN_BIT_XOR:
        case ASSIGN_DIV:
        case ASSIGN_MOD:
        case ASSIGN_MUL:
        case ASSIGN_SAR:
        case ASSIGN_SHL:
        case ASSIGN_SHR:
        case ASSIGN_SUB:
           return true;
        default:
           return false;
        }
    }

    @Override
    public boolean isSelfModifying() {
        return isAssignment() && tokenType() != TokenType.ASSIGN;
    }

    @Override
    public Expression getAssignmentDest() {
        return isAssignment() ? lhs() : null;
    }

    @Override
    public BinaryNode setAssignmentDest(final Expression n) {
        return setLHS(n);
    }

    @Override
    public Expression getAssignmentSource() {
        return rhs();
    }

    /**
     * Assist in IR navigation.
     * @param visitor IR navigating visitor.
     */
    @Override
    public Node accept(final NodeVisitor<? extends LexicalContext> visitor) {
        if (visitor.enterBinaryNode(this)) {
            return visitor.leaveBinaryNode(setLHS((Expression)lhs.accept(visitor)).setRHS((Expression)rhs.accept(visitor)));
        }

        return this;
    }

    @Override
    public boolean isLocal() {
        switch (tokenType()) {
        case SAR:
        case SHL:
        case SHR:
        case BIT_AND:
        case BIT_OR:
        case BIT_XOR:
        case ADD:
        case DIV:
        case MOD:
        case MUL:
        case SUB:
            return lhs.isLocal() && lhs.getType().isJSPrimitive()
                && rhs.isLocal() && rhs.getType().isJSPrimitive();
        case ASSIGN_ADD:
        case ASSIGN_BIT_AND:
        case ASSIGN_BIT_OR:
        case ASSIGN_BIT_XOR:
        case ASSIGN_DIV:
        case ASSIGN_MOD:
        case ASSIGN_MUL:
        case ASSIGN_SAR:
        case ASSIGN_SHL:
        case ASSIGN_SHR:
        case ASSIGN_SUB:
            return lhs instanceof IdentNode && lhs.isLocal() && lhs.getType().isJSPrimitive()
                    && rhs.isLocal() && rhs.getType().isJSPrimitive();
        case ASSIGN:
            return lhs instanceof IdentNode && lhs.isLocal() && rhs.isLocal();
        default:
            return false;
        }
    }

    @Override
    public boolean isAlwaysFalse() {
        switch (tokenType()) {
        case COMMALEFT:
            return lhs.isAlwaysFalse();
        case COMMARIGHT:
            return rhs.isAlwaysFalse();
        default:
            return false;
        }
    }

    @Override
    public boolean isAlwaysTrue() {
        switch (tokenType()) {
        case COMMALEFT:
            return lhs.isAlwaysTrue();
        case COMMARIGHT:
            return rhs.isAlwaysTrue();
        default:
            return false;
        }
    }

    @Override
    public void toString(final StringBuilder sb, final boolean printType) {
        final TokenType tokenType = tokenType();

        final boolean lhsParen = tokenType.needsParens(lhs().tokenType(), true);
        final boolean rhsParen = tokenType.needsParens(rhs().tokenType(), false);

        if (lhsParen) {
            sb.append('(');
        }

        lhs().toString(sb, printType);

        if (lhsParen) {
            sb.append(')');
        }

        sb.append(' ');

        switch (tokenType) {
        case COMMALEFT:
            sb.append(",<");
            break;
        case COMMARIGHT:
            sb.append(",>");
            break;
        case INCPREFIX:
        case DECPREFIX:
            sb.append("++");
            break;
        default:
            sb.append(tokenType.getName());
            break;
        }

        if (isOptimistic()) {
            sb.append(Expression.OPT_IDENTIFIER);
        }

        sb.append(' ');

        if (rhsParen) {
            sb.append('(');
        }
        rhs().toString(sb, printType);
        if (rhsParen) {
            sb.append(')');
        }
    }

    /**
     * Get the left hand side expression for this node
     * @return the left hand side expression
     */
    public Expression lhs() {
        return lhs;
    }

    /**
     * Get the right hand side expression for this node
     * @return the left hand side expression
     */
    public Expression rhs() {
        return rhs;
    }

    /**
     * Set the left hand side expression for this node
     * @param lhs new left hand side expression
     * @return a node equivalent to this one except for the requested change.
     */
    public BinaryNode setLHS(final Expression lhs) {
        if (this.lhs == lhs) {
            return this;
        }
        return new BinaryNode(this, lhs, rhs, type, programPoint);
    }

    /**
     * Set the right hand side expression for this node
     * @param rhs new left hand side expression
     * @return a node equivalent to this one except for the requested change.
     */
    public BinaryNode setRHS(final Expression rhs) {
        if (this.rhs == rhs) {
            return this;
        }
        return new BinaryNode(this, lhs, rhs, type, programPoint);
    }

    @Override
    public int getProgramPoint() {
        return programPoint;
    }

    @Override
    public boolean canBeOptimistic() {
        return isTokenType(TokenType.ADD) || (getMostOptimisticType() != getMostPessimisticType());
    }

    @Override
    public BinaryNode setProgramPoint(final int programPoint) {
        if (this.programPoint == programPoint) {
            return this;
        }
        return new BinaryNode(this, lhs, rhs, type, programPoint);
    }

    @Override
    public Type getMostOptimisticType() {
        final TokenType tokenType = tokenType();
        if(tokenType == TokenType.ADD || tokenType == TokenType.ASSIGN_ADD) {
            return OPTIMISTIC_UNDECIDED_TYPE;
        } else if (CAN_OVERFLOW.contains(tokenType())) {
            return Type.INT;
        }
        return getMostPessimisticType();
    }

    @Override
    public Type getMostPessimisticType() {
        return getWidestOperationType();
    }

    /**
     * Returns true if the node has the optimistic type of the node is not yet decided. Optimistic ADD nodes start out
     * as undecided until we can figure out if they're numeric or not.
     * @return true if the node has the optimistic type of the node is not yet decided.
     */
    public boolean isOptimisticUndecidedType() {
        return type == OPTIMISTIC_UNDECIDED_TYPE;
    }

    @Override
    public Type getType(final Function<Symbol, Type> localVariableTypes) {
        if(localVariableTypes == cachedTypeFunction) {
            return cachedType;
        }
        cachedType = getTypeUncached(localVariableTypes);
        cachedTypeFunction = localVariableTypes;
        return cachedType;
    }

    private Type getTypeUncached(final Function<Symbol, Type> localVariableTypes) {
        if(type == OPTIMISTIC_UNDECIDED_TYPE) {
            return decideType(lhs.getType(localVariableTypes), rhs.getType(localVariableTypes));
        }
        final Type widest = getWidestOperationType(localVariableTypes);
        if(type == null) {
            return widest;
        }
        return Type.narrowest(widest, Type.widest(type, Type.widest(lhs.getType(localVariableTypes), rhs.getType(localVariableTypes))));
    }

    private static Type decideType(final Type lhsType, final Type rhsType) {
        // Compare this to getWidestOperationType() for ADD and ASSIGN_ADD cases. There's some similar logic, but these
        // are optimistic decisions, meaning that we don't have to treat boolean addition separately (as it'll become
        // int addition in the general case anyway), and that we also don't conservatively widen sums of ints to
        // longs, or sums of longs to doubles.
        if(isString(lhsType) || isString(rhsType)) {
            return Type.CHARSEQUENCE;
        }
        // NOTE: We don't have optimistic object-to-(int, long) conversions. Therefore, if any operand is an Object, we
        // bail out of optimism here and presume a conservative Object return value, as the object's ToPrimitive() can
        // end up returning either a number or a string, and their common supertype is Object, for better or worse.
        final Type widest = Type.widest(undefinedToNumber(booleanToInt(lhsType)), undefinedToNumber(booleanToInt(rhsType)));
        return widest.isObject() ? Type.OBJECT : widest;
    }

    /**
     * If the node is a node representing an add operation and has {@link #isOptimisticUndecidedType() optimistic
     * undecided type}, decides its type. Should be invoked after its operands types have been finalized.
     * @return returns a new node similar to this node, but with its type set to the type decided from the type of its
     * operands.
     */
    public BinaryNode decideType() {
        assert type == OPTIMISTIC_UNDECIDED_TYPE;
        return setType(decideType(lhs.getType(), rhs.getType()));
    }

    @Override
    public BinaryNode setType(final Type type) {
        if (this.type == type) {
            return this;
        }
        return new BinaryNode(this, lhs, rhs, type, programPoint);
    }
}
