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
import jdk.nashorn.internal.ir.annotations.Immutable;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;

/**
 * IR representation of CASE clause.
 * Case nodes are not BreakableNodes, but the SwitchNode is
 */
@Immutable
public final class CaseNode extends Node {
    /** Test expression. */
    private final Expression test;

    /** Statements. */
    private final Block body;

    /** Case entry label. */
    private final Label entry;

    /**
     * Constructors
     *
     * @param token    token
     * @param finish   finish
     * @param test     case test node, can be any node in JavaScript
     * @param body     case body
     */
    public CaseNode(final long token, final int finish, final Expression test, final Block body) {
        super(token, finish);

        this.test  = test;
        this.body  = body;
        this.entry = new Label("entry");
    }

    CaseNode(final CaseNode caseNode, final Expression test, final Block body) {
        super(caseNode);

        this.test  = test;
        this.body  = body;
        this.entry = new Label(caseNode.entry);
    }

    @Override
    public boolean isTerminal() {
        return body.isTerminal();
    }

    /**
     * Assist in IR navigation.
     * @param visitor IR navigating visitor.
     */
    @Override
    public Node accept(final NodeVisitor<? extends LexicalContext> visitor) {
        if (visitor.enterCaseNode(this)) {
            final Expression newTest = test == null ? null : (Expression)test.accept(visitor);
            final Block newBody = body == null ? null : (Block)body.accept(visitor);

            return visitor.leaveCaseNode(setTest(newTest).setBody(newBody));
        }

        return this;
    }

    @Override
    public void toString(final StringBuilder sb) {
        if (test != null) {
            sb.append("case ");
            test.toString(sb);
            sb.append(':');
        } else {
            sb.append("default:");
        }
    }

    /**
     * Get the body for this case node
     * @return the body
     */
    public Block getBody() {
        return body;
    }

    /**
     * Get the entry label for this case node
     * @return the entry label
     */
    public Label getEntry() {
        return entry;
    }

    /**
     * Get the test expression for this case node
     * @return the test
     */
    public Expression getTest() {
        return test;
    }

    /**
     * Reset the test expression for this case node
     * @param test new test expression
     * @return new or same CaseNode
     */
    public CaseNode setTest(final Expression test) {
        if (this.test == test) {
            return this;
        }
        return new CaseNode(this, test, body);
    }

    private CaseNode setBody(final Block body) {
        if (this.body == body) {
            return this;
        }
        return new CaseNode(this, test, body);
    }
}
