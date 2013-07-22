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

import jdk.nashorn.internal.ir.annotations.Immutable;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;

/**
 * IR representing a FOR statement.
 */
@Immutable
public final class ForNode extends LoopNode {
    /** Initialize expression. */
    private final Expression init;

    /** Test expression. */
    private final Expression modify;

    /** Iterator symbol. */
    private Symbol iterator;

    /** Is this a normal for loop? */
    public static final int IS_FOR      = 1 << 0;

    /** Is this a normal for in loop? */
    public static final int IS_FOR_IN   = 1 << 1;

    /** Is this a normal for each in loop? */
    public static final int IS_FOR_EACH = 1 << 2;

    private final int flags;

    /**
     * Constructor
     *
     * @param lineNumber line number
     * @param token      token
     * @param finish     finish
     * @param init       initialization expression
     * @param test       test
     * @param body       body
     * @param modify     modify
     * @param flags      flags
     */
    public ForNode(final int lineNumber, final long token, final int finish, final Expression init, final Expression test, final Block body, final Expression modify, final int flags) {
        super(lineNumber, token, finish, test, body, false);
        this.init   = init;
        this.modify = modify;
        this.flags  = flags;
    }

    private ForNode(final ForNode forNode, final Expression init, final Expression test, final Block body, final Expression modify, final int flags, final boolean controlFlowEscapes) {
        super(forNode, test, body, controlFlowEscapes);
        this.init   = init;
        this.modify = modify;
        this.flags  = flags;
        this.iterator = forNode.iterator; //TODO is this acceptable? symbols are never cloned, just copied as references
    }

    @Override
    public Node ensureUniqueLabels(LexicalContext lc) {
        return Node.replaceInLexicalContext(lc, this, new ForNode(this, init, test, body, modify, flags, controlFlowEscapes));
    }

    @Override
    public Node accept(final LexicalContext lc, final NodeVisitor<? extends LexicalContext> visitor) {
        if (visitor.enterForNode(this)) {
            return visitor.leaveForNode(
                setInit(lc, init == null ? null : (Expression)init.accept(visitor)).
                setTest(lc, test == null ? null : (Expression)test.accept(visitor)).
                setModify(lc, modify == null ? null : (Expression)modify.accept(visitor)).
                setBody(lc, (Block)body.accept(visitor)));
        }

        return this;
    }

    @Override
    public void toString(final StringBuilder sb) {
        sb.append("for (");

        if (isForIn()) {
            init.toString(sb);
            sb.append(" in ");
            modify.toString(sb);
        } else {
            if (init != null) {
                init.toString(sb);
            }
            sb.append("; ");
            if (test != null) {
                test.toString(sb);
            }
            sb.append("; ");
            if (modify != null) {
                modify.toString(sb);
            }
        }

        sb.append(')');
    }

    @Override
    public boolean hasGoto() {
        return !isForIn() && test == null;
    }

    @Override
    public boolean mustEnter() {
        if (isForIn()) {
            return false; //may be an empty set to iterate over, then we skip the loop
        }
        return test == null;
    }

    /**
     * Get the initialization expression for this for loop
     * @return the initialization expression
     */
    public Expression getInit() {
        return init;
    }

    /**
     * Reset the initialization expression for this for loop
     * @param lc lexical context
     * @param init new initialization expression
     * @return new for node if changed or existing if not
     */
    public ForNode setInit(final LexicalContext lc, final Expression init) {
        if (this.init == init) {
            return this;
        }
        return Node.replaceInLexicalContext(lc, this, new ForNode(this, init, test, body, modify, flags, controlFlowEscapes));
    }

    /**
     * Is this a for in construct rather than a standard init;condition;modification one
     * @return true if this is a for in constructor
     */
    public boolean isForIn() {
        return (flags & IS_FOR_IN) != 0;
    }

    /**
     * Flag this to be a for in construct
     * @param lc lexical context
     * @return new for node if changed or existing if not
     */
    public ForNode setIsForIn(final LexicalContext lc) {
        return setFlags(lc, flags | IS_FOR_IN);
    }

    /**
     * Is this a for each construct, known from e.g. Rhino. This will be a for of construct
     * in ECMAScript 6
     * @return true if this is a for each construct
     */
    public boolean isForEach() {
        return (flags & IS_FOR_EACH) != 0;
    }

    /**
     * Flag this to be a for each construct
     * @param lc lexical context
     * @return new for node if changed or existing if not
     */
    public ForNode setIsForEach(final LexicalContext lc) {
        return setFlags(lc, flags | IS_FOR_EACH);
    }

    /**
     * If this is a for in or for each construct, there is an iterator symbol
     * @return the symbol for the iterator to be used, or null if none exists
     */
    public Symbol getIterator() {
        return iterator;
    }

    /**
     * Assign an iterator symbol to this ForNode. Used for for in and for each constructs
     * @param iterator the iterator symbol
     */
    public void setIterator(final Symbol iterator) {
        this.iterator = iterator;
    }

    /**
     * Get the modification expression for this ForNode
     * @return the modification expression
     */
    public Expression getModify() {
        return modify;
    }

    /**
     * Reset the modification expression for this ForNode
     * @param lc lexical context
     * @param modify new modification expression
     * @return new for node if changed or existing if not
     */
    public ForNode setModify(final LexicalContext lc, final Expression modify) {
        if (this.modify == modify) {
            return this;
        }
        return Node.replaceInLexicalContext(lc, this, new ForNode(this, init, test, body, modify, flags, controlFlowEscapes));
    }

    @Override
    public Expression getTest() {
        return test;
    }

    @Override
    public ForNode setTest(final LexicalContext lc, final Expression test) {
        if (this.test == test) {
            return this;
        }
        return Node.replaceInLexicalContext(lc, this, new ForNode(this, init, test, body, modify, flags, controlFlowEscapes));
    }

    @Override
    public Block getBody() {
        return body;
    }

    @Override
    public ForNode setBody(final LexicalContext lc, final Block body) {
        if (this.body == body) {
            return this;
        }
        return Node.replaceInLexicalContext(lc, this, new ForNode(this, init, test, body, modify, flags, controlFlowEscapes));
    }

    @Override
    public ForNode setControlFlowEscapes(final LexicalContext lc, final boolean controlFlowEscapes) {
        if (this.controlFlowEscapes == controlFlowEscapes) {
            return this;
        }
        return Node.replaceInLexicalContext(lc, this, new ForNode(this, init, test, body, modify, flags, controlFlowEscapes));
    }

    private ForNode setFlags(final LexicalContext lc, final int flags) {
        if (this.flags == flags) {
            return this;
        }
        return Node.replaceInLexicalContext(lc, this, new ForNode(this, init, test, body, modify, flags, controlFlowEscapes));
    }

}
