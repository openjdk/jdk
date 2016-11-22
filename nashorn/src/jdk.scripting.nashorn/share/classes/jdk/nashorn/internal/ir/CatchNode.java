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
    private static final long serialVersionUID = 1L;

    /** Exception binding identifier or binding pattern. */
    private final Expression exception;

    /** Exception condition. */
    private final Expression exceptionCondition;

    /** Catch body. */
    private final Block body;

    private final boolean isSyntheticRethrow;

    /**
     * Constructors
     *
     * @param lineNumber         lineNumber
     * @param token              token
     * @param finish             finish
     * @param exception          variable name or pattern of exception
     * @param exceptionCondition exception condition
     * @param body               catch body
     * @param isSyntheticRethrow true if this node is a synthetically generated rethrow node.
     */
    public CatchNode(final int lineNumber, final long token, final int finish, final Expression exception,
            final Expression exceptionCondition, final Block body, final boolean isSyntheticRethrow) {
        super(lineNumber, token, finish);
        if (exception instanceof IdentNode) {
            this.exception = ((IdentNode) exception).setIsInitializedHere();
        } else if ((exception instanceof LiteralNode.ArrayLiteralNode) || (exception instanceof ObjectNode)) {
            this.exception = exception;
        } else {
            throw new IllegalArgumentException("invalid catch parameter");
        }
        this.exceptionCondition = exceptionCondition;
        this.body = body;
        this.isSyntheticRethrow = isSyntheticRethrow;
    }

    private CatchNode(final CatchNode catchNode, final Expression exception, final Expression exceptionCondition,
            final Block body, final boolean isSyntheticRethrow) {
        super(catchNode);
        this.exception          = exception;
        this.exceptionCondition = exceptionCondition;
        this.body               = body;
        this.isSyntheticRethrow = isSyntheticRethrow;
    }

    /**
     * Assist in IR navigation.
     * @param visitor IR navigating visitor.
     */
    @Override
    public Node accept(final NodeVisitor<? extends LexicalContext> visitor) {
        if (visitor.enterCatchNode(this)) {
            return visitor.leaveCatchNode(
                    setException((Expression) exception.accept(visitor)).
                            setExceptionCondition(exceptionCondition == null ? null : (Expression) exceptionCondition.accept(visitor)).
                            setBody((Block) body.accept(visitor)));
        }
        return this;
    }

    @Override
    public boolean isTerminal() {
        return body.isTerminal();
    }

    @Override
    public void toString(final StringBuilder sb, final boolean printTypes) {
        sb.append(" catch (");
        exception.toString(sb, printTypes);

        if (exceptionCondition != null) {
            sb.append(" if ");
            exceptionCondition.toString(sb, printTypes);
        }
        sb.append(')');
    }

    /**
     * Get the binding pattern representing the exception thrown
     *
     * @return the exception binding pattern
     */
    public Expression getException() {
        return exception;
    }

    /**
     * Get the identifier representing the exception thrown
     *
     * @return the exception identifier
     * @throws ClassCastException if exception set is not binding identifier
     */
    public IdentNode getExceptionIdentifier() {
        return (IdentNode) exception;
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
        return new CatchNode(this, exception, exceptionCondition, body, isSyntheticRethrow);
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
     *
     * @param exception new exception which can be binding identifier or binding
     * pattern
     * @return new catch node if changed, same otherwise
     */
    public CatchNode setException(final Expression exception) {
        if (this.exception == exception) {
            return this;
        }
        /*check if exception is legitimate*/
        if (!((exception instanceof IdentNode) || (exception instanceof LiteralNode.ArrayLiteralNode) || (exception instanceof ObjectNode))) {
            throw new IllegalArgumentException("invalid catch parameter");
        }
        return new CatchNode(this, exception, exceptionCondition, body, isSyntheticRethrow);
    }

    private CatchNode setBody(final Block body) {
        if (this.body == body) {
            return this;
        }
        return new CatchNode(this, exception, exceptionCondition, body, isSyntheticRethrow);
    }

    /**
     * Is this catch block a non-JavaScript constructor, for example created as
     * part of the rethrow mechanism of a finally block in Lower? Then we just
     * pass the exception on and need not unwrap whatever is in the ECMAException
     * object catch symbol
     * @return true if a finally synthetic rethrow
     */
    public boolean isSyntheticRethrow() {
        return isSyntheticRethrow;
    }
}
