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

import jdk.nashorn.internal.codegen.Label;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.runtime.Source;

/**
 * IR representation for a WHILE statement. This is the superclass of all
 * loop nodes
 */
public class WhileNode extends BreakableNode {
    /** Test expression. */
    protected Node test;

    /** For body. */
    protected Block body;

    /** loop continue label. */
    protected Label continueLabel;

    /**
     * Constructor
     *
     * @param source  the source
     * @param token   token
     * @param finish  finish
     */
    public WhileNode(final Source source, final long token, final int finish) {
        super(source, token, finish);

        this.breakLabel    = new Label("while_break");
        this.continueLabel = new Label("while_continue");
    }

    /**
     * Copy constructor
     *
     * @param whileNode source node
     * @param cs        copy state
     */
    protected WhileNode(final WhileNode whileNode, final CopyState cs) {
        super(whileNode);

        this.test          = cs.existingOrCopy(whileNode.test);
        this.body          = (Block)cs.existingOrCopy(whileNode.body);
        this.breakLabel    = new Label(whileNode.breakLabel);
        this.continueLabel = new Label(whileNode.continueLabel);
    }

    @Override
    protected Node copy(final CopyState cs) {
        return new WhileNode(this, cs);
    }

    @Override
    public boolean isLoop() {
        return true;
    }

    /**
     * Assist in IR navigation.
     * @param visitor IR navigating visitor.
     */
    @Override
    public Node accept(final NodeVisitor visitor) {
        if (visitor.enter(this) != null) {
            test = test.accept(visitor);
            body = (Block)body.accept(visitor);

            return visitor.leave(this);
        }
        return this;
    }

    @Override
    public void toString(final StringBuilder sb) {
        sb.append("while (");
        test.toString(sb);
        sb.append(')');
    }

    /**
     * Get the loop body
     * @return body
     */
    public Block getBody() {
        return body;
    }

    /**
     * Reset the loop body
     * @param body new body
     */
    public void setBody(final Block body) {
        this.body = body;
    }

    /**
     * Set the break label (described in {@link WhileNode#getBreakLabel()} for this while node
     * @param breakLabel break label
     */
    public void setBreakLabel(final Label breakLabel) {
        this.breakLabel = breakLabel;
    }

    /**
     * Get the continue label for this while node, i.e. location to go to on continue
     * @return continue label
     */
    public Label getContinueLabel() {
        return continueLabel;
    }

    /**
     * Set the continue label (described in {@link WhileNode#getContinueLabel()} for this while node
     * @param continueLabel continue label
     */
    public void setContinueLabel(final Label continueLabel) {
        this.continueLabel = continueLabel;
    }

    /**
     * Get the test expression for this loop, that upon evaluation to true does another iteration
     * @return test expression
     */
    public Node getTest() {
        return test;
    }

    /**
     * Set the test expression for this loop
     * @param test test expression, null if infinite loop
     */
    public void setTest(final Node test) {
        this.test = test;
    }
}
