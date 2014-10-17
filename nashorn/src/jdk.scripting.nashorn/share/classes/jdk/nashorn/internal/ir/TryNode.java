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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jdk.nashorn.internal.ir.annotations.Immutable;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;

/**
 * IR representation of a TRY statement.
 */
@Immutable
public final class TryNode extends Statement implements JoinPredecessor {
    private static final long serialVersionUID = 1L;

    /** Try statements. */
    private final Block body;

    /** List of catch clauses. */
    private final List<Block> catchBlocks;

    /** Finally clause. */
    private final Block finallyBody;

    /** Exception symbol. */
    private Symbol exception;

    /** Catchall exception for finally expansion, where applicable */
    private Symbol finallyCatchAll;

    private final LocalVariableConversion conversion;

    /**
     * Constructor
     *
     * @param lineNumber  lineNumber
     * @param token       token
     * @param finish      finish
     * @param body        try node body
     * @param catchBlocks list of catch blocks in order
     * @param finallyBody body of finally block or null if none
     */
    public TryNode(final int lineNumber, final long token, final int finish, final Block body, final List<Block> catchBlocks, final Block finallyBody) {
        super(lineNumber, token, finish);
        this.body        = body;
        this.catchBlocks = catchBlocks;
        this.finallyBody = finallyBody;
        this.conversion  = null;
    }

    private TryNode(final TryNode tryNode, final Block body, final List<Block> catchBlocks, final Block finallyBody, final LocalVariableConversion conversion) {
        super(tryNode);
        this.body        = body;
        this.catchBlocks = catchBlocks;
        this.finallyBody = finallyBody;
        this.conversion  = conversion;
        this.exception = tryNode.exception;
    }

    @Override
    public Node ensureUniqueLabels(final LexicalContext lc) {
        //try nodes are never in lex context
        return new TryNode(this, body, catchBlocks, finallyBody, conversion);
    }

    @Override
    public boolean isTerminal() {
        if (body.isTerminal()) {
            for (final Block catchBlock : getCatchBlocks()) {
                if (!catchBlock.isTerminal()) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Assist in IR navigation.
     * @param visitor IR navigating visitor.
     */
    @Override
    public Node accept(final NodeVisitor<? extends LexicalContext> visitor) {
        if (visitor.enterTryNode(this)) {
            // Need to do finallybody first for termination analysis. TODO still necessary?
            final Block newFinallyBody = finallyBody == null ? null : (Block)finallyBody.accept(visitor);
            final Block newBody        = (Block)body.accept(visitor);
            return visitor.leaveTryNode(
                setBody(newBody).
                setFinallyBody(newFinallyBody).
                setCatchBlocks(Node.accept(visitor, catchBlocks)).
                setFinallyCatchAll(finallyCatchAll));
        }

        return this;
    }

    @Override
    public void toString(final StringBuilder sb, final boolean printType) {
        sb.append("try ");
    }

    /**
     * Get the body for this try block
     * @return body
     */
    public Block getBody() {
        return body;
    }

    /**
     * Reset the body of this try block
     * @param body new body
     * @return new TryNode or same if unchanged
     */
    public TryNode setBody(final Block body) {
        if (this.body == body) {
            return this;
        }
        return new TryNode(this,  body, catchBlocks, finallyBody, conversion);
    }

    /**
     * Get the catches for this try block
     * @return a list of catch nodes
     */
    public List<CatchNode> getCatches() {
        final List<CatchNode> catches = new ArrayList<>(catchBlocks.size());
        for (final Block catchBlock : catchBlocks) {
            catches.add(getCatchNodeFromBlock(catchBlock));
        }
        return Collections.unmodifiableList(catches);
    }

    private static CatchNode getCatchNodeFromBlock(final Block catchBlock) {
        return (CatchNode)catchBlock.getStatements().get(0);
    }

    /**
     * Get the catch blocks for this try block
     * @return a list of blocks
     */
    public List<Block> getCatchBlocks() {
        return Collections.unmodifiableList(catchBlocks);
    }

    /**
     * Set the catch blocks of this try
     * @param catchBlocks list of catch blocks
     * @return new TryNode or same if unchanged
     */
    public TryNode setCatchBlocks(final List<Block> catchBlocks) {
        if (this.catchBlocks == catchBlocks) {
            return this;
        }
        return new TryNode(this, body, catchBlocks, finallyBody, conversion);
    }

    /**
     * Get the exception symbol for this try block
     * @return a symbol for the compiler to store the exception in
     */
    public Symbol getException() {
        return exception;
    }
    /**
     * Set the exception symbol for this try block
     * @param exception a symbol for the compiler to store the exception in
     * @return new TryNode or same if unchanged
     */
    public TryNode setException(final Symbol exception) {
        this.exception = exception;
        return this;
    }

    /**
     * Get the catch all symbol for this try block
     * @return catch all symbol
     */
    public Symbol getFinallyCatchAll() {
        return this.finallyCatchAll;
    }

    /**
     * If a finally block exists, the synthetic catchall needs another symbol to
     * store its throwable
     * @param finallyCatchAll a symbol for the finally catch all exception
     * @return new TryNode or same if unchanged
     *
     * TODO can this still be stateful?
     */
    public TryNode setFinallyCatchAll(final Symbol finallyCatchAll) {
        this.finallyCatchAll = finallyCatchAll;
        return this;
    }

    /**
     * Get the body of the finally clause for this try
     * @return finally body, or null if no finally
     */
    public Block getFinallyBody() {
        return finallyBody;
    }

    /**
     * Set the finally body of this try
     * @param finallyBody new finally body
     * @return new TryNode or same if unchanged
     */
    public TryNode setFinallyBody(final Block finallyBody) {
        if (this.finallyBody == finallyBody) {
            return this;
        }
        return new TryNode(this, body, catchBlocks, finallyBody, conversion);
    }

    @Override
    public JoinPredecessor setLocalVariableConversion(final LexicalContext lc, final LocalVariableConversion conversion) {
        if(this.conversion == conversion) {
            return this;
        }
        return new TryNode(this, body, catchBlocks, finallyBody, conversion);
    }

    @Override
    public LocalVariableConversion getLocalVariableConversion() {
        return conversion;
    }
}
