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

import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.runtime.Source;

/**
 * IR representation for an IF statement.
 *
 */
public class IfNode extends Node {
    /** Test expression. */
    private Node test;

    /** Pass statements. */
    private Block pass;

    /** Fail statements. */
    private Block fail;

    /**
     * Constructor
     *
     * @param source  the source
     * @param token   token
     * @param finish  finish
     * @param test    test
     * @param pass    block to execute when test passes
     * @param fail    block to execute when test fails or null
     */
    public IfNode(final Source source, final long token, final int finish, final Node test, final Block pass, final Block fail) {
        super(source, token, finish);

        this.test = test;
        this.pass = pass;
        this.fail = fail;
    }

    private IfNode(final IfNode ifNode, final CopyState cs) {
        super(ifNode);

        this.test = cs.existingOrCopy(ifNode.test);
        this.pass = (Block)cs.existingOrCopy(ifNode.pass);
        this.fail = (Block)cs.existingOrCopy(ifNode.fail);
    }

    @Override
    protected Node copy(final CopyState cs) {
        return new IfNode(this, cs);
    }

    @Override
    public Node accept(final NodeVisitor visitor) {
        if (visitor.enter(this) != null) {
            test = test.accept(visitor);

            pass = (Block)pass.accept(visitor);

            if (fail != null) {
                fail = (Block)fail.accept(visitor);
            }

            return visitor.leave(this);
        }

        return this;
    }

    @Override
    public void toString(final StringBuilder sb) {
        sb.append("if (");
        test.toString(sb);
        sb.append(')');
    }

    /**
     * Get the else block of this IfNode
     * @return the else block, or null if none exists
     */
    public Block getFail() {
        return fail;
    }

    /**
     * Get the then block for this IfNode
     * @return the then block
     */
    public Block getPass() {
        return pass;
    }

    /**
     * Get the test expression for this IfNode
     * @return the test expression
     */
    public Node getTest() {
        return test;
    }

    /**
     * Reset the test expression for this IfNode
     * @param test a new test expression
     */
    public void setTest(final Node test) {
        this.test = test;
    }
}
