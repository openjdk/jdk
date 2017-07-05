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

import java.util.List;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;

/**
 * Represents a block used as a statement.
 */
public class BlockStatement extends Statement {
    private static final long serialVersionUID = 1L;

    /** Block to execute. */
    private final Block block;

    /**
     * Constructor
     *
     * @param block the block to execute
     */
    public BlockStatement(final Block block) {
        this(block.getFirstStatementLineNumber(), block);
    }

    /**
     * Constructor
     *
     * @param lineNumber line number
     * @param block the block to execute
     */
    public BlockStatement(final int lineNumber, final Block block) {
        super(lineNumber, block.getToken(), block.getFinish());
        this.block = block;
    }

    private BlockStatement(final BlockStatement blockStatement, final Block block) {
        super(blockStatement);
        this.block = block;
    }

    /**
     * Use this method to create a block statement meant to replace a single statement.
     * @param stmt the statement to replace
     * @param newStmts the statements for the new block statement
     * @return a block statement with the new statements. It will have the line number, token, and finish of the
     * original statement.
     */
    public static BlockStatement createReplacement(final Statement stmt, final List<Statement> newStmts) {
        return createReplacement(stmt, stmt.getFinish(), newStmts);
    }

    /**
     * Use this method to create a block statement meant to replace a single statement.
     * @param stmt the statement to replace
     * @param finish the new finish for the block
     * @param newStmts the statements for the new block statement
     * @return a block statement with the new statements. It will have the line number, and token of the
     * original statement.
     */
    public static BlockStatement createReplacement(final Statement stmt, final int finish, final List<Statement> newStmts) {
        return new BlockStatement(stmt.getLineNumber(), new Block(stmt.getToken(), finish, newStmts));
    }

    @Override
    public boolean isTerminal() {
        return block.isTerminal();
    }

    /**
     * Tells if this is a synthetic block statement or not.
     *
     * @return true if this is a synthetic statement
     */
    public boolean isSynthetic() {
        return block.isSynthetic();
    }

    @Override
    public Node accept(final NodeVisitor<? extends LexicalContext> visitor) {
        if (visitor.enterBlockStatement(this)) {
            return visitor.leaveBlockStatement(setBlock((Block)block.accept(visitor)));
        }

        return this;
    }

    @Override
    public void toString(final StringBuilder sb, final boolean printType) {
        block.toString(sb, printType);
    }

    /**
     * Return the block to be executed
     * @return the block
     */
    public Block getBlock() {
        return block;
    }

    /**
     * Reset the block to be executed
     * @param block the block
     * @return new or same execute node
     */
    public BlockStatement setBlock(final Block block) {
        if (this.block == block) {
            return this;
        }
        return new BlockStatement(this, block);
    }
}
