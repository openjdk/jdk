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
 * IR representation for a WHILE statement. This is the superclass of all
 * loop nodes
 */
@Immutable
public final class WhileNode extends LoopNode {

    /** is this a do while node ? */
    private final boolean isDoWhile;

    /**
     * Constructor
     *
     * @param lineNumber line number
     * @param token      token
     * @param finish     finish
     * @param isDoWhile  is this a do while loop?
     */
    public WhileNode(final int lineNumber, final long token, final int finish, final boolean isDoWhile) {
        super(lineNumber, token, finish, null, null, false);
        this.isDoWhile = isDoWhile;
    }

    /**
     * Internal copy constructor
     *
     * @param whileNode while node
     * @param test      test
     * @param body      body
     * @param controlFlowEscapes control flow escapes?
     */
    protected WhileNode(final WhileNode whileNode, final Expression test, final Block body, final boolean controlFlowEscapes) {
        super(whileNode, test, body, controlFlowEscapes);
        this.isDoWhile = whileNode.isDoWhile;
    }

    @Override
    public Node ensureUniqueLabels(final LexicalContext lc) {
        return Node.replaceInLexicalContext(lc, this, new WhileNode(this, test, body, controlFlowEscapes));
    }

    @Override
    public boolean hasGoto() {
        return test == null;
    }

    @Override
    public Node accept(final LexicalContext lc, final NodeVisitor<? extends LexicalContext> visitor) {
        if (visitor.enterWhileNode(this)) {
            if (isDoWhile()) {
                return visitor.leaveWhileNode(
                        setTest(lc, (Expression)test.accept(visitor)).
                        setBody(lc, (Block)body.accept(visitor)));
            }
            return visitor.leaveWhileNode(
                    setBody(lc, (Block)body.accept(visitor)).
                    setTest(lc, (Expression)test.accept(visitor)));

        }
        return this;
    }

    @Override
    public Expression getTest() {
        return test;
    }

    @Override
    public WhileNode setTest(final LexicalContext lc, final Expression test) {
        if (this.test == test) {
            return this;
        }
        return Node.replaceInLexicalContext(lc, this, new WhileNode(this, test, body, controlFlowEscapes));
    }

    @Override
    public Block getBody() {
        return body;
    }

    @Override
    public WhileNode setBody(final LexicalContext lc, final Block body) {
        if (this.body == body) {
            return this;
        }
        return Node.replaceInLexicalContext(lc, this, new WhileNode(this, test, body, controlFlowEscapes));
    }

    @Override
    public WhileNode setControlFlowEscapes(final LexicalContext lc, final boolean controlFlowEscapes) {
        if (this.controlFlowEscapes == controlFlowEscapes) {
            return this;
        }
        return Node.replaceInLexicalContext(lc, this, new WhileNode(this, test, body, controlFlowEscapes));
    }

    /**
     * Check if this is a do while loop or a normal while loop
     * @return true if do while
     */
    public boolean isDoWhile() {
        return isDoWhile;
    }

    @Override
    public void toString(final StringBuilder sb) {
        sb.append("while (");
        test.toString(sb);
        sb.append(')');
    }

    @Override
    public boolean mustEnter() {
        if (isDoWhile()) {
            return true;
        }
        return test == null;
    }
}
