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
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.parser.Token;
import jdk.nashorn.internal.parser.TokenType;

/**
 * Nodes are used to compose Abstract Syntax Trees.
 */
public abstract class Node implements Cloneable {
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
        this.start  = node.start;
        this.finish = node.finish;
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
    public void toString(final StringBuilder sb) {
        toString(sb, true);
    }

    /**
     * Print logic that decides whether to show the optimistic type
     * or not - for example it should not be printed after just parse,
     * when it hasn't been computed, or has been set to a trivially provable
     * value
     * @param sb   string builder
     * @param printType print type?
     */
    public abstract void toString(final StringBuilder sb, final boolean printType);

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
     * Get start position for node
     * @return start position
     */
    public int getStart() {
        return start;
    }

    @Override
    protected Object clone() {
        try {
            return super.clone();
        } catch (final CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
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

    //on change, we have to replace the entire list, that's we can't simple do ListIterator.set
    static <T extends Node> List<T> accept(final NodeVisitor<? extends LexicalContext> visitor, final List<T> list) {
        final int size = list.size();
        if (size == 0) {
            return list;
        }

         List<T> newList = null;

        for (int i = 0; i < size; i++) {
            final T node = list.get(i);
            @SuppressWarnings("unchecked")
            final T newNode = node == null ? null : (T)node.accept(visitor);
            if (newNode != node) {
                if (newList == null) {
                    newList = new ArrayList<>(size);
                    for (int j = 0; j < i; j++) {
                        newList.add(list.get(j));
                    }
                }
                newList.add(newNode);
            } else {
                if (newList != null) {
                    newList.add(node);
                }
            }
        }

        return newList == null ? list : newList;
    }

    static <T extends LexicalContextNode> T replaceInLexicalContext(final LexicalContext lc, final T oldNode, final T newNode) {
        if (lc != null) {
            lc.replace(oldNode, newNode);
        }
        return newNode;
    }
}
