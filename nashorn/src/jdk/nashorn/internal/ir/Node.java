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

import java.util.ArrayList;
import java.util.List;

import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.parser.Token;
import jdk.nashorn.internal.parser.TokenType;

/**
 * Nodes are used to compose Abstract Syntax Trees.
 */
public abstract class Node implements Cloneable {
    /** Node symbol. */
    private Symbol symbol;

    /** Start of source range. */
    protected final int start;

    /** End of source range. */
    protected int finish;

    /** Token descriptor. */
    private final long token;

    /**
     * Constructor
     *
     * @param token  token
     * @param finish finish
     */
    public Node(final long token, final int finish) {
        this.token  = token;
        this.start  = Token.descPosition(token);
        this.finish = finish;
    }

    /**
     * Constructor
     *
     * @param token   token
     * @param start   start
     * @param finish  finish
     */
    protected Node(final long token, final int start, final int finish) {
        this.start = start;
        this.finish = finish;
        this.token = token;
    }

    /**
     * Copy constructor
     *
     * @param node source node
     */
    protected Node(final Node node) {
        this.token  = node.token;
        this.symbol = node.symbol;
        this.start  = node.start;
        this.finish = node.finish;
    }

    /**
     * Check if the node has a type. The default behavior is to go into the symbol
     * and check the symbol type, but there may be overrides, for example in
     * getters that require a different type than the internal representation
     *
     * @return true if a type exists
     */
    public boolean hasType() {
        return getSymbol() != null;
    }

    /**
     * Returns the type of the node. Typically this is the symbol type. No types
     * are stored in the node itself, unless it implements TypeOverride
     *
     * @return the type of the node.
     */
    public Type getType() {
        assert hasType() : this + " has no type";
        return symbol.getSymbolType();
    }

    /**
     * Is this an atom node - for example a literal or an identity
     *
     * @return true if atom
     */
    public boolean isAtom() {
        return false;
    }

    /**
     * Is this a loop node?
     *
     * @return true if atom
     */
    public boolean isLoop() {
        return false;
    }

    /**
     * Is this an assignment node - for example a var node with an init
     * or a binary node that writes to a destination
     *
     * @return true if assignment
     */
    public boolean isAssignment() {
        return false;
    }

    /**
     * Is this a self modifying assignment?
     * @return true if self modifying, e.g. a++, or a*= 17
     */
    public boolean isSelfModifying() {
        return false;
    }

    /**
     * Returns widest operation type of this operation.
     *
     * @return the widest type for this operation
     */
    public Type getWidestOperationType() {
        return Type.OBJECT;
    }

    /**
     * Returns true if this node represents a comparison operator
     * @return true if comparison
     */
    public boolean isComparison() {
        return false;
    }

    /**
     * For reference copies - ensure that labels in the copy node are unique
     * using an appropriate copy constructor
     * @param lc lexical context
     * @return new node or same of no labels
     */
    public Node ensureUniqueLabels(final LexicalContext lc) {
        return this;
    }

    /**
     * Provides a means to navigate the IR.
     * @param visitor Node visitor.
     * @return node the node or its replacement after visitation, null if no further visitations are required
     */
    public abstract Node accept(NodeVisitor<? extends LexicalContext> visitor);

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        toString(sb);
        return sb.toString();
    }

    /**
     * String conversion helper. Fills a {@link StringBuilder} with the
     * string version of this node
     *
     * @param sb a StringBuilder
     */
    public abstract void toString(StringBuilder sb);

    /**
     * Check if this node has terminal flags, i.e. ends or breaks control flow
     *
     * @return true if terminal
     */
    public boolean hasTerminalFlags() {
        return isTerminal() || hasGoto();
    }

    /**
     * Get the finish position for this node in the source string
     * @return finish
     */
    public int getFinish() {
        return finish;
    }

    /**
     * Set finish position for this node in the source string
     * @param finish finish
     */
    public void setFinish(final int finish) {
        this.finish = finish;
    }

    /**
     * Check if this function repositions control flow with goto like
     * semantics, for example {@link BreakNode} or a {@link ForNode} with no test
     * @return true if node has goto semantics
     */
    public boolean hasGoto() {
        return false;
    }

    /**
     * Get start position for node
     * @return start position
     */
    public int getStart() {
        return start;
    }

    /**
     * Return the Symbol the compiler has assigned to this Node. The symbol
     * is the place where it's expression value is stored after evaluation
     *
     * @return the symbol
     */
    public Symbol getSymbol() {
        return symbol;
    }

    @Override
    protected Object clone() {
        try {
            return super.clone();
        } catch (final CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Assign a symbol to this node. See {@link Node#getSymbol()} for explanation
     * of what a symbol is
     *
     * @param lc lexical context
     * @param symbol the symbol
     * @return new node
     */
    public Node setSymbol(final LexicalContext lc, final Symbol symbol) {
        if (this.symbol == symbol) {
            return this;
        }
        final Node newNode = (Node)clone();
        newNode.symbol = symbol;
        return newNode;
    }


    @Override
    public final boolean equals(final Object other) {
        return super.equals(other);
    }

    @Override
    public final int hashCode() {
        return super.hashCode();
    }

    /**
     * Return token position from a token descriptor.
     *
     * @return Start position of the token in the source.
     */
    public int position() {
        return Token.descPosition(token);
    }

    /**
     * Return token length from a token descriptor.
     *
     * @return Length of the token.
     */
    public int length() {
        return Token.descLength(token);
    }

    /**
     * Return token tokenType from a token descriptor.
     *
     * @return Type of token.
     */
    public TokenType tokenType() {
        return Token.descType(token);
    }

    /**
     * Test token tokenType.
     *
     * @param type a type to check this token against
     * @return true if token types match.
     */
    public boolean isTokenType(final TokenType type) {
        return Token.descType(token) == type;
    }

    /**
     * Get the token for this location
     * @return the token
     */
    public long getToken() {
        return token;
    }

    /**
     * Is this a terminal Node, i.e. does it end control flow like a throw or return
     * expression does?
     *
     * @return true if this node is terminal
     */
    public boolean isTerminal() {
        return false;
    }

    //on change, we have to replace the entire list, that's we can't simple do ListIterator.set
    static <T extends Node> List<T> accept(final NodeVisitor<? extends LexicalContext> visitor, final Class<T> clazz, final List<T> list) {
        boolean changed = false;
        final List<T> newList = new ArrayList<>();

        for (final Node node : list) {
            final T newNode = node == null ? null : clazz.cast(node.accept(visitor));
            if (newNode != node) {
                changed = true;
            }
            newList.add(newNode);
        }

        return changed ? newList : list;
    }

    static <T extends LexicalContextNode> T replaceInLexicalContext(final LexicalContext lc, final T oldNode, final T newNode) {
        if (lc != null) {
            lc.replace(oldNode, newNode);
        }
        return newNode;
    }
}
