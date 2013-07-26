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
 * IR representation of a catch clause.
 */
@Immutable
public final class CatchNode extends Statement {
    /** Exception identifier. */
    private final IdentNode exception;

    /** Exception condition. */
    private final Expression exceptionCondition;

    /** Catch body. */
    private final Block body;

    private final int flags;

    /** Is this block a synthethic rethrow created by finally inlining? */
    public static final int IS_SYNTHETIC_RETHROW = 1;

    /**
     * Constructors
     *
     * @param lineNumber         lineNumber
     * @param token              token
     * @param finish             finish
     * @param exception          variable name of exception
     * @param exceptionCondition exception condition
     * @param body               catch body
     * @param flags              flags
     */
    public CatchNode(final int lineNumber, final long token, final int finish, final IdentNode exception, final Expression exceptionCondition, final Block body, final int flags) {
        super(lineNumber, token, finish);
        this.exception          = exception;
        this.exceptionCondition = exceptionCondition;
        this.body               = body;
        this.flags              = flags;
    }

    private CatchNode(final CatchNode catchNode, final IdentNode exception, final Expression exceptionCondition, final Block body, final int flags) {
        super(catchNode);
        this.exception          = exception;
        this.exceptionCondition = exceptionCondition;
        this.body               = body;
        this.flags              = flags;
    }

    /**
     * Assist in IR navigation.
     * @param visitor IR navigating visitor.
     */
    @Override
    public Node accept(final NodeVisitor<? extends LexicalContext> visitor) {
        if (visitor.enterCatchNode(this)) {
            return visitor.leaveCatchNode(
                setException((IdentNode)exception.accept(visitor)).
                setExceptionCondition(exceptionCondition == null ? null : (Expression)exceptionCondition.accept(visitor)).
                setBody((Block)body.accept(visitor)));
        }

        return this;
    }

    @Override
    public boolean isTerminal() {
        return body.isTerminal();
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
    public Expression getExceptionCondition() {
        return exceptionCondition;
    }

    /**
     * Reset the exception condition for this catch block
     * @param exceptionCondition the new exception condition
     * @return new or same CatchNode
     */
    public CatchNode setExceptionCondition(final Expression exceptionCondition) {
        if (this.exceptionCondition == exceptionCondition) {
            return this;
        }
        return new CatchNode(this, exception, exceptionCondition, body, flags);
    }

    /**
     * Get the body for this catch block
     * @return the catch block body
     */
    public Block getBody() {
        return body;
    }

    /**
     * Resets the exception of a catch block
     * @param exception new exception
     * @return new catch node if changed, same otherwise
     */
    public CatchNode setException(final IdentNode exception) {
        if (this.exception == exception) {
            return this;
        }
        return new CatchNode(this, exception, exceptionCondition, body, flags);
    }

    private CatchNode setBody(final Block body) {
        if (this.body == body) {
            return this;
        }
        return new CatchNode(this, exception, exceptionCondition, body, flags);
    }

    /**
     * Is this catch block a non-JavaScript constructor, for example created as
     * part of the rethrow mechanism of a finally block in Lower? Then we just
     * pass the exception on and need not unwrap whatever is in the ECMAException
     * object catch symbol
     * @return true if a finally synthetic rethrow
     */
    public boolean isSyntheticRethrow() {
        return (flags & IS_SYNTHETIC_RETHROW) == IS_SYNTHETIC_RETHROW;
    }

}
