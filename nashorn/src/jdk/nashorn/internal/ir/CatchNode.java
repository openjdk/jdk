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
 * IR representation of a catch clause.
 *
 */
public class CatchNode extends Node {
    /** Exception identifier. */
    private IdentNode exception;

    /** Exception condition. */
    private Node exceptionCondition;

    /** Catch body. */
    private Block body;

    /** Is rethrow - e.g. synthetic catch block for e.g. finallies, the parser case where
     * there has to be at least on catch for syntactic validity */
    private boolean isSyntheticRethrow;

    /**
     * Constructors
     *
     * @param source             the source
     * @param token              token
     * @param finish             finish
     * @param exception          variable name of exception
     * @param exceptionCondition exception condition
     * @param body               catch body
     */
    public CatchNode(final Source source, final long token, final int finish, final IdentNode exception, final Node exceptionCondition, final Block body) {
        super(source, token, finish);

        this.exception          = exception;
        this.exceptionCondition = exceptionCondition;
        this.body               = body;
    }

    private CatchNode(final CatchNode catchNode, final CopyState cs) {
        super(catchNode);

        this.exception          = (IdentNode)cs.existingOrCopy(catchNode.exception);
        this.exceptionCondition = cs.existingOrCopy(catchNode.exceptionCondition);
        this.body               = (Block)cs.existingOrCopy(catchNode.body);
        this.isSyntheticRethrow = catchNode.isSyntheticRethrow;
     }

    @Override
    protected Node copy(final CopyState cs) {
        return new CatchNode(this, cs);
    }

    /**
     * Assist in IR navigation.
     * @param visitor IR navigating visitor.
     */
    @Override
    public Node accept(final NodeVisitor visitor) {
        if (visitor.enter(this) != null) {
            exception = (IdentNode)exception.accept(visitor);

            if (exceptionCondition != null) {
                exceptionCondition = exceptionCondition.accept(visitor);
            }

            body = (Block)body.accept(visitor);
            return visitor.leave(this);
        }

        return this;
    }

    @Override
    public void toString(final StringBuilder sb) {
        sb.append(" catch (");
        exception.toString(sb);

        if (exceptionCondition != null) {
            sb.append(" if ");
            exceptionCondition.toString(sb);
        }
        sb.append(')');
    }

    /**
     * Check if this catch is a synthetic rethrow
     * @return true if this is a synthetic rethrow
     */
    public boolean isSyntheticRethrow() {
        return isSyntheticRethrow;
    }

    /**
     * Flag this as deliberatly generated catch all that rethrows the
     * caught exception. This is used for example for generating finally
     * expressions
     */
    public void setIsSyntheticRethrow() {
        this.isSyntheticRethrow = true;
    }

    /**
     * Get the identifier representing the exception thrown
     * @return the exception identifier
     */
    public IdentNode getException() {
        return exception;
    }

    /**
     * Get the exception condition for this catch block
     * @return the exception condition
     */
    public Node getExceptionCondition() {
        return exceptionCondition;
    }

    /**
     * Reset the exception condition for this catch block
     * @param exceptionCondition the new exception condition
     */
    public void setExceptionCondition(final Node exceptionCondition) {
        this.exceptionCondition = exceptionCondition;
    }

    /**
     * Get the body for this catch block
     * @return the catch block body
     */
    public Block getBody() {
        return body;
    }
}
