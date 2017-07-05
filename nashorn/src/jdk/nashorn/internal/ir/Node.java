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

import java.util.IdentityHashMap;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.parser.Token;
import jdk.nashorn.internal.runtime.Source;

/**
 * Nodes are used to compose Abstract Syntax Trees.
 *
 */
public abstract class Node extends Location {
    /** Node symbol. */
    private Symbol symbol;

    /** Start of source range. */
    protected int start;

    /** End of source range. */
    protected int finish;

    /** Has this node been resolved - i.e. emitted code already */
    private boolean isResolved;

    /** Is this node terminal */
    private boolean isTerminal;

    /** Is this a goto node */
    private boolean hasGoto;

    /** Is this a discard */
    private boolean shouldDiscard;

    /**
     * Constructor
     *
     * @param source the source
     * @param token  token
     * @param finish finish
     */
    public Node(final Source source, final long token, final int finish) {
        super(source, token);

        this.start  = Token.descPosition(token);
        this.finish = finish;
    }

    /**
     * Copy constructor
     *
     * @param node source node
     */
    protected Node(final Node node) {
        super(node);

        this.symbol        = node.symbol;
        this.isResolved    = node.isResolved;
        this.isTerminal    = node.isTerminal;
        this.hasGoto       = node.hasGoto;
        this.shouldDiscard = node.shouldDiscard;
        this.start         = node.start;
        this.finish        = node.finish;
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
     * Test to see if code been generated for this node. Set isResolved if not.
     *
     * @return True if node has already been resolved.
     */
    public boolean testResolved() {
        if (isResolved()) {
            return true;
        }

        setIsResolved(true);

        return false;
    }

    /**
     * Reset the resolved flag.
     */
    public void resetResolved() {
        setIsResolved(false);
    }

    /**
     * Is this a debug info node like LineNumberNode etc?
     *
     * @return true if this is a debug node
     */
    public boolean isDebug() {
        return false;
    }

    /**
     * Helper class used for node cloning
     */
    public static final class CopyState {
        private final IdentityHashMap<Node, Node> cloneMap = new IdentityHashMap<>();

        /**
         * Find existing or create new copy of the node.
         *
         * @param node Node to copy.
         *
         * @return New object.
         */
        public Node existingOrCopy(final Node node) {
            if (node != null) {
                Node copy = cloneMap.get(node);

                if (copy == null) {
                    copy = node.copy(this);
                    cloneMap.put(node, copy);
                }

                return copy;
            }

            return node;
        }

        /**
         * Find existing or use old copy of the node.
         *
         * @param node Node to copy.
         *
         * @return new object.
         */
        public Node existingOrSame(final Node node) {
            if (node != null) {
                Node copy = cloneMap.get(node);

                if (copy == null) {
                    copy = node;
                }

                return copy;
            }

            return node;
        }
    }

    /**
     * Deep copy the node.
     *
     * @return Deep copy of the  Node.
     */
    public final Node copy() {
        return copy(new CopyState());
    }

    /**
     * Deep copy the node.
     *
     * @param cs CopyState passed around to re-use certain nodes.
     * @return Deep copy of the  Node.
     */
    protected Node copy(final CopyState cs) {
        return cs.existingOrCopy(this);
    }

    /**
     * Provides a means to navigate the IR.
     * @param visitor Node visitor.
     * @return node the node or its replacement after visitation, null if no further visitations are required
     */
    public abstract Node accept(NodeVisitor visitor);

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
        return isTerminal || hasGoto;
    }

    /**
     * Copy the terminal flags state of a node to another node
     *
     * @param other source node
     */
    public void copyTerminalFlags(final Node other) {
        isTerminal = other.isTerminal;
        hasGoto    = other.hasGoto;
    }

    /**
     * Check if the return value of this expression should be discarded
     * @return true if return value is discarded
     */
    public boolean shouldDiscard() {
        return shouldDiscard;
    }

    /**
     * Setter that determines whether this node's return value should be discarded
     * or not
     *
     * @param shouldDiscard true if return value is discarded, false otherwise
     */
    public void setDiscard(final boolean shouldDiscard) {
        this.shouldDiscard = shouldDiscard;
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
        return hasGoto;
    }

    /**
     * Flag this node as having goto semantics as described in {@link Node#hasGoto()}
     */
    public void setHasGoto() {
        this.hasGoto = true;
    }

    /**
     * Check whether this node is resolved, i.e. code has been generated for it
     * @return true if node is resolved
     */
    public boolean isResolved() {
        return isResolved;
    }

    /**
     * Flag this node as resolved or not, i.e. code has been generated for it
     */
    private void setIsResolved(boolean isResolved) {
        this.isResolved = isResolved;
    }

    /**
     * Get start position for node
     * @return start position
     */
    public int getStart() {
        return start;
    }

    /**
     * Set start position for node
     * @param start start position
     */
    public void setStart(final int start) {
        this.start = start;
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

    /**
     * Assign a symbol to this node. See {@link Node#getSymbol()} for explanation
     * of what a symbol is
     *
     * @param symbol the symbol
     */
    public void setSymbol(final Symbol symbol) {
        this.symbol = symbol;
    }

    /**
     * Is this a terminal Node, i.e. does it end control flow like a throw or return
     * expression does?
     *
     * @return true if this node is terminal
     */
    public boolean isTerminal() {
        return isTerminal;
    }

    /**
     * Set this to be a terminal node, i.e. it terminates control flow as described
     * in {@link Node#isTerminal()}
     *
     * @param isTerminal true if this is a terminal node, false otherwise
     */
    public void setIsTerminal(final boolean isTerminal) {
        this.isTerminal = isTerminal;
    }

}
