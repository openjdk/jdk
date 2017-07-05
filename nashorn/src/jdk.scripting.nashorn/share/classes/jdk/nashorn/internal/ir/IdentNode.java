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

import static jdk.nashorn.internal.codegen.CompilerConstants.__DIR__;
import static jdk.nashorn.internal.codegen.CompilerConstants.__FILE__;
import static jdk.nashorn.internal.codegen.CompilerConstants.__LINE__;
import static jdk.nashorn.internal.runtime.UnwarrantedOptimismException.INVALID_PROGRAM_POINT;

import java.util.function.Function;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.annotations.Immutable;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.parser.Token;
import jdk.nashorn.internal.parser.TokenType;

/**
 * IR representation for an identifier.
 */
@Immutable
public final class IdentNode extends Expression implements PropertyKey, FunctionCall, Optimistic, JoinPredecessor {
    private static final int PROPERTY_NAME     = 1 << 0;
    private static final int INITIALIZED_HERE  = 1 << 1;
    private static final int FUNCTION          = 1 << 2;
    private static final int FUTURESTRICT_NAME = 1 << 3;

    /** Identifier. */
    private final String name;

    /** Optimistic type */
    private final Type type;

    private final int flags;

    private final int programPoint;

    private final LocalVariableConversion conversion;

    private Symbol symbol;


    /**
     * Constructor
     *
     * @param token   token
     * @param finish  finish position
     * @param name    name of identifier
     */
    public IdentNode(final long token, final int finish, final String name) {
        super(token, finish);
        this.name = name;
        this.type = null;
        this.flags = 0;
        this.programPoint = INVALID_PROGRAM_POINT;
        this.conversion = null;
    }

    private IdentNode(final IdentNode identNode, final String name, final Type type, final int flags, final int programPoint, final LocalVariableConversion conversion) {
        super(identNode);
        this.name = name;
        this.type = type;
        this.flags = flags;
        this.programPoint = programPoint;
        this.conversion = conversion;
        this.symbol = identNode.symbol;
    }

    /**
     * Copy constructor - create a new IdentNode for the same location
     *
     * @param identNode  identNode
     */
    public IdentNode(final IdentNode identNode) {
        super(identNode);
        this.name = identNode.getName();
        this.type = identNode.type;
        this.flags = identNode.flags;
        this.conversion = identNode.conversion;
        this.programPoint = INVALID_PROGRAM_POINT;
        this.symbol = identNode.symbol;
    }

    /**
     * Creates an identifier for the symbol. Normally used by code generator for creating temporary storage identifiers
     * that must contain both a symbol and a type.
     * @param symbol the symbol to create a temporary identifier for.
     * @return a temporary identifier for the symbol.
     */
    public static IdentNode createInternalIdentifier(final Symbol symbol) {
        return new IdentNode(Token.toDesc(TokenType.IDENT, 0, 0), 0, symbol.getName()).setSymbol(symbol);
    }

    @Override
    public Type getType(final Function<Symbol, Type> localVariableTypes) {
        if(type != null) {
            return type;
        } else if(symbol != null && symbol.isScope()) {
            return Type.OBJECT;
        }
        final Type symbolType = localVariableTypes.apply(symbol);
        return symbolType == null ? Type.UNDEFINED : symbolType;
    }

    /**
     * Assist in IR navigation.
     *
     * @param visitor IR navigating visitor.
     */
    @Override
    public Node accept(final NodeVisitor<? extends LexicalContext> visitor) {
        if (visitor.enterIdentNode(this)) {
            return visitor.leaveIdentNode(this);
        }

        return this;
    }

    @Override
    public void toString(final StringBuilder sb, final boolean printType) {
        if (printType) {
            optimisticTypeToString(sb, symbol == null || !symbol.hasSlot());
        }
        sb.append(name);
    }

    /**
     * Get the name of the identifier
     * @return  IdentNode name
     */
    public String getName() {
        return name;
    }

    @Override
    public String getPropertyName() {
        return getName();
    }

    @Override
    public boolean isLocal() {
        return !getSymbol().isScope();
    }

    /**
     * Return the Symbol the compiler has assigned to this identifier. The symbol is a description of the storage
     * location for the identifier.
     *
     * @return the symbol
     */
    public Symbol getSymbol() {
        return symbol;
    }

    /**
     * Assign a symbol to this identifier. See {@link IdentNode#getSymbol()} for explanation of what a symbol is.
     *
     * @param symbol the symbol
     * @return new node
     */
    public IdentNode setSymbol(final Symbol symbol) {
        if (this.symbol == symbol) {
            return this;
        }
        final IdentNode newIdent = (IdentNode)clone();
        newIdent.symbol = symbol;
        return newIdent;
    }

    /**
     * Check if this IdentNode is a property name
     * @return true if this is a property name
     */
    public boolean isPropertyName() {
        return (flags & PROPERTY_NAME) == PROPERTY_NAME;
    }

    /**
     * Flag this IdentNode as a property name
     * @return a node equivalent to this one except for the requested change.
     */
    public IdentNode setIsPropertyName() {
        if (isPropertyName()) {
            return this;
        }
        return new IdentNode(this, name, type, flags | PROPERTY_NAME, programPoint, conversion);
    }

    /**
     * Check if this IdentNode is a future strict name
     * @return true if this is a future strict name
     */
    public boolean isFutureStrictName() {
        return (flags & FUTURESTRICT_NAME) == FUTURESTRICT_NAME;
    }

    /**
     * Flag this IdentNode as a future strict name
     * @return a node equivalent to this one except for the requested change.
     */
    public IdentNode setIsFutureStrictName() {
        if (isFutureStrictName()) {
            return this;
        }
        return new IdentNode(this, name, type, flags | FUTURESTRICT_NAME, programPoint, conversion);
    }

    /**
     * Helper function for local def analysis.
     * @return true if IdentNode is initialized on creation
     */
    public boolean isInitializedHere() {
        return (flags & INITIALIZED_HERE) == INITIALIZED_HERE;
    }

    /**
     * Flag IdentNode to be initialized on creation
     * @return a node equivalent to this one except for the requested change.
     */
    public IdentNode setIsInitializedHere() {
        if (isInitializedHere()) {
            return this;
        }
        return new IdentNode(this, name, type, flags | INITIALIZED_HERE, programPoint, conversion);
    }

    /**
     * Check if the name of this IdentNode is same as that of a compile-time property (currently __DIR__, __FILE__, and
     * __LINE__).
     *
     * @return true if this IdentNode's name is same as that of a compile-time property
     */
    public boolean isCompileTimePropertyName() {
        return name.equals(__DIR__.symbolName()) || name.equals(__FILE__.symbolName()) || name.equals(__LINE__.symbolName());
    }

    @Override
    public boolean isFunction() {
        return (flags & FUNCTION) == FUNCTION;
    }

    @Override
    public IdentNode setType(final Type type) {
        if (this.type == type) {
            return this;
        }
        return new IdentNode(this, name, type, flags, programPoint, conversion);
    }

    /**
     * Mark this node as being the callee operand of a {@link CallNode}.
     * @return an ident node identical to this one in all aspects except with its function flag set.
     */
    public IdentNode setIsFunction() {
        if (isFunction()) {
            return this;
        }
        return new IdentNode(this, name, type, flags | FUNCTION, programPoint, conversion);
    }

    /**
     * Mark this node as not being the callee operand of a {@link CallNode}.
     * @return an ident node identical to this one in all aspects except with its function flag unset.
     */
    public IdentNode setIsNotFunction() {
        if (! isFunction()) {
            return this;
        }
        return new IdentNode(this, name, type, flags & ~FUNCTION, programPoint, conversion);
    }

    @Override
    public int getProgramPoint() {
        return programPoint;
    }

    @Override
    public Optimistic setProgramPoint(final int programPoint) {
        if (this.programPoint == programPoint) {
            return this;
        }
        return new IdentNode(this, name, type, flags, programPoint, conversion);
    }

    @Override
    public Type getMostOptimisticType() {
        return Type.INT;
    }

    @Override
    public Type getMostPessimisticType() {
        return Type.OBJECT;
    }

    @Override
    public boolean canBeOptimistic() {
        return true;
    }

    @Override
    public JoinPredecessor setLocalVariableConversion(final LexicalContext lc, final LocalVariableConversion conversion) {
        if(this.conversion == conversion) {
            return this;
        }
        return new IdentNode(this, name, type, flags, programPoint, conversion);
    }

    /**
     * Is this an internal symbol, i.e. one that starts with ':'. Those can
     * never be optimistic.
     * @return true if internal symbol
     */
    public boolean isInternal() {
        assert name != null;
        return name.charAt(0) == ':';
    }

    @Override
    public LocalVariableConversion getLocalVariableConversion() {
        return conversion;
    }
}
