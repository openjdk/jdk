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
 * IR representing a FOR statement.
 *
 */
public class ForNode extends WhileNode {
    /** Initialize expression. */
    private Node init;

    /** Test expression. */
    private Node modify;

    /** Iterator symbol. */
    private Symbol iterator;

    /** is for in */
    private boolean isForIn;

    /** is for each */
    private boolean isForEach;

    /**
     * Constructor
     *
     * @param source     the source
     * @param token      token
     * @param finish     finish
     */
    public ForNode(final Source source, final long token, final int finish) {
        super(source, token, finish);
    }

    private ForNode(final ForNode forNode, final CopyState cs) {
        super(forNode, cs);

        this.init      = cs.existingOrCopy(forNode.init);
        this.modify    = cs.existingOrCopy(forNode.modify);
        this.iterator  = forNode.iterator;
        this.isForIn   = forNode.isForIn;
        this.isForEach = forNode.isForEach;
    }

    @Override
    protected Node copy(final CopyState cs) {
        return new ForNode(this, cs);
    }

    @Override
    public Node accept(final NodeVisitor visitor) {
        if (visitor.enterForNode(this) != null) {
            if (init != null) {
                init = init.accept(visitor);
            }

            if (test != null) {
                test = test.accept(visitor);
            }

            if (modify != null) {
                modify = modify.accept(visitor);
            }

            body = (Block)body.accept(visitor);

            return visitor.leaveForNode(this);
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

    /**
     * Get the initialization expression for this for loop
     * @return the initialization expression
     */
    public Node getInit() {
        return init;
    }

    /**
     * Reset the initialization expression for this for loop
     * @param init new initialization expression
     */
    public void setInit(final Node init) {
        this.init = init;
    }

    /**
     * Is this a for in construct rather than a standard init;condition;modification one
     * @return true if this is a for in constructor
     */
    public boolean isForIn() {
        return isForIn;
    }

    /**
     * Flag this to be a for in construct
     */
    public void setIsForIn() {
        this.isForIn = true;
    }

    /**
     * Is this a for each construct, known from e.g. Rhino. This will be a for of construct
     * in ECMAScript 6
     * @return true if this is a for each construct
     */
    public boolean isForEach() {
        return isForEach;
    }

    /**
     * Flag this to be a for each construct
     */
    public void setIsForEach() {
        this.isForEach = true;
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
    public Node getModify() {
        return modify;
    }

    /**
     * Reset the modification expression for this ForNode
     * @param modify new modification expression
     */
    public void setModify(final Node modify) {
        this.modify = modify;
    }

    @Override
    public Node getTest() {
        return test;
    }

    @Override
    public void setTest(final Node test) {
        this.test = test;
    }
}
