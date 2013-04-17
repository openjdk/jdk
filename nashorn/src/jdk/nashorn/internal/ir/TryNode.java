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
import jdk.nashorn.internal.codegen.Label;
import jdk.nashorn.internal.ir.annotations.Ignore;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.runtime.Source;

/**
 * IR representation of a TRY statement.
 */
public class TryNode extends Node {
    /** Try chain. */
    @Ignore //don't print, will be apparent from the AST
    private TryNode next;

    /** Try statements. */
    private Block body;

    /** List of catch clauses. */
    private List<Block> catchBlocks;

    /** Finally clause. */
    private Block finallyBody;

    /** Exit label. */
    private Label exit;

    /** Exception symbol. */
    private Symbol exception;

    /** Catchall exception for finally expansion, where applicable */
    private Symbol finallyCatchAll;

    /**
     * Constructor
     *
     * @param source  the source
     * @param token   token
     * @param finish  finish
     * @param next    next try node in chain
     */
    public TryNode(final Source source, final long token, final int finish, final TryNode next) {
        super(source, token, finish);

        this.next = next;
        this.exit = new Label("exit");
    }

    private TryNode(final TryNode tryNode, final CopyState cs) {
        super(tryNode);

        final List<Block> newCatchBlocks = new ArrayList<>();

        for (final Block block : tryNode.catchBlocks) {
            newCatchBlocks.add((Block)cs.existingOrCopy(block));
        }

        this.next        = (TryNode)cs.existingOrSame(tryNode.getNext());
        this.body        = (Block)cs.existingOrCopy(tryNode.getBody());
        this.catchBlocks = newCatchBlocks;
        this.finallyBody = (Block)cs.existingOrCopy(tryNode.getFinallyBody());
        this.exit        = new Label(tryNode.getExit());
    }

    @Override
    protected Node copy(final CopyState cs) {
        return new TryNode(this, cs);
    }

    /**
     * Assist in IR navigation.
     * @param visitor IR navigating visitor.
     */
    @Override
    public Node accept(final NodeVisitor visitor) {
        if (visitor.enterTryNode(this) != null) {
            // Need to do first for termination analysis.
            if (finallyBody != null) {
                finallyBody = (Block)finallyBody.accept(visitor);
            }

            body = (Block)body.accept(visitor);

            final List<Block> newCatchBlocks = new ArrayList<>(catchBlocks.size());
            for (final Block catchBlock : catchBlocks) {
                newCatchBlocks.add((Block)catchBlock.accept(visitor));
            }
            this.catchBlocks = newCatchBlocks;

            return visitor.leaveTryNode(this);
        }

        return this;
    }

    @Override
    public void toString(final StringBuilder sb) {
        sb.append("try");
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
     */
    public void setBody(final Block body) {
        this.body = body;
    }

    /**
     * Get the catches for this try block
     * @return a list of catch nodes
     */
    public List<CatchNode> getCatches() {
        final List<CatchNode> catches = new ArrayList<>(catchBlocks.size());
        for (final Block catchBlock : catchBlocks) {
            catches.add((CatchNode)catchBlock.getStatements().get(0));
        }
        return catches;
    }

    /**
     * Returns true if the specified block is the body of this try block, or any of its catch blocks.
     * @param block the block
     * @return true if the specified block is the body of this try block, or any of its catch blocks.
     */
    public boolean isChildBlock(Block block) {
        return body == block || catchBlocks.contains(block);
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
     */
    public void setCatchBlocks(final List<Block> catchBlocks) {
        this.catchBlocks = catchBlocks;
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
     */
    public void setException(final Symbol exception) {
        this.exception = exception;
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
     */
    public void setFinallyCatchAll(final Symbol finallyCatchAll) {
        this.finallyCatchAll = finallyCatchAll;
    }

    /**
     * Get the exit label for this try block
     * @return exit label
     */
    public Label getExit() {
        return exit;
    }

    /**
     * Set the exit label for this try block
     * @param exit label
     */
    public void setExit(final Label exit) {
        this.exit = exit;
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
     */
    public void setFinallyBody(final Block finallyBody) {
        this.finallyBody = finallyBody;
    }

    /**
     * Get next try node in try chain
     * @return next try node
     */
    public TryNode getNext() {
        return next;
    }

    /**
     * Set next try node in try chain
     * @param next next try node
     */
    public void setNext(final TryNode next) {
        this.next = next;
    }
}
